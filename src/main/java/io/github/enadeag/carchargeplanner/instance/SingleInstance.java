package io.github.enadeag.carchargeplanner.instance;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.net.ConnectException;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * One running copy of the app per user. The running copy holds a lock on a file in a private folder for as long as it
 * runs; the system lets go of it when the process ends, also when it crashes, so a lock is never left behind. The lock
 * alone decides which copy runs. Beside it the running copy listens on a Unix domain socket, which only carries the
 * commands of later starts: such a start sends {@link InstanceCommand#SHOW} and exits, also when the socket cannot be
 * reached, for instance because its path is too long for a socket.
 */
public final class SingleInstance implements AutoCloseable {

    private static final System.Logger LOG = System.getLogger(SingleInstance.class.getName());
    private static final String SOCKET_NAME = "instance.sock";
    private static final String LOCK_NAME = "instance.lock";
    /** How long to wait between looks while another copy that holds the lock starts listening. */
    private static final Duration RACE_PAUSE = Duration.ofMillis(50);

    private final Path path;
    private final PrivateFolder folder;
    private final Optional<UserPrincipal> user;
    private final Duration timeout;
    private FileLock lock;
    private InstanceServer server;
    private Optional<String> problem = Optional.empty();

    /**
     * Makes the single-instance guard; nothing is made or sent yet.
     *
     * @param folder the folder for the lock and the socket, only for this user, such as
     *     {@code $XDG_RUNTIME_DIR/car-charge-planner}; it is made when missing, with the folders above it
     * @param user the user this app runs as, where files have an owner and a mode (Linux): the folder must be theirs and
     *     only connections of theirs are taken; empty on Windows, where the folder's place keeps others out
     * @param timeout how long one side waits for the other's line, the command or its confirmation, and how long a
     *     later start waits for a copy that holds the lock to start listening; more than zero. A running app that is
     *     busy starting may take a moment to answer.
     */
    public SingleInstance(Path folder, Optional<UserPrincipal> user, Duration timeout) {
        this.path = folder;
        this.folder = new PrivateFolder(folder, user);
        this.user = user;
        this.timeout = timeout;
    }

    /**
     * Becomes the running copy, or asks the copy that runs to show its dashboard.
     *
     * @param handler what a command of a later start does, when this becomes the running copy; called on a thread of
     *     its own per command, from now until {@link #close()}
     * @return what came of it; only with {@link Claim#FIRST} does this copy listen, and only with it,
     *     {@link Claim#FIRST_UNREACHABLE} and {@link Claim#UNAVAILABLE} should this copy run
     */
    public Claim claim(Consumer<InstanceCommand> handler) {
        Path made;
        try {
            made = folder.make();
        } catch (NotPrivateException e) {
            problem = Optional.of(e.getMessage());
            return Claim.NOT_PRIVATE;
        } catch (IOException e) {
            problem = Optional.of("the folder " + path + " for the instance lock cannot be made: " + e);
            return Claim.UNAVAILABLE;
        }
        var socket = made.resolve(SOCKET_NAME);
        var deadline = System.nanoTime() + timeout.toNanos();
        try {
            while (true) {
                var taken = tryLock(made);
                if (taken.isPresent()) {
                    lock = taken.get();
                    return listen(socket, handler);
                }
                // another copy holds the lock; it may have started a moment ago and not listen yet
                var reply = reach(socket, InstanceCommand.SHOW);
                if (reply == Reply.ANSWERED) {
                    return Claim.HANDED_OVER;
                }
                if (reply == Reply.SILENT) {
                    return Claim.NO_ANSWER;
                }
                if (!pause(deadline)) {
                    return Claim.UNREACHABLE;
                }
            }
        } catch (IOException e) {
            problem = Optional.of("the instance lock in " + made + " cannot be taken: " + e);
            return Claim.UNAVAILABLE;
        }
    }

    /** Holding the lock: no copy that takes it runs, so a socket that is there is an older version's or left over. */
    private Claim listen(Path socket, Consumer<InstanceCommand> handler) {
        if (Files.exists(socket, LinkOption.NOFOLLOW_LINKS)) {
            // a version of the app from before the lock does not take it, but may run and listen
            var reply = reach(socket, InstanceCommand.SHOW);
            if (reply == Reply.ANSWERED || reply == Reply.SILENT) {
                releaseLock();
                return reply == Reply.ANSWERED ? Claim.HANDED_OVER : Claim.NO_ANSWER;
            }
            LOG.log(Level.INFO, "removing the instance socket left behind by an app that ended without removing it");
        }
        try {
            Files.deleteIfExists(socket);
            server = InstanceServer.bind(socket, user, timeout);
        } catch (IOException e) {
            problem = Optional.of("the instance socket " + socket + " cannot be made: " + e);
            return Claim.FIRST_UNREACHABLE;
        }
        server.serve(handler);
        return Claim.FIRST;
    }

    /**
     * Why the last {@link #claim(Consumer)} gave {@link Claim#FIRST_UNREACHABLE}, {@link Claim#NOT_PRIVATE} or
     * {@link Claim#UNAVAILABLE}, for the log and the owner.
     *
     * @return what went wrong, in English, naming the folder or the socket; empty after any other claim, or before one
     */
    public Optional<String> problem() {
        return problem;
    }

    /**
     * Sends a command to the running copy and waits for its confirmation, such as {@link InstanceCommand#QUIT} from
     * the command line. When a copy holds the lock but its socket cannot be reached, it looks again until the timeout,
     * as the copy may just be starting.
     *
     * @param command what to ask
     * @return what came of it: {@link Reply#NOBODY} or {@link Reply#STALE} when no copy holds the lock and none answers
     * @throws NotPrivateException when the private folder is another user's or a link
     * @throws IOException when the private folder cannot be made, or whether a copy holds the lock cannot be told
     */
    public Reply send(InstanceCommand command) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return Reply.NOBODY;
        }
        var made = folder.make();
        var socket = made.resolve(SOCKET_NAME);
        var deadline = System.nanoTime() + timeout.toNanos();
        while (true) {
            var reply = reach(socket, command);
            if (reply == Reply.ANSWERED || reply == Reply.SILENT) {
                return reply;
            }
            if (!lockHeld(made)) {
                return reply == Reply.UNREACHABLE ? Reply.STALE : reply;
            }
            if (!pause(deadline)) {
                return Reply.UNREACHABLE;
            }
        }
    }

    /**
     * Sends a command over the socket.
     *
     * @return {@link Reply#UNREACHABLE} when the socket is there but cannot be connected to for another reason than
     *     that nobody listens
     */
    private Reply reach(Path socket, InstanceCommand command) {
        if (!Files.exists(socket, LinkOption.NOFOLLOW_LINKS)) {
            return Reply.NOBODY;
        }
        SocketChannel channel;
        try {
            channel = SocketChannel.open(UnixDomainSocketAddress.of(socket));
        } catch (ConnectException e) {
            // refused: the socket is there but nobody listens; Windows says the same when it is gone meanwhile
            return Files.exists(socket, LinkOption.NOFOLLOW_LINKS) ? Reply.STALE : Reply.NOBODY;
        } catch (IOException e) {
            // such as a socket that may not be opened; or the running copy ended and removed it a moment ago
            return Files.exists(socket, LinkOption.NOFOLLOW_LINKS) ? Reply.UNREACHABLE : Reply.NOBODY;
        }
        try (channel) {
            Lines.write(channel, command.word());
            return Lines.read(channel, timeout).filter(InstanceServer.OK::equals).isPresent()
                    ? Reply.ANSWERED : Reply.SILENT;
        } catch (IOException e) {
            return Reply.SILENT; // it listens, but hung up without confirming
        }
    }

    /** The lock, when no other process or guard of this one holds it; the lock file is made when missing. */
    private Optional<FileLock> tryLock(Path made) throws IOException {
        Set<OpenOption> options = Set.of(StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
        var channel = FileChannel.open(made.resolve(LOCK_NAME), options, lockFileMode());
        try {
            var taken = channel.tryLock();
            if (taken == null) {
                channel.close();
            }
            return Optional.ofNullable(taken);
        } catch (OverlappingFileLockException e) {
            channel.close(); // another guard in this JVM holds it
            return Optional.empty();
        } catch (IOException | RuntimeException e) {
            channel.close();
            throw e;
        }
    }

    private FileAttribute<?>[] lockFileMode() {
        return user.isPresent()
                ? new FileAttribute<?>[] {PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))}
                : new FileAttribute<?>[0];
    }

    /** Whether a copy holds the lock: it is taken for a moment to find out, and let go of at once. */
    private static boolean lockHeld(Path made) throws IOException {
        var file = made.resolve(LOCK_NAME);
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        try (var channel = FileChannel.open(file, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            var taken = channel.tryLock();
            if (taken == null) {
                return true;
            }
            taken.release();
            return false;
        } catch (OverlappingFileLockException e) {
            return true;
        }
    }

    /** Waits a moment before looking again; false when the deadline has passed instead. */
    private static boolean pause(long deadline) throws IOException {
        var left = deadline - System.nanoTime();
        if (left <= 0) {
            return false;
        }
        try {
            Thread.sleep(Duration.ofNanos(Math.min(left, RACE_PAUSE.toNanos())));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while waiting for another copy of the app", e);
        }
        return true;
    }

    private void releaseLock() {
        if (lock == null) {
            return;
        }
        try {
            lock.channel().close(); // which lets go of the lock
        } catch (IOException e) {
            LOG.log(Level.WARNING, "could not let go of the instance lock", e);
        }
        lock = null;
    }

    /**
     * Stops listening and removes the socket, then lets go of the lock, when this is the running copy; otherwise does
     * nothing. The lock file stays: removing it could let two copies lock two different files.
     */
    @Override
    public void close() {
        if (server != null) {
            server.close();
            server = null;
        }
        releaseLock();
    }
}

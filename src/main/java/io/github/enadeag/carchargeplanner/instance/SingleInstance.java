package io.github.enadeag.carchargeplanner.instance;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.net.BindException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.UserPrincipal;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * One running copy of the app per user. The first copy listens on a Unix domain socket in a private folder; a later
 * start sends it {@link InstanceCommand#SHOW} and exits. A socket left behind by a crash is taken over, but only once
 * a connection to it is refused, which means nobody listens: a socket that cannot be reached for any other reason is
 * left alone.
 */
public final class SingleInstance implements AutoCloseable {

    private static final System.Logger LOG = System.getLogger(SingleInstance.class.getName());
    private static final String SOCKET_NAME = "instance.sock";
    /** Another copy may be starting at this very moment: it gets this long to start listening. */
    private static final Duration RACE_PAUSE = Duration.ofMillis(100);

    private final PrivateFolder folder;
    private final Optional<UserPrincipal> user;
    private final Duration timeout;
    private InstanceServer server;

    /**
     * Makes the single-instance guard; nothing is made or sent yet.
     *
     * @param folder the folder for the socket, only for this user, such as {@code $XDG_RUNTIME_DIR/car-charge-planner};
     *     its parent must exist, and it is made when missing
     * @param user the user this app runs as, where files have an owner and a mode (Linux): the folder must be theirs and
     *     only connections of theirs are taken; empty on Windows, where the folder's place keeps others out
     * @param timeout how long one side waits for the other's line, the command or its confirmation; more than zero.
     *     A running app that is busy starting may take a moment to answer.
     */
    public SingleInstance(Path folder, Optional<UserPrincipal> user, Duration timeout) {
        this.folder = new PrivateFolder(folder, user);
        this.user = user;
        this.timeout = timeout;
    }

    /**
     * Becomes the running copy, or asks the copy that runs to show its dashboard.
     *
     * @param handler what a command of a later start does, when this becomes the running copy; called on a thread of
     *     its own per command, from now until {@link #close()}
     * @return what came of it; only with {@link Claim#FIRST} does this copy listen
     */
    public Claim claim(Consumer<InstanceCommand> handler) {
        try {
            var socket = folder.make().resolve(SOCKET_NAME);
            var claim = claim(socket);
            if (claim == Claim.FIRST) {
                server.serve(handler);
            }
            return claim;
        } catch (IOException e) {
            LOG.log(Level.WARNING, "no private instance socket, so a second copy of the app could start", e);
            return Claim.UNAVAILABLE;
        }
    }

    private Claim claim(Path socket) throws IOException {
        var reply = send(socket, InstanceCommand.SHOW);
        if (reply == Reply.ANSWERED || reply == Reply.SILENT) {
            return handedOver(reply);
        }
        try {
            server = InstanceServer.bind(socket, user, timeout);
            return Claim.FIRST;
        } catch (BindException e) {
            // something is there: a crash's leftover, or a copy that started a moment ago and listens now
        }
        sleep(RACE_PAUSE);
        reply = send(socket, InstanceCommand.SHOW);
        if (reply == Reply.ANSWERED || reply == Reply.SILENT) {
            return handedOver(reply);
        }
        if (reply == Reply.STALE) {
            LOG.log(Level.INFO, "taking over the instance socket left behind by an app that ended without removing it");
            Files.deleteIfExists(socket);
        }
        server = InstanceServer.bind(socket, user, timeout);
        return Claim.FIRST;
    }

    private static Claim handedOver(Reply reply) {
        return reply == Reply.ANSWERED ? Claim.HANDED_OVER : Claim.NO_ANSWER;
    }

    /**
     * Sends a command to the running copy and waits for its confirmation, such as {@link InstanceCommand#QUIT} from
     * the command line.
     *
     * @param command what to ask
     * @return what came of it
     * @throws IOException when the private folder cannot be made or is not private, or the socket cannot be reached
     *     for another reason than that nobody listens
     */
    public Reply send(InstanceCommand command) throws IOException {
        return send(folder.make().resolve(SOCKET_NAME), command);
    }

    private Reply send(Path socket, InstanceCommand command) throws IOException {
        if (!Files.exists(socket, LinkOption.NOFOLLOW_LINKS)) {
            return Reply.NOBODY;
        }
        SocketChannel channel;
        try {
            channel = SocketChannel.open(UnixDomainSocketAddress.of(socket));
        } catch (ConnectException e) {
            // refused: the socket is there but nobody listens; Windows says the same when it is gone meanwhile
            return Files.exists(socket, LinkOption.NOFOLLOW_LINKS) ? Reply.STALE : Reply.NOBODY;
        } catch (SocketException e) {
            if (Files.exists(socket, LinkOption.NOFOLLOW_LINKS)) {
                throw e;
            }
            return Reply.NOBODY; // the running copy ended and removed it a moment ago
        }
        try (channel) {
            Lines.write(channel, command.word());
            return Lines.read(channel, timeout).filter(InstanceServer.OK::equals).isPresent()
                    ? Reply.ANSWERED : Reply.SILENT;
        } catch (IOException e) {
            return Reply.SILENT; // it listens, but hung up without confirming
        }
    }

    private static void sleep(Duration pause) throws IOException {
        try {
            Thread.sleep(pause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while waiting for another copy of the app", e);
        }
    }

    /** Stops listening and removes the socket, when this is the running copy; otherwise does nothing. */
    @Override
    public void close() {
        if (server != null) {
            server.close();
        }
    }
}

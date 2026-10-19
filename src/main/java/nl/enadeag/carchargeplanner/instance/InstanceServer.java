package nl.enadeag.carchargeplanner.instance;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Consumer;
import jdk.net.ExtendedSocketOptions;

/**
 * The running app's end of the instance socket: it takes the commands of later starts and confirms each with "ok".
 * Each connection is read on a thread of its own and with a timeout, so a peer that sends nothing holds up neither
 * the others nor the app. Lines that are not a command are dropped unanswered and unlogged: they could hold anything.
 */
final class InstanceServer implements AutoCloseable {

    private static final System.Logger LOG = System.getLogger(InstanceServer.class.getName());
    /** The answer that confirms a command. */
    static final String OK = "ok";

    private final ServerSocketChannel channel;
    private final Path socket;
    /** The only user whose connections are taken; null where the system cannot name a socket's peer. */
    private final UserPrincipal user;
    private final Duration timeout;

    private InstanceServer(ServerSocketChannel channel, Path socket, UserPrincipal user, Duration timeout) {
        this.channel = channel;
        this.socket = socket;
        this.user = user;
        this.timeout = timeout;
    }

    /**
     * Makes the socket and listens on it; commands are taken once {@link #serve(Consumer)} is called. Where files have
     * a mode the socket gets mode 0600.
     *
     * @param socket where the socket is made, in a private folder; nothing may be there yet
     * @param user the user this app runs as, the only one whose connections are taken where the system names a
     *     socket's peer (Linux); empty where it cannot (Windows), where the private folder alone keeps others out
     * @param timeout how long a connection may take to send its line; more than zero
     * @return the server, listening
     * @throws java.net.BindException when something is at {@code socket} already, a live server or one left behind
     * @throws IOException when the socket cannot be made
     */
    static InstanceServer bind(Path socket, Optional<UserPrincipal> user, Duration timeout) throws IOException {
        var channel = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        try {
            channel.bind(UnixDomainSocketAddress.of(socket));
        } catch (IOException e) {
            channel.close();
            throw e;
        }
        var server = new InstanceServer(channel, socket, user.orElse(null), timeout);
        if (user.isPresent()) {
            try {
                Files.setPosixFilePermissions(socket, PosixFilePermissions.fromString("rw-------"));
            } catch (IOException e) {
                server.close();
                throw e;
            }
        }
        return server;
    }

    /**
     * Starts taking commands, on a thread of the server's own, until {@link #close()}.
     *
     * @param handler what each confirmed command does; called on a new thread per command, so it may take its time
     *     and may close this server
     */
    void serve(Consumer<InstanceCommand> handler) {
        Thread.ofPlatform().name("single-instance").daemon().start(() -> accept(handler));
    }

    private void accept(Consumer<InstanceCommand> handler) {
        while (channel.isOpen()) {
            try {
                var connection = channel.accept();
                Thread.ofVirtual().name("single-instance-connection").start(() -> answer(connection, handler));
            } catch (ClosedChannelException _) {
                return;
            } catch (IOException e) {
                LOG.log(Level.WARNING, "could not take a connection on the instance socket", e);
            }
        }
    }

    private void answer(SocketChannel connection, Consumer<InstanceCommand> handler) {
        try (connection) {
            if (!fromUser(connection)) {
                LOG.log(Level.WARNING, "refused a connection to the instance socket from another user");
                return;
            }
            var command = Lines.read(connection, timeout).flatMap(InstanceCommand::of);
            if (command.isEmpty()) {
                return;
            }
            LOG.log(Level.INFO, "another start of the app sent \"" + command.get().word() + "\"");
            Lines.write(connection, OK);
            Thread.ofVirtual().name("single-instance-command").start(() -> handler.accept(command.get()));
        } catch (IOException e) {
            LOG.log(Level.DEBUG, "a connection to the instance socket failed", e);
        }
    }

    private boolean fromUser(SocketChannel connection) throws IOException {
        if (user == null || !connection.supportedOptions().contains(ExtendedSocketOptions.SO_PEERCRED)) {
            return true;
        }
        return connection.getOption(ExtendedSocketOptions.SO_PEERCRED).user().equals(user);
    }

    /** Stops listening and removes the socket, so the next start does not find it left behind. */
    @Override
    public void close() {
        try {
            channel.close();
            Files.deleteIfExists(socket);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "could not remove the instance socket " + socket, e);
        }
    }
}

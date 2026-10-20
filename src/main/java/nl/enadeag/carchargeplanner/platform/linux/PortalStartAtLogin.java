package nl.enadeag.carchargeplanner.platform.linux;

import nl.enadeag.carchargeplanner.desktop.system.StartAtLogin;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.types.Variant;

/**
 * Start at login inside a Flatpak sandbox, which cannot write the user's autostart folder: the desktop's Background
 * portal writes the entry, after asking the owner where the desktop does so.
 *
 * <p>The portal cannot be asked whether the entry is there, so the app remembers its last choice in a file of its
 * own: {@link #isOn()} says what the app last asked for and the desktop granted. Removing the entry in the desktop's
 * own settings is not seen; switching again in the app sets it right.
 */
public final class PortalStartAtLogin implements StartAtLogin {

    /** How long the owner has to answer when the desktop asks; Settings shows "Saving…" meanwhile. */
    public static final Duration ANSWER_WAIT = Duration.ofMinutes(2);

    private static final long GRANTED = 0;
    private static final long CANCELLED = 1;

    private final BusConnector connector;
    private final Path choice;
    private final List<String> commandLine;
    private final Duration wait;

    /**
     * Makes the switch; nothing is asked or written yet.
     *
     * @param connector opens a session bus connection for each request, closed again once the portal answered
     * @param choiceFile the file that remembers that the app starts at login, in the app's own folder; it is there
     *     only while start at login is on, and its folder is made when missing
     * @param commandLine the command the desktop starts at login inside the sandbox, the program first, such as the
     *     Flatpak's command and {@link #AT_LOGIN_FLAG}
     * @param wait how long to wait for the portal's answer, which may need the owner; more than zero
     */
    public PortalStartAtLogin(BusConnector connector, Path choiceFile, List<String> commandLine, Duration wait) {
        if (wait.isNegative() || wait.isZero()) {
            throw new IllegalArgumentException("the wait must be more than zero, not " + wait);
        }
        this.connector = connector;
        this.choice = choiceFile;
        this.commandLine = List.copyOf(commandLine);
        this.wait = wait;
    }

    @Override
    public boolean isOn() {
        return Files.exists(choice);
    }

    /**
     * {@inheritDoc}
     *
     * @throws IOException also when there is no portal, the owner or the desktop refused, or no answer came in time
     */
    @Override
    public void switchOn() throws IOException {
        request(true);
        Files.createDirectories(choice.getParent());
        Files.writeString(choice, "The desktop's Background portal starts the app at login; this file only"
                + " remembers that it was asked to.\n");
    }

    /**
     * {@inheritDoc}
     *
     * @throws IOException also when there is no portal, the desktop refused, or no answer came in time
     */
    @Override
    public void switchOff() throws IOException {
        request(false);
        Files.deleteIfExists(choice);
    }

    /** Asks the portal to start the app at login or not, and waits for the answer. */
    private void request(boolean autostart) throws IOException {
        var token = "ccp" + UUID.randomUUID().toString().replace("-", "");
        try (var bus = connector.connect()) {
            var answer = new CompletableFuture<BackgroundPortal.Request.Response>();
            // the portal names the request object after the caller and the token; listen before asking, as the
            // answer can come before the call returns. An older portal may name it otherwise, known only then.
            var expected = BackgroundPortal.REQUEST_FOLDER + bus.getUniqueName().substring(1).replace('.', '_')
                    + "/" + token;
            var wanted = new AtomicReference<>(expected);
            var arrived = new ConcurrentHashMap<String, BackgroundPortal.Request.Response>();
            var listening = bus.addSigHandler(BackgroundPortal.Request.Response.class, response -> {
                arrived.put(response.getPath(), response);
                if (response.getPath().equals(wanted.get())) {
                    answer.complete(response);
                }
            });
            try {
                var handle = portal(bus).RequestBackground("", Map.of(
                        "handle_token", new Variant<>(token),
                        "autostart", new Variant<>(autostart),
                        "commandline", new Variant<>(commandLine, "as")));
                wanted.set(handle.getPath());
                var early = arrived.get(handle.getPath());
                if (early != null) {
                    answer.complete(early);
                }
                check(waitFor(answer), autostart);
            } finally {
                stopListening(listening);
            }
        } catch (DBusException | RuntimeException e) {
            // dbus-java reports a missing portal or an error reply as a runtime exception
            throw new IOException("the desktop's Background portal cannot be reached (" + e.getMessage() + ")", e);
        }
    }

    private static void stopListening(AutoCloseable listening) {
        try {
            listening.close();
        } catch (Exception _) {
            // the handler goes with the connection anyway
        }
    }

    private static BackgroundPortal.Background portal(DBusConnection bus) throws DBusException {
        return bus.getRemoteObject(BackgroundPortal.BUS_NAME, BackgroundPortal.OBJECT_PATH,
                BackgroundPortal.Background.class);
    }

    private BackgroundPortal.Request.Response waitFor(CompletableFuture<BackgroundPortal.Request.Response> answer)
            throws IOException {
        try {
            return answer.get(wait.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException | ExecutionException e) {
            throw new IOException("the desktop did not answer in time", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("stopped while waiting for the desktop", e);
        }
    }

    /** A refusal, or a grant without start at login, is an error; switching off needs only the call to end well. */
    private static void check(BackgroundPortal.Request.Response response, boolean autostart) throws IOException {
        var code = response.response.longValue();
        if (code == CANCELLED) {
            throw new IOException("the request was cancelled");
        }
        if (code != GRANTED) {
            throw new IOException("the desktop refused it (response " + code + ")");
        }
        var granted = response.results.get("autostart");
        if (autostart && (granted == null || !Boolean.TRUE.equals(granted.getValue()))) {
            throw new IOException("the desktop did not allow the app to start at login");
        }
    }
}

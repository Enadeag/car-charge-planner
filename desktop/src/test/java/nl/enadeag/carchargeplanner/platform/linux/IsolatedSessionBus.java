package nl.enadeag.carchargeplanner.platform.linux;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A D-Bus session of a test's own. It has no service files, so it never starts a program of the system's: with the
 * system's session config, a call to a missing name such as org.freedesktop.Notifications would start the desktop's
 * own server, which could show a test's notification on the developer's desktop.
 */
public final class IsolatedSessionBus {

    private static final String CONFIG = """
            <!DOCTYPE busconfig PUBLIC "-//freedesktop//DTD D-Bus Bus Configuration 1.0//EN"
             "http://www.freedesktop.org/standards/dbus/1.0/busconfig.dtd">
            <busconfig>
              <type>session</type>
              <listen>unix:tmpdir=/tmp</listen>
              <policy context="default">
                <allow send_destination="*" eavesdrop="true"/>
                <allow eavesdrop="true"/>
                <allow own="*"/>
              </policy>
            </busconfig>
            """;

    private final Process daemon;
    private final String address;

    private IsolatedSessionBus(Process daemon, String address) {
        this.daemon = daemon;
        this.address = address;
    }

    /** Starts the bus with its socket and config in the folder; without dbus-daemon, see {@link SystemPrograms}. */
    public static IsolatedSessionBus start(Path folder) throws IOException {
        var config = Files.writeString(folder.resolve("bus.conf"), CONFIG);
        var daemon = SystemPrograms.start(new ProcessBuilder("dbus-daemon", "--config-file=" + config, "--nofork",
                "--nopidfile", "--address=unix:path=" + folder.resolve("bus"), "--print-address=1"));
        // it prints the address once it accepts connections
        var address = new BufferedReader(new InputStreamReader(daemon.getInputStream(), StandardCharsets.UTF_8))
                .readLine().strip();
        return new IsolatedSessionBus(daemon, address);
    }

    public String address() {
        return address;
    }

    public void stop() {
        daemon.destroy();
    }
}

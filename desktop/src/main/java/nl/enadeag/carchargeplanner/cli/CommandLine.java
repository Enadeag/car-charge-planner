package nl.enadeag.carchargeplanner.cli;

import nl.enadeag.carchargeplanner.desktop.system.StartAtLogin;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What the app was started to do, read from its arguments.
 *
 * @param command what to do
 * @param config the config file named with {@code --config=}; empty when none was named
 * @param atLogin {@code --background} was given: the app was started at login and starts hidden, as a tray icon
 *     or as a window that is not shown
 */
public record CommandLine(Command command, Optional<Path> config, boolean atLogin) {

    /** How to start the app, for the message about arguments it does not know. */
    public static final String USAGE = "Usage: car-charge-planner [run | quit | test-notification | start-at-login on | start-at-login off"
            + " | install-launcher | uninstall-launcher]"
            + " [--config=path/to/engine.json] [" + StartAtLogin.AT_LOGIN_FLAG + "]";

    /** What the app can be started to do, named by the words before or between the options. */
    public enum Command {
        /** Run the app: no words, or "run". */
        RUN,
        /** Ask the running app to quit: "quit". */
        QUIT,
        /** Ask the running app to show a test notification: "test-notification". */
        TEST_NOTIFICATION,
        /** Make the app start at login: "start-at-login on". */
        START_AT_LOGIN_ON,
        /** Stop the app from starting at login: "start-at-login off". */
        START_AT_LOGIN_OFF,
        /** Add the app to the Linux application menu: "install-launcher". */
        INSTALL_LAUNCHER,
        /** Remove the app from the Linux application menu: "uninstall-launcher". */
        UNINSTALL_LAUNCHER;

        private static Optional<Command> of(List<String> words) {
            return switch (String.join(" ", words)) {
                case "", "run" -> Optional.of(RUN);
                case "quit" -> Optional.of(QUIT);
                case "test-notification" -> Optional.of(TEST_NOTIFICATION);
                case "start-at-login on" -> Optional.of(START_AT_LOGIN_ON);
                case "start-at-login off" -> Optional.of(START_AT_LOGIN_OFF);
                case "install-launcher" -> Optional.of(INSTALL_LAUNCHER);
                case "uninstall-launcher" -> Optional.of(UNINSTALL_LAUNCHER);
                default -> Optional.empty();
            };
        }
    }

    /**
     * Reads the arguments.
     *
     * @param args the arguments as the app got them; options it does not know are ignored
     * @return what they ask for
     * @throws IllegalArgumentException when the words name no command; its message says which words
     */
    public static CommandLine parse(String... args) {
        var words = new ArrayList<String>();
        Optional<Path> config = Optional.empty();
        var atLogin = false;
        for (var arg : args) {
            if (arg.startsWith("--config=")) {
                config = Optional.of(Path.of(arg.substring("--config=".length())));
            } else if (arg.equals(StartAtLogin.AT_LOGIN_FLAG)) {
                atLogin = true;
            } else if (!arg.startsWith("-")) {
                words.add(arg);
            }
        }
        var command = Command.of(words)
                .orElseThrow(() -> new IllegalArgumentException("Unknown command: " + String.join(" ", words)));
        return new CommandLine(command, config, atLogin);
    }
}

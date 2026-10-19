package nl.enadeag.carchargeplanner.cli;

import nl.enadeag.carchargeplanner.desktop.system.StartAtLogin;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.host.AppName;
import nl.enadeag.carchargeplanner.host.DataLocation;
import nl.enadeag.carchargeplanner.host.Host;
import nl.enadeag.carchargeplanner.host.Os;
import nl.enadeag.carchargeplanner.instance.InstanceCommand;
import nl.enadeag.carchargeplanner.instance.NotPrivateException;
import nl.enadeag.carchargeplanner.instance.Reply;
import nl.enadeag.carchargeplanner.instance.SingleInstance;
import nl.enadeag.carchargeplanner.platform.linux.LinuxMenuEntry;
import nl.enadeag.carchargeplanner.platforms.PlatformParts;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The commands of the command line that do one thing and end: quit the running app, have it show a test
 * notification, switch start at login, and install or uninstall the Linux menu entry. Each gives its exit code and what to tell the owner, and prints nothing.
 */
public final class Commands {

    private final Messages messages;
    private final SingleInstance instance;
    private final LoginSwitch startAtLogin;
    private final MenuEntry menuEntry;

    /** The system's start at login, or why the commands cannot switch it. */
    sealed interface LoginSwitch {

        /**
         * The system's own start at login.
         *
         * @param startAtLogin what switches it
         */
        record Present(StartAtLogin startAtLogin) implements LoginSwitch { }

        /** Only the installed app, run from its launcher, can start itself at login. */
        record OnlyFromLauncher() implements LoginSwitch { }
    }

    /** The Linux menu entry, or why the commands cannot install it. */
    sealed interface MenuEntry {

        /**
         * The menu entry for the program that runs from its launcher.
         *
         * @param os the system, as the menu entry is Linux's only
         * @param entry the menu entry for that program
         * @return the entry on Linux; elsewhere, that it is Linux's only
         */
        static MenuEntry of(Os os, LinuxMenuEntry entry) {
            return os == Os.LINUX ? new Present(entry) : new LinuxOnly();
        }

        /**
         * No menu entry, as the app does not run from its launcher.
         *
         * @param os the system, as the menu entry is Linux's only
         * @return that only the installed app can put itself in the menu; elsewhere than on Linux, that the entry is
         *     Linux's only
         */
        static MenuEntry none(Os os) {
            return os == Os.LINUX ? new OnlyFromLauncher() : new LinuxOnly();
        }

        /**
         * The menu entry for the program that runs.
         *
         * @param entry what installs and removes it
         */
        record Present(LinuxMenuEntry entry) implements MenuEntry { }

        /** The menu entry is Linux's only. */
        record LinuxOnly() implements MenuEntry { }

        /** Only the installed app, run from its launcher, can put itself in the menu. */
        record OnlyFromLauncher() implements MenuEntry { }
    }

    /**
     * Makes the commands; nothing is done yet.
     *
     * @param messages the texts in the owner's language
     * @param instance the single-instance guard, to reach the running app
     * @param startAtLogin the system's start at login, or why it cannot be switched
     * @param menuEntry the Linux menu entry, or why it cannot be installed
     */
    Commands(Messages messages, SingleInstance instance, LoginSwitch startAtLogin, MenuEntry menuEntry) {
        this.messages = messages;
        this.instance = instance;
        this.startAtLogin = startAtLogin;
        this.menuEntry = menuEntry;
    }

    /**
     * The commands for this system, with its own start at login and menu entry.
     *
     * @param host the system and its user
     * @param parts the system's parts, for start at login
     * @param instance the single-instance guard, to reach the running app
     * @param messages the texts in the owner's language
     * @return the commands
     */
    public static Commands of(Host host, PlatformParts parts, SingleInstance instance, Messages messages) {
        var dataHome = DataLocation.systemFolder(host.os(), host.environment(), host.home());
        var menuEntry = host.launcher()
                .map(program -> MenuEntry.of(host.os(), new LinuxMenuEntry(dataHome, program, AppName.ID,
                        AppName.TITLE, LinuxMenuEntry::refreshCaches)))
                .orElseGet(() -> MenuEntry.none(host.os()));
        var startAtLogin = parts.startAtLogin().<LoginSwitch>map(LoginSwitch.Present::new)
                .orElseGet(LoginSwitch.OnlyFromLauncher::new);
        return new Commands(messages, instance, startAtLogin, menuEntry);
    }

    /**
     * Carries out a command.
     *
     * @param command any command but {@link CommandLine.Command#RUN}, which starts the app instead
     * @return the exit code and what to tell the owner
     * @throws IllegalArgumentException for {@link CommandLine.Command#RUN}
     */
    public CommandOutcome carryOut(CommandLine.Command command) {
        return switch (command) {
            case RUN -> throw new IllegalArgumentException("running the app is not a command that ends");
            case QUIT -> quitRunningApp();
            case TEST_NOTIFICATION -> showTestNotification();
            case START_AT_LOGIN_ON -> switchStartAtLogin(true);
            case START_AT_LOGIN_OFF -> switchStartAtLogin(false);
            case INSTALL_LAUNCHER -> switchLauncher(true);
            case UNINSTALL_LAUNCHER -> switchLauncher(false);
        };
    }

    private CommandOutcome quitRunningApp() {
        return askRunningApp(InstanceCommand.QUIT, messages.text(TextKey.CLI_QUIT_CONFIRMED, AppName.TITLE),
                success(messages.text(TextKey.CLI_NOT_RUNNING, AppName.TITLE)));
    }

    private CommandOutcome showTestNotification() {
        return askRunningApp(InstanceCommand.TEST_NOTIFICATION,
                messages.text(TextKey.CLI_TEST_NOTIFICATION_SHOWN, AppName.TITLE),
                new CommandOutcome(ExitCode.UNREACHABLE,
                        messages.text(TextKey.CLI_TEST_NOTIFICATION_NOT_RUNNING, AppName.TITLE)));
    }

    /** Sends a command to the running app; what to say when it confirmed, and what comes of it when none runs. */
    private CommandOutcome askRunningApp(InstanceCommand command, String confirmed, CommandOutcome notRunning) {
        Reply reply;
        try {
            reply = instance.send(command);
        } catch (NotPrivateException e) {
            // may be someone's attempt to get in the way, so say what is wrong instead of "not running"
            return failure(messages.text(TextKey.CLI_COMMAND_NOT_PRIVATE, AppName.TITLE, e.getMessage()));
        } catch (IOException e) {
            // only a missing folder means that no copy runs, and that is a reply; this is something else in the way
            return failure(messages.text(TextKey.CLI_COMMAND_FAILED, AppName.TITLE, e.toString()));
        }
        return switch (reply) {
            case ANSWERED -> success(confirmed);
            case SILENT -> failure(messages.text(TextKey.CLI_COMMAND_SILENT, AppName.TITLE));
            case UNREACHABLE -> new CommandOutcome(ExitCode.UNREACHABLE,
                    messages.text(TextKey.CLI_COMMAND_UNREACHABLE, AppName.TITLE));
            case NOBODY, STALE -> notRunning;
        };
    }

    private CommandOutcome switchStartAtLogin(boolean on) {
        return switch (startAtLogin) {
            case LoginSwitch.OnlyFromLauncher _ -> failure(messages.text(TextKey.CLI_START_AT_LOGIN_NEEDS_INSTALL));
            case LoginSwitch.Present(var system) -> switchStartAtLogin(system, on);
        };
    }

    private CommandOutcome switchStartAtLogin(StartAtLogin system, boolean on) {
        try {
            if (on) {
                system.switchOn();
                return success(messages.text(TextKey.CLI_START_AT_LOGIN_ON, AppName.TITLE));
            }
            system.switchOff();
            return success(messages.text(TextKey.CLI_START_AT_LOGIN_OFF, AppName.TITLE));
        } catch (IOException e) {
            return failure(messages.text(TextKey.CLI_START_AT_LOGIN_FAILED, String.valueOf(e.getMessage())));
        }
    }

    private CommandOutcome switchLauncher(boolean install) {
        return switch (menuEntry) {
            case MenuEntry.LinuxOnly _ -> failure(messages.text(TextKey.CLI_LAUNCHER_LINUX_ONLY, AppName.TITLE));
            case MenuEntry.OnlyFromLauncher _ -> failure(messages.text(TextKey.CLI_LAUNCHER_NEEDS_INSTALL));
            case MenuEntry.Present(var entry) -> switchLauncher(entry, install);
        };
    }

    private CommandOutcome switchLauncher(LinuxMenuEntry entry, boolean install) {
        try {
            if (install) {
                return success(messages.text(TextKey.CLI_LAUNCHER_INSTALLED, AppName.TITLE, lines(entry.install())));
            }
            var removed = entry.uninstall();
            return success(removed.isEmpty() ? messages.text(TextKey.CLI_LAUNCHER_NOT_FOUND, AppName.TITLE)
                    : messages.text(TextKey.CLI_LAUNCHER_REMOVED, AppName.TITLE, lines(removed)));
        } catch (IOException e) {
            return failure(messages.text(install ? TextKey.CLI_LAUNCHER_INSTALL_FAILED
                    : TextKey.CLI_LAUNCHER_REMOVE_FAILED, String.valueOf(e.getMessage())));
        }
    }

    private static String lines(List<Path> files) {
        return files.stream().map(file -> "  " + file).collect(Collectors.joining("\n"));
    }

    private static CommandOutcome success(String text) {
        return new CommandOutcome(ExitCode.SUCCESS, text);
    }

    private static CommandOutcome failure(String text) {
        return new CommandOutcome(ExitCode.FAILURE, text);
    }
}

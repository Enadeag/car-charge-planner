package io.github.enadeag.carchargeplanner;

import io.github.enadeag.carchargeplanner.desktop.Messages;
import io.github.enadeag.carchargeplanner.desktop.StartAtLogin;
import io.github.enadeag.carchargeplanner.desktop.TextKey;
import io.github.enadeag.carchargeplanner.instance.InstanceCommand;
import io.github.enadeag.carchargeplanner.instance.Reply;
import io.github.enadeag.carchargeplanner.instance.SingleInstance;
import io.github.enadeag.carchargeplanner.platform.linux.LinuxMenuEntry;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The commands of the command line that do one thing and end: quit the running app, switch start at login, and
 * install or uninstall the Linux menu entry. Each gives its exit code and what to tell the owner, and prints nothing.
 */
final class Commands {

    private final Os os;
    private final Messages messages;
    private final SingleInstance instance;
    private final Optional<StartAtLogin> startAtLogin;
    private final Optional<LinuxMenuEntry> menuEntry;

    /**
     * Makes the commands; nothing is done yet.
     *
     * @param os the system, as the menu entry is Linux's only
     * @param messages the texts in the owner's language
     * @param instance the single-instance guard, to reach the running app
     * @param startAtLogin the system's start at login; empty when the app does not run from its launcher
     * @param menuEntry the Linux menu entry; empty when the app does not run from its launcher, or not on Linux
     */
    Commands(Os os, Messages messages, SingleInstance instance, Optional<StartAtLogin> startAtLogin,
            Optional<LinuxMenuEntry> menuEntry) {
        this.os = os;
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
    static Commands of(Host host, PlatformParts parts, SingleInstance instance, Messages messages) {
        var dataHome = DataLocation.systemFolder(host.os(), host.environment(), host.home());
        var menuEntry = host.launcher().filter(program -> host.os() == Os.LINUX)
                .map(program -> new LinuxMenuEntry(dataHome, program, AppName.TITLE, LinuxMenuEntry::refreshCaches));
        return new Commands(host.os(), messages, instance, parts.startAtLogin(), menuEntry);
    }

    /**
     * Carries out a command.
     *
     * @param command any command but {@link CommandLine.Command#RUN}, which starts the app instead
     * @return the exit code and what to tell the owner
     * @throws IllegalArgumentException for {@link CommandLine.Command#RUN}
     */
    CommandOutcome run(CommandLine.Command command) {
        return switch (command) {
            case RUN -> throw new IllegalArgumentException("running the app is not a command that ends");
            case QUIT -> quitRunningApp();
            case START_AT_LOGIN_ON -> switchStartAtLogin(true);
            case START_AT_LOGIN_OFF -> switchStartAtLogin(false);
            case INSTALL_LAUNCHER -> switchLauncher(true);
            case UNINSTALL_LAUNCHER -> switchLauncher(false);
        };
    }

    private CommandOutcome quitRunningApp() {
        try {
            var reply = instance.send(InstanceCommand.QUIT);
            if (reply == Reply.ANSWERED) {
                return success(messages.text(TextKey.CLI_QUIT_CONFIRMED, AppName.TITLE));
            }
            if (reply == Reply.SILENT) {
                return failure(messages.text(TextKey.CLI_QUIT_SILENT, AppName.TITLE));
            }
        } catch (IOException e) {
            // no private folder for the socket, so no running copy can have made one there
        }
        return success(messages.text(TextKey.CLI_NOT_RUNNING, AppName.TITLE));
    }

    private CommandOutcome switchStartAtLogin(boolean on) {
        if (startAtLogin.isEmpty()) {
            return failure(messages.text(TextKey.CLI_START_AT_LOGIN_NEEDS_INSTALL));
        }
        try {
            if (on) {
                startAtLogin.get().switchOn();
                return success(messages.text(TextKey.CLI_START_AT_LOGIN_ON, AppName.TITLE));
            }
            startAtLogin.get().switchOff();
            return success(messages.text(TextKey.CLI_START_AT_LOGIN_OFF, AppName.TITLE));
        } catch (IOException e) {
            return failure(messages.text(TextKey.CLI_START_AT_LOGIN_FAILED, String.valueOf(e.getMessage())));
        }
    }

    private CommandOutcome switchLauncher(boolean install) {
        if (os != Os.LINUX) {
            return failure(messages.text(TextKey.CLI_LAUNCHER_LINUX_ONLY, AppName.TITLE));
        }
        if (menuEntry.isEmpty()) {
            return failure(messages.text(TextKey.CLI_LAUNCHER_NEEDS_INSTALL));
        }
        try {
            if (install) {
                return success(messages.text(TextKey.CLI_LAUNCHER_INSTALLED, AppName.TITLE,
                        lines(menuEntry.get().install())));
            }
            var removed = menuEntry.get().uninstall();
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

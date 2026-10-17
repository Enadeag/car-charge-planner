package io.github.enadeag.carchargeplanner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.enadeag.carchargeplanner.CommandLine.Command;
import io.github.enadeag.carchargeplanner.desktop.Language;
import io.github.enadeag.carchargeplanner.desktop.Messages;
import io.github.enadeag.carchargeplanner.desktop.StartAtLogin;
import io.github.enadeag.carchargeplanner.desktop.TextKey;
import io.github.enadeag.carchargeplanner.instance.Claim;
import io.github.enadeag.carchargeplanner.instance.InstanceCommand;
import io.github.enadeag.carchargeplanner.instance.SingleInstance;
import io.github.enadeag.carchargeplanner.platform.linux.LinuxMenuEntry;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** The commands that do one thing and end, each with what it tells the owner and its exit code. */
class CommandsTest {

    private static final Messages MESSAGES = new Messages(Language.ENGLISH);
    private static final Path PROGRAM = Path.of("/opt/car-charge-planner/bin/car-charge-planner");

    @TempDir
    Path temp;

    private Path instanceFolder;
    private Optional<UserPrincipal> user;
    private final List<AutoCloseable> toClose = new ArrayList<>();
    private final FakeStartAtLogin startAtLogin = new FakeStartAtLogin();

    /** Start at login kept in a field, which a test can make fail. */
    private static final class FakeStartAtLogin implements StartAtLogin {
        boolean on;
        Optional<IOException> failure = Optional.empty();

        @Override
        public boolean isOn() {
            return on;
        }

        @Override
        public void switchOn() throws IOException {
            if (failure.isPresent()) {
                throw failure.get();
            }
            on = true;
        }

        @Override
        public void switchOff() throws IOException {
            if (failure.isPresent()) {
                throw failure.get();
            }
            on = false;
        }
    }

    @BeforeEach
    void setUp() throws IOException {
        instanceFolder = temp.resolve("instance");
        user = hasFileModes() ? Optional.of(Files.getOwner(temp)) : Optional.empty();
    }

    @AfterEach
    void closeAll() throws Exception {
        for (var closeable : toClose.reversed()) {
            closeable.close();
        }
    }

    private boolean hasFileModes() {
        return temp.getFileSystem().supportedFileAttributeViews().contains("posix");
    }

    private SingleInstance instance() {
        var instance = new SingleInstance(instanceFolder, user, Duration.ofMillis(500));
        toClose.add(instance);
        return instance;
    }

    private Commands commands(Os os, Optional<StartAtLogin> login, Optional<LinuxMenuEntry> menuEntry) {
        return new Commands(os, MESSAGES, instance(), login, menuEntry);
    }

    private Commands commands() {
        return commands(Os.LINUX, Optional.of(startAtLogin), Optional.empty());
    }

    private LinuxMenuEntry menuEntry(Path dataHome) {
        return new LinuxMenuEntry(dataHome, PROGRAM, AppName.TITLE, refreshed -> { });
    }

    private static CommandOutcome success(TextKey key, Object... values) {
        return new CommandOutcome(ExitCode.SUCCESS, MESSAGES.text(key, values));
    }

    private static CommandOutcome failure(TextKey key, Object... values) {
        return new CommandOutcome(ExitCode.FAILURE, MESSAGES.text(key, values));
    }

    @Test
    void quitAsksTheRunningAppToQuitAndSaysItDid() throws Exception {
        var received = new LinkedBlockingQueue<InstanceCommand>();
        assertThat(instance().claim(received::add)).isEqualTo(Claim.FIRST);

        var outcome = commands().run(Command.QUIT);

        assertThat(received.poll(5, TimeUnit.SECONDS)).isEqualTo(InstanceCommand.QUIT);
        assertThat(outcome).isEqualTo(success(TextKey.CLI_QUIT_CONFIRMED, AppName.TITLE));
    }

    @Test
    void quitWithoutARunningAppSaysSoAndSucceeds() {
        assertThat(commands().run(Command.QUIT)).isEqualTo(success(TextKey.CLI_NOT_RUNNING, AppName.TITLE));
    }

    @Test
    void quitFailsWhenTheRunningAppDoesNotAnswer() throws Exception {
        FileAttribute<?>[] onlyMine = hasFileModes()
                ? new FileAttribute<?>[] {PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))}
                : new FileAttribute<?>[0];
        Files.createDirectory(instanceFolder, onlyMine);
        // listens, but never takes the connection, so the command is never confirmed
        var silent = ServerSocketChannel.open(StandardProtocolFamily.UNIX)
                .bind(UnixDomainSocketAddress.of(instanceFolder.resolve("instance.sock")));
        toClose.add(silent);

        assertThat(commands().run(Command.QUIT)).isEqualTo(failure(TextKey.CLI_QUIT_SILENT, AppName.TITLE));
    }

    @Test
    void startAtLoginIsSwitchedOnAndOff() {
        assertThat(commands().run(Command.START_AT_LOGIN_ON))
                .isEqualTo(success(TextKey.CLI_START_AT_LOGIN_ON, AppName.TITLE));
        assertThat(startAtLogin.on).isTrue();

        assertThat(commands().run(Command.START_AT_LOGIN_OFF))
                .isEqualTo(success(TextKey.CLI_START_AT_LOGIN_OFF, AppName.TITLE));
        assertThat(startAtLogin.on).isFalse();
    }

    @Test
    void startAtLoginThatCannotBeWrittenFailsWithTheReason() {
        startAtLogin.failure = Optional.of(new IOException("the folder is read-only"));

        assertThat(commands().run(Command.START_AT_LOGIN_ON))
                .isEqualTo(failure(TextKey.CLI_START_AT_LOGIN_FAILED, "the folder is read-only"));
    }

    @Test
    void startAtLoginNeedsTheInstalledApp() {
        assertThat(commands(Os.LINUX, Optional.empty(), Optional.empty()).run(Command.START_AT_LOGIN_ON))
                .isEqualTo(failure(TextKey.CLI_START_AT_LOGIN_NEEDS_INSTALL));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void theLauncherIsInstalledAndRemovedAgainNamingItsFiles() throws Exception {
        var dataHome = temp.resolve("data");
        var commands = commands(Os.LINUX, Optional.empty(), Optional.of(menuEntry(dataHome)));

        var installed = commands.run(Command.INSTALL_LAUNCHER);
        var entry = dataHome.resolve("applications/car-charge-planner.desktop");
        assertThat(entry).exists();
        assertThat(installed.exit()).isEqualTo(ExitCode.SUCCESS);
        assertThat(installed.text()).contains("  " + entry);

        var removed = commands.run(Command.UNINSTALL_LAUNCHER);
        assertThat(entry).doesNotExist();
        assertThat(removed.exit()).isEqualTo(ExitCode.SUCCESS);
        assertThat(removed.text()).contains("  " + entry);

        assertThat(commands.run(Command.UNINSTALL_LAUNCHER))
                .isEqualTo(success(TextKey.CLI_LAUNCHER_NOT_FOUND, AppName.TITLE));
    }

    @Test
    void theLauncherIsLinuxOnly() {
        assertThat(commands(Os.WINDOWS, Optional.empty(), Optional.of(menuEntry(temp))).run(Command.INSTALL_LAUNCHER))
                .isEqualTo(failure(TextKey.CLI_LAUNCHER_LINUX_ONLY, AppName.TITLE));
    }

    @Test
    void theLauncherNeedsTheInstalledApp() {
        assertThat(commands(Os.LINUX, Optional.empty(), Optional.empty()).run(Command.INSTALL_LAUNCHER))
                .isEqualTo(failure(TextKey.CLI_LAUNCHER_NEEDS_INSTALL));
    }

    @Test
    void runIsNotACommandThatEnds() {
        assertThatThrownBy(() -> commands().run(Command.RUN)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void successGoesToStandardOutputAndAFailureToStandardError() {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        try (var outStream = new PrintStream(out, true, StandardCharsets.UTF_8);
                var errStream = new PrintStream(err, true, StandardCharsets.UTF_8)) {
            new CommandOutcome(ExitCode.SUCCESS, "done").print(outStream, errStream);
            new CommandOutcome(ExitCode.FAILURE, "failed").print(outStream, errStream);
        }

        assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo("done" + System.lineSeparator());
        assertThat(err.toString(StandardCharsets.UTF_8)).isEqualTo("failed" + System.lineSeparator());
    }
}

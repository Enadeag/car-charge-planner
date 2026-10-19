package nl.enadeag.carchargeplanner.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import nl.enadeag.carchargeplanner.cli.CommandLine.Command;
import nl.enadeag.carchargeplanner.desktop.system.StartAtLogin;
import nl.enadeag.carchargeplanner.desktop.text.Language;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.host.Host;
import nl.enadeag.carchargeplanner.host.Os;
import nl.enadeag.carchargeplanner.instance.ClaimOutcome;
import nl.enadeag.carchargeplanner.instance.InstanceCommand;
import nl.enadeag.carchargeplanner.instance.Owner;
import nl.enadeag.carchargeplanner.instance.SingleInstance;
import nl.enadeag.carchargeplanner.platform.linux.ApplicationMenuEntry;
import nl.enadeag.carchargeplanner.platforms.PlatformParts;
import nl.enadeag.carchargeplanner.host.AppName;
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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** The commands that do one thing and end, each with what it tells the owner and its exit code. */
class CommandsTest {

    private static final Messages MESSAGES = new Messages(Language.ENGLISH);
    private static final Path PROGRAM = Path.of("/opt/car-charge-planner/bin/car-charge-planner");

    @TempDir
    Path temp;

    private Path instanceFolder;
    private Owner owner;
    private final List<AutoCloseable> toClose = new ArrayList<>();
    private final FakeStartAtLogin startAtLogin = new FakeStartAtLogin();

    /** Start at login kept in a field, which a test can make fail. */
    private static final class FakeStartAtLogin implements StartAtLogin {
        boolean on;
        /** Null unless switching fails. */
        IOException failure;

        @Override
        public boolean isOn() {
            return on;
        }

        @Override
        public void switchOn() throws IOException {
            if (failure != null) {
                throw failure;
            }
            on = true;
        }

        @Override
        public void switchOff() throws IOException {
            if (failure != null) {
                throw failure;
            }
            on = false;
        }
    }

    @BeforeEach
    void setUp() throws IOException {
        instanceFolder = temp.resolve("instance");
        owner = hasFileModes() ? new Owner.User(Files.getOwner(temp)) : new Owner.Unchecked();
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

    private void makeInstanceFolder() throws IOException {
        FileAttribute<?>[] onlyMine = hasFileModes()
                ? new FileAttribute<?>[] {PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))}
                : new FileAttribute<?>[0];
        Files.createDirectory(instanceFolder, onlyMine);
    }

    private SingleInstance instance() {
        var instance = new SingleInstance(instanceFolder, owner, Duration.ofMillis(500));
        toClose.add(instance);
        return instance;
    }

    private Commands commands(Os os, Optional<StartAtLogin> login, Optional<ApplicationMenuEntry> menuEntry) {
        return new Commands(MESSAGES, instance(),
                login.<Commands.LoginSwitch>map(Commands.LoginSwitch.Present::new)
                        .orElseGet(Commands.LoginSwitch.OnlyFromLauncher::new),
                menuEntry.map(entry -> Commands.MenuEntry.of(os, entry)).orElseGet(() -> Commands.MenuEntry.none(os)));
    }

    private Commands commands() {
        return commands(Os.LINUX, Optional.of(startAtLogin), Optional.empty());
    }

    private ApplicationMenuEntry menuEntry(Path dataHome) {
        return new ApplicationMenuEntry(dataHome, PROGRAM, AppName.ID, AppName.TITLE, _ -> { });
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
        assertThat(instance().claim(received::add)).isEqualTo(ClaimOutcome.RUNNING);

        var outcome = commands().carryOut(Command.QUIT);

        assertThat(received.poll(5, TimeUnit.SECONDS)).isEqualTo(InstanceCommand.QUIT);
        assertThat(outcome).isEqualTo(success(TextKey.CLI_QUIT_CONFIRMED, AppName.TITLE));
    }

    @Test
    void quitWithoutARunningAppSaysSoAndSucceeds() {
        assertThat(commands().carryOut(Command.QUIT)).isEqualTo(success(TextKey.CLI_NOT_RUNNING, AppName.TITLE));
    }

    @Test
    void testNotificationAsksTheRunningAppToShowOneAndSaysItDid() throws Exception {
        var received = new LinkedBlockingQueue<InstanceCommand>();
        assertThat(instance().claim(received::add)).isEqualTo(ClaimOutcome.RUNNING);

        var outcome = commands().carryOut(Command.TEST_NOTIFICATION);

        assertThat(received.poll(5, TimeUnit.SECONDS)).isEqualTo(InstanceCommand.TEST_NOTIFICATION);
        assertThat(outcome).isEqualTo(success(TextKey.CLI_TEST_NOTIFICATION_SHOWN, AppName.TITLE));
    }

    @Test
    void testNotificationWithoutARunningAppSaysSoAndEndsWithTheNotRunningCode() {
        assertThat(commands().carryOut(Command.TEST_NOTIFICATION)).isEqualTo(new CommandOutcome(ExitCode.NOT_RUNNING,
                MESSAGES.text(TextKey.CLI_TEST_NOTIFICATION_NOT_RUNNING, AppName.TITLE)));
    }

    @Test
    void testNotificationAfterACrashSaysTheAppDoesNotRun() throws Exception {
        makeInstanceFolder();
        // a socket that nothing listens on and no lock: what a crashed app leaves behind
        ServerSocketChannel.open(StandardProtocolFamily.UNIX)
                .bind(UnixDomainSocketAddress.of(instanceFolder.resolve("instance.sock"))).close();

        assertThat(commands().carryOut(Command.TEST_NOTIFICATION)).isEqualTo(new CommandOutcome(ExitCode.NOT_RUNNING,
                MESSAGES.text(TextKey.CLI_TEST_NOTIFICATION_NOT_RUNNING, AppName.TITLE)));
    }

    @ParameterizedTest
    @EnumSource(names = {"QUIT", "TEST_NOTIFICATION"})
    void aCommandSaysTheAppRunsButCannotBeReachedWhenItHoldsTheLockWithoutASocket(Command command) {
        // longer than a Unix domain socket's path may be, so the running app has the lock but no socket
        instanceFolder = temp.resolve("a-folder-with-a-long-name-" + "x".repeat(100));
        assertThat(instance().claim(_ -> { })).isEqualTo(ClaimOutcome.RUNNING_UNREACHABLE);

        assertThat(commands().carryOut(command)).isEqualTo(
                new CommandOutcome(ExitCode.UNREACHABLE, MESSAGES.text(TextKey.CLI_COMMAND_UNREACHABLE, AppName.TITLE)));
    }

    @ParameterizedTest
    @EnumSource(names = {"QUIT", "TEST_NOTIFICATION"})
    void aCommandFailsWhenTheRunningAppDoesNotAnswer(Command command) throws Exception {
        makeInstanceFolder();
        // listens, but never takes the connection, so the command is never confirmed
        var silent = ServerSocketChannel.open(StandardProtocolFamily.UNIX)
                .bind(UnixDomainSocketAddress.of(instanceFolder.resolve("instance.sock")));
        toClose.add(silent);

        assertThat(commands().carryOut(command)).isEqualTo(failure(TextKey.CLI_COMMAND_SILENT, AppName.TITLE));
    }

    @ParameterizedTest
    @EnumSource(names = {"QUIT", "TEST_NOTIFICATION"})
    void aCommandIsNotSentWhenTheInstanceFolderIsNotPrivate(Command command) throws IOException {
        try {
            Files.createSymbolicLink(instanceFolder, Files.createDirectory(temp.resolve("elsewhere")));
        } catch (IOException | UnsupportedOperationException e) {
            assumeTrue(false, "this account may not make links: " + e);
            return;
        }

        var outcome = commands().carryOut(command);

        // not "not running": a folder in the way may be someone's doing, so the owner is told what is wrong
        var said = MESSAGES.text(TextKey.CLI_COMMAND_NOT_PRIVATE, AppName.TITLE, "%s");
        assertThat(outcome.exit()).isEqualTo(ExitCode.FAILURE);
        assertThat(outcome.text()).startsWith(said.substring(0, said.indexOf("%s")))
                .contains(instanceFolder.getFileName().toString());
    }

    @ParameterizedTest
    @EnumSource(names = {"QUIT", "TEST_NOTIFICATION"})
    void aCommandFailsWithTheReasonWhenWhetherTheAppRunsCannotBeTold(Command command) throws IOException {
        makeInstanceFolder();
        Files.createDirectory(instanceFolder.resolve("instance.lock")); // a lock file that cannot be opened

        var outcome = commands().carryOut(command);

        var said = MESSAGES.text(TextKey.CLI_COMMAND_FAILED, AppName.TITLE, "%s");
        assertThat(outcome.exit()).isEqualTo(ExitCode.FAILURE);
        assertThat(outcome.text()).startsWith(said.substring(0, said.indexOf("%s")))
                .contains("instance.lock");
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void aCommandLeavesAnInstanceFolderOthersMayReadAsItIsAndSaysWhatIsWrong() throws IOException {
        Files.createDirectory(instanceFolder);
        Files.setPosixFilePermissions(instanceFolder, PosixFilePermissions.fromString("rwxr-x---"));

        var outcome = commands().carryOut(Command.QUIT);

        var said = MESSAGES.text(TextKey.CLI_COMMAND_NOT_PRIVATE, AppName.TITLE, "%s");
        assertThat(outcome.exit()).isEqualTo(ExitCode.FAILURE);
        assertThat(outcome.text()).startsWith(said.substring(0, said.indexOf("%s")));
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(instanceFolder)))
                .isEqualTo("rwxr-x---");
    }

    @Test
    void startAtLoginIsSwitchedOnAndOff() {
        assertThat(commands().carryOut(Command.START_AT_LOGIN_ON))
                .isEqualTo(success(TextKey.CLI_START_AT_LOGIN_ON, AppName.TITLE));
        assertThat(startAtLogin.on).isTrue();

        assertThat(commands().carryOut(Command.START_AT_LOGIN_OFF))
                .isEqualTo(success(TextKey.CLI_START_AT_LOGIN_OFF, AppName.TITLE));
        assertThat(startAtLogin.on).isFalse();
    }

    @Test
    void startAtLoginThatCannotBeWrittenFailsWithTheReason() {
        startAtLogin.failure = new IOException("the folder is read-only");

        assertThat(commands().carryOut(Command.START_AT_LOGIN_ON))
                .isEqualTo(failure(TextKey.CLI_START_AT_LOGIN_FAILED, "the folder is read-only"));
    }

    @Test
    void startAtLoginNeedsTheInstalledApp() {
        assertThat(commands(Os.LINUX, Optional.empty(), Optional.empty()).carryOut(Command.START_AT_LOGIN_ON))
                .isEqualTo(failure(TextKey.CLI_START_AT_LOGIN_NEEDS_INSTALL));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void theLauncherIsInstalledAndRemovedAgainNamingItsFiles() throws Exception {
        var dataHome = temp.resolve("data");
        var commands = commands(Os.LINUX, Optional.empty(), Optional.of(menuEntry(dataHome)));

        var installed = commands.carryOut(Command.INSTALL_LAUNCHER);
        var entry = dataHome.resolve("applications/car-charge-planner.desktop");
        assertThat(entry).exists();
        assertThat(installed.exit()).isEqualTo(ExitCode.SUCCESS);
        assertThat(installed.text()).contains("  " + entry);

        var removed = commands.carryOut(Command.UNINSTALL_LAUNCHER);
        assertThat(entry).doesNotExist();
        assertThat(removed.exit()).isEqualTo(ExitCode.SUCCESS);
        assertThat(removed.text()).contains("  " + entry);

        assertThat(commands.carryOut(Command.UNINSTALL_LAUNCHER))
                .isEqualTo(success(TextKey.CLI_LAUNCHER_NOT_FOUND, AppName.TITLE));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void aLauncherThatCannotBeWrittenOrRemovedFailsWithTheReason() throws Exception {
        // a file where the data folder should be: nothing can be written below it
        var notAFolder = Files.createFile(temp.resolve("not-a-folder"));
        var install = commands(Os.LINUX, Optional.empty(), Optional.of(menuEntry(notAFolder)))
                .carryOut(Command.INSTALL_LAUNCHER);
        assertThat(install.exit()).isEqualTo(ExitCode.FAILURE);
        assertThat(install.text()).startsWith(MESSAGES.text(TextKey.CLI_LAUNCHER_INSTALL_FAILED, ""))
                .contains(notAFolder.toString());

        // a folder with something in it where the entry is: it cannot be removed
        var dataHome = temp.resolve("data");
        Files.createDirectories(dataHome.resolve("applications/car-charge-planner.desktop/inside"));
        var uninstall = commands(Os.LINUX, Optional.empty(), Optional.of(menuEntry(dataHome)))
                .carryOut(Command.UNINSTALL_LAUNCHER);
        assertThat(uninstall.exit()).isEqualTo(ExitCode.FAILURE);
        assertThat(uninstall.text()).startsWith(MESSAGES.text(TextKey.CLI_LAUNCHER_REMOVE_FAILED, ""))
                .contains("car-charge-planner.desktop");
    }

    @Test
    void theLauncherIsLinuxOnly() {
        assertThat(commands(Os.WINDOWS, Optional.empty(), Optional.of(menuEntry(temp)))
                .carryOut(Command.INSTALL_LAUNCHER))
                .isEqualTo(failure(TextKey.CLI_LAUNCHER_LINUX_ONLY, AppName.TITLE));
    }

    @Test
    void theCommandsForASystemOfferTheMenuEntryOnLinuxOnlyAndBothSwitchesOnlyFromTheLauncher() {
        var windows = new Host(Os.WINDOWS, Map.of("LOCALAPPDATA", temp.toString()), temp, temp, new Owner.Unchecked(),
                Optional.of(PROGRAM), false);
        var fromTheBuild = new Host(Os.LINUX, Map.of(), temp, temp, new Owner.Unchecked(), Optional.empty(), false);
        var linux = Commands.of(fromTheBuild, PlatformParts.of(fromTheBuild), instance(), MESSAGES);

        assertThat(Commands.of(windows, PlatformParts.of(windows), instance(), MESSAGES)
                .carryOut(Command.INSTALL_LAUNCHER))
                .isEqualTo(failure(TextKey.CLI_LAUNCHER_LINUX_ONLY, AppName.TITLE));
        assertThat(linux.carryOut(Command.INSTALL_LAUNCHER)).isEqualTo(failure(TextKey.CLI_LAUNCHER_NEEDS_INSTALL));
        assertThat(linux.carryOut(Command.START_AT_LOGIN_ON))
                .isEqualTo(failure(TextKey.CLI_START_AT_LOGIN_NEEDS_INSTALL));
    }

    @Test
    void theLauncherNeedsTheInstalledApp() {
        assertThat(commands(Os.LINUX, Optional.empty(), Optional.empty()).carryOut(Command.INSTALL_LAUNCHER))
                .isEqualTo(failure(TextKey.CLI_LAUNCHER_NEEDS_INSTALL));
    }

    @Test
    void runIsNotACommandThatEnds() {
        assertThatThrownBy(() -> commands().carryOut(Command.RUN)).isInstanceOf(IllegalArgumentException.class);
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

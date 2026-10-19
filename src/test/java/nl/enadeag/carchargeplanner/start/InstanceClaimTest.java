package nl.enadeag.carchargeplanner.start;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import nl.enadeag.carchargeplanner.cli.ExitCode;
import nl.enadeag.carchargeplanner.desktop.text.Language;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.instance.InstanceCommand;
import nl.enadeag.carchargeplanner.instance.SingleInstance;
import nl.enadeag.carchargeplanner.logging.AppLog;
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
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** What the app tells the owner, logs and ends with after its single-instance claim. */
class InstanceClaimTest {

    private static final Messages MESSAGES = new Messages(Language.ENGLISH);

    @TempDir
    Path temp;

    private Path folder;
    private Optional<UserPrincipal> user;
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();
    private final List<AutoCloseable> toClose = new ArrayList<>();
    private AppLog log;
    private System.Logger logger;

    @BeforeEach
    void setUp() throws IOException {
        // longer than a Unix domain socket's path may be: the lock works, the socket cannot be made
        folder = temp.resolve("a-folder-with-a-long-name-" + "x".repeat(100));
        user = temp.getFileSystem().supportedFileAttributeViews().contains("posix")
                ? Optional.of(Files.getOwner(temp)) : Optional.empty();
        var name = "instance-claim-test-" + UUID.randomUUID();
        var root = Logger.getLogger(name);
        root.setUseParentHandlers(false);
        var app = Logger.getLogger(name + ".app");
        log = new AppLog(root, app);
        logger = System.getLogger(app.getName() + ".claim");
    }

    @AfterEach
    void tearDown() throws Exception {
        log.close();
        for (var closeable : toClose.reversed()) {
            closeable.close();
        }
    }

    private SingleInstance instance() {
        var instance = new SingleInstance(folder, user, Duration.ofMillis(500));
        toClose.add(instance);
        return instance;
    }

    private InstanceClaim claim(SingleInstance instance) {
        return new InstanceClaim(instance, MESSAGES, new PrintStream(err, true, StandardCharsets.UTF_8), logger);
    }

    private InstanceClaim claim() {
        return claim(instance());
    }

    private String said() {
        return err.toString(StandardCharsets.UTF_8).strip();
    }

    @Test
    void theFirstCopyWithoutASocketRunsSaysSoAndLogsAWarningOnceTheLogFileIsOpen() throws IOException {
        var first = claim();
        assertThat(first.claim(command -> { })).isEmpty();
        assertThat(said()).isEqualTo(MESSAGES.text(TextKey.CLI_RUNS_UNREACHABLE, AppName.TITLE));

        var data = temp.resolve("data");
        log.start(data, Map.of(), false, ZoneId.of("UTC"));
        first.logProblem();
        assertThat(Files.readString(AppLog.file(data))).contains("WARNING").contains("instance.sock")
                .contains("keeps a second copy from running");
    }

    @Test
    void aSecondCopyThatCannotReachTheFirstEndsWithItsOwnExitCode() {
        claim().claim(command -> { });
        err.reset();

        assertThat(claim().claim(command -> { })).contains(ExitCode.UNREACHABLE);
        assertThat(said()).isEqualTo(MESSAGES.text(TextKey.CLI_UNREACHABLE, AppName.TITLE));
    }

    /** A folder for the lock and the socket with a path short enough for a socket, made only for this user. */
    private Path privateFolder() throws IOException {
        folder = temp.resolve("instance");
        FileAttribute<?>[] onlyMine = user.isPresent()
                ? new FileAttribute<?>[] {PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))}
                : new FileAttribute<?>[0];
        return Files.createDirectory(folder, onlyMine);
    }

    @Test
    void aSecondCopyHandsOverToTheRunningOneSaysSoAndSucceeds() throws Exception {
        privateFolder();
        var received = new LinkedBlockingQueue<InstanceCommand>();
        assertThat(claim().claim(received::add)).isEmpty();
        assertThat(said()).isEmpty();

        assertThat(claim().claim(command -> { })).contains(ExitCode.SUCCESS);
        assertThat(received.poll(5, TimeUnit.SECONDS)).isEqualTo(InstanceCommand.SHOW);
        assertThat(said()).isEqualTo(MESSAGES.text(TextKey.CLI_HANDED_OVER, AppName.TITLE));
    }

    @Test
    void aSecondCopyFailsWhenTheCopyThatHoldsTheSocketDoesNotAnswer() throws Exception {
        var made = privateFolder();
        // listens, but never takes the connection, so the hand-over is never confirmed
        try (var mute = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
            mute.bind(UnixDomainSocketAddress.of(made.resolve("instance.sock")));

            assertThat(claim().claim(command -> { })).contains(ExitCode.FAILURE);
            assertThat(said()).isEqualTo(MESSAGES.text(TextKey.CLI_NO_ANSWER, AppName.TITLE));
        }
    }

    @Test
    void noCopyRunsInAFolderThatIsNotPrivateAndTheOwnerIsToldWhy() throws IOException {
        var elsewhere = Files.createDirectory(temp.resolve("elsewhere"));
        folder = temp.resolve("instance");
        try {
            Files.createSymbolicLink(folder, elsewhere);
        } catch (IOException | UnsupportedOperationException e) {
            assumeTrue(false, "this account may not make links: " + e);
        }

        var instance = instance();
        assertThat(claim(instance).claim(command -> { })).contains(ExitCode.FAILURE);
        assertThat(instance.problem()).hasValueSatisfying(problem -> assertThat(problem).contains(folder.toString()));
        assertThat(said()).isEqualTo(MESSAGES.text(TextKey.CLI_INSTANCE_NOT_PRIVATE, AppName.TITLE,
                instance.problem().orElseThrow()));
    }

    @Test
    void withoutAnInstanceLockTheCopyRunsAndLogsThatASecondCopyCouldStart() throws IOException {
        // a folder that cannot be made, as a file is in the way
        folder = Files.createFile(temp.resolve("a-file")).resolve("instance");
        var first = claim();
        assertThat(first.claim(command -> { })).isEmpty();
        assertThat(said()).isEmpty();

        var data = temp.resolve("data");
        log.start(data, Map.of(), false, ZoneId.of("UTC"));
        first.logProblem();
        assertThat(Files.readString(AppLog.file(data))).contains("WARNING")
                .contains("no instance lock, so a second copy of the app could start").contains(folder.toString());
    }
}

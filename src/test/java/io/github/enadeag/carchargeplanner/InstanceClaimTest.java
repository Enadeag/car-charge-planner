package io.github.enadeag.carchargeplanner;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.enadeag.carchargeplanner.desktop.Language;
import io.github.enadeag.carchargeplanner.desktop.Messages;
import io.github.enadeag.carchargeplanner.desktop.TextKey;
import io.github.enadeag.carchargeplanner.instance.SingleInstance;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.UserPrincipal;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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

    private InstanceClaim claim() {
        var instance = new SingleInstance(folder, user, Duration.ofMillis(500));
        toClose.add(instance);
        return new InstanceClaim(instance, MESSAGES, new PrintStream(err, true, StandardCharsets.UTF_8), logger);
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
}

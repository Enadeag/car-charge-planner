package nl.enadeag.carchargeplanner;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * The log as the app sets it up, on loggers of the test's own in place of the JVM's root logger, so the test run's own
 * logging stays as it is.
 */
class AppLogTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Amsterdam");

    @TempDir
    Path folder;

    private Logger root;
    private Logger app;
    private Logger library;
    private AppLog log;

    @BeforeEach
    void loggers() {
        var name = "applog-test-" + UUID.randomUUID();
        root = Logger.getLogger(name);
        root.setUseParentHandlers(false); // the test's output stays clean
        app = Logger.getLogger(name + ".app");
        library = Logger.getLogger(name + ".library");
        log = new AppLog(root, app);
    }

    @AfterEach
    void closeLog() {
        log.close();
    }

    private Path data() {
        return folder.resolve("data");
    }

    private List<String> lines() throws IOException {
        return Files.readAllLines(AppLog.file(data()));
    }

    private static String mode(Path path) throws IOException {
        return PosixFilePermissions.toString(Files.getPosixFilePermissions(path));
    }

    @Test
    void aRecordLandsInTheLogFileOnOneLineWithItsTimeLevelLoggerAndMessage() throws IOException {
        log.start(data(), Map.of(), false, ZONE);

        app.log(Level.WARNING, "the forecast\nprovider failed");

        assertThat(lines()).singleElement().asString()
                .matches("\\d{4}-\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d\\.\\d{3} \\+0[12]:00 WARNING "
                        + "applog-test-[-0-9a-f]+\\.app: the forecast provider failed");
    }

    @Test
    void anExceptionFollowsOnTheLinesAfterItsRecord() throws IOException {
        log.start(data(), Map.of(), false, ZONE);

        app.log(Level.SEVERE, "planning failed", new IllegalStateException("broken"));

        var lines = lines();
        assertThat(lines.getFirst()).endsWith("SEVERE " + app.getName() + ": planning failed");
        assertThat(lines.get(1)).isEqualTo("java.lang.IllegalStateException: broken");
        assertThat(lines.get(2)).startsWith("\tat ");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void onlyTheOwnerCanReadTheLogFileAndItsFolder() throws IOException {
        log.start(data(), Map.of(), false, ZONE);
        app.info("started");

        assertThat(mode(data())).isEqualTo("rwx------");
        assertThat(mode(AppLog.file(data()))).isEqualTo("rw-------");
        try (var files = Files.list(data())) {
            assertThat(files).containsExactly(AppLog.file(data())); // no lock file beside it
        }
    }

    @Test
    void recordsBelowInfoAreLeftOutUnlessTheOwnerAsksForThem() throws IOException {
        log.start(data(), Map.of(), false, ZONE);

        app.fine("a detail");
        app.info("a step");

        assertThat(lines()).singleElement().asString().endsWith("a step");
    }

    @Test
    void theOwnerCanLowerTheLevelOfTheAppsOwnLoggersOnly() throws IOException {
        log.start(data(), Map.of(AppLog.LEVEL_VARIABLE, "debug"), false, ZONE);

        app.fine("the app's detail");
        app.finest("the app's trace");
        library.fine("a library's detail, which may name an address");

        assertThat(lines()).singleElement().asString().endsWith("FINE " + app.getName() + ": the app's detail");
    }

    @Test
    void aLevelThatDoesNotExistIsLoggedAndInfoIsUsed() throws IOException {
        log.start(data(), Map.of(AppLog.LEVEL_VARIABLE, "LOUD"), false, ZONE);

        app.fine("a detail");

        assertThat(lines()).singleElement().asString()
                .contains("WARNING", AppLog.LEVEL_VARIABLE + " is \"LOUD\"", "logging from INFO");
    }

    @Test
    void theHandlersThatWereThereAreReplacedAndClosingRemovesTheLogsOwn() throws IOException {
        var earlier = new ConsoleHandler();
        root.addHandler(earlier);

        log.start(data(), Map.of(), false, ZONE);
        assertThat(root.getHandlers()).hasSize(1).doesNotContain(earlier);
        log.close();
        app.info("after closing");

        assertThat(root.getHandlers()).isEmpty();
        assertThat(lines()).isEmpty();
    }

    @Test
    void inATerminalTheRecordsAreShownThereToo() {
        log.start(data(), Map.of(), true, ZONE);

        assertThat(root.getHandlers()).hasSize(2).hasAtLeastOneElementOfType(ConsoleHandler.class);
    }

    @Test
    void withoutALogFileTheRecordsGoToTheConsoleAndTheProblemIsTold() throws IOException {
        Files.writeString(data(), "a file where the folder should be");

        log.start(data(), Map.of(), false, ZONE);

        assertThat(root.getHandlers()).singleElement().isInstanceOf(ConsoleHandler.class);
    }
}

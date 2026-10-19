package nl.enadeag.carchargeplanner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.ZoneId;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

class LogFileTest {

    @TempDir
    Path folder;

    private static LogFile logFile(Path folder, long limit, int count) throws IOException {
        return new LogFile(folder, limit, count, new LogLine(ZoneId.of("UTC")));
    }

    private Path file(int number) {
        return folder.resolve("car-charge-planner." + number + ".log");
    }

    private static void write(LogFile log, String message) {
        var record = new LogRecord(Level.INFO, message);
        record.setLoggerName("test");
        log.publish(record);
    }

    @Test
    void aFullFileMovesUpAndTheOldestIsDropped() throws IOException {
        var log = logFile(folder, 10, 3);
        try {
            for (var message : new String[] {"first", "second", "third", "fourth"}) {
                write(log, message); // each line is past the limit
            }
        } finally {
            log.close();
        }

        assertThat(file(0)).isEmptyFile();
        assertThat(Files.readString(file(1))).contains("fourth").doesNotContain("third");
        assertThat(Files.readString(file(2))).contains("third");
        assertThat(file(3)).doesNotExist();
    }

    @Test
    void aFileThatIsThereIsAddedTo() throws IOException {
        Files.writeString(file(0), "from the last run\n");
        var log = logFile(folder, 1_000_000, 3);
        try {
            write(log, "this run");
        } finally {
            log.close();
        }

        assertThat(Files.readAllLines(file(0))).hasSize(2).first().isEqualTo("from the last run");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void everyFileMadeByRotatingIsOnlyForTheOwner() throws IOException {
        var log = logFile(folder, 10, 3);
        try {
            write(log, "first");
            write(log, "second");
        } finally {
            log.close();
        }

        for (var number = 0; number < 3; number++) {
            assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(file(number))))
                    .as("file %d", number).isEqualTo("rw-------");
        }
    }

    @Test
    void aFileNeedsALimitAndACount() {
        assertThatThrownBy(() -> logFile(folder, 0, 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> logFile(folder, 10, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}

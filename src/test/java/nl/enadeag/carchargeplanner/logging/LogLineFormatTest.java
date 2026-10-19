package nl.enadeag.carchargeplanner.logging;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.Main;
import java.time.Instant;
import java.time.ZoneId;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.junit.jupiter.api.Test;

/** How a log record is written as a line: the logger part, as the time, level and message are in {@link AppLogTest}. */
class LogLineFormatTest {

    private static final LogLineFormat LINE = new LogLineFormat(ZoneId.of("Europe/Amsterdam"));

    private static String line(String logger) {
        var record = new LogRecord(Level.INFO, "planned");
        record.setInstant(Instant.parse("2026-10-17T12:30:00.123Z"));
        record.setLoggerName(logger);
        return LINE.format(record).strip();
    }

    @Test
    void theAppsOwnLoggersAreNamedWithoutTheAppsPackage() {
        assertThat(line(Main.class.getPackageName() + ".engine.PlanSchedule"))
                .isEqualTo("2026-10-17 14:30:00.123 +02:00 INFO engine.PlanSchedule: planned");
    }

    @Test
    void otherLoggersKeepTheirWholeName() {
        assertThat(line("javafx.scene")).endsWith(" INFO javafx.scene: planned");
        // a package that only starts like the app's is not the app's
        assertThat(line(Main.class.getPackageName() + "extra.Thing"))
                .endsWith(" INFO " + Main.class.getPackageName() + "extra.Thing: planned");
    }

    @Test
    void aRecordWithoutALoggerNameIsTheRootLoggers() {
        assertThat(line(null)).endsWith(" INFO root: planned");
        assertThat(line("")).endsWith(" INFO root: planned");
    }
}

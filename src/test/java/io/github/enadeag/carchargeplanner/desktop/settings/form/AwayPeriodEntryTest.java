package io.github.enadeag.carchargeplanner.desktop.settings.form;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.enadeag.carchargeplanner.desktop.settings.form.AwayPeriodEntry.Checked;
import io.github.enadeag.carchargeplanner.desktop.text.Language;
import io.github.enadeag.carchargeplanner.desktop.text.Messages;
import io.github.enadeag.carchargeplanner.engine.away.RecurringAwayPeriod;
import java.time.DayOfWeek;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class AwayPeriodEntryTest {

    private static final Messages ENGLISH = new Messages(Language.ENGLISH);

    @Test
    void twoTimesWithTheEndAfterTheStartAreAPeriod() {
        assertThat(new AwayPeriodEntry(DayOfWeek.TUESDAY, "08:00", " 17:30").check(ENGLISH))
                .isEqualTo(new Checked.Accepted(new RecurringAwayPeriod(DayOfWeek.TUESDAY, LocalTime.of(8, 0),
                        LocalTime.of(17, 30))));
    }

    @Test
    void aTimeThatIsNotATimeSaysHowToTypeIt() {
        var problem = new Checked.Refused("Type the times as hours and minutes, such as 07:30.");

        assertThat(new AwayPeriodEntry(DayOfWeek.MONDAY, "8 o'clock", "17:00").check(ENGLISH)).isEqualTo(problem);
        assertThat(new AwayPeriodEntry(DayOfWeek.MONDAY, "08:00", "").check(ENGLISH)).isEqualTo(problem);
    }

    @Test
    void anEndNotAfterTheStartIsTheRecordsProblem() {
        assertThat(new AwayPeriodEntry(DayOfWeek.MONDAY, "17:00", "08:00").check(ENGLISH))
                .isEqualTo(new Checked.Refused("Must end after it starts, on the same day."));
    }
}

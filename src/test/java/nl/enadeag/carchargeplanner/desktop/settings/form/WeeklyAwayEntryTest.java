package nl.enadeag.carchargeplanner.desktop.settings.form;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.desktop.settings.form.WeeklyAwayEntry.Checked;
import nl.enadeag.carchargeplanner.desktop.text.Language;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.engine.away.WeeklyAwayPeriod;
import java.time.DayOfWeek;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class WeeklyAwayEntryTest {

    private static final Messages ENGLISH = new Messages(Language.ENGLISH);

    @Test
    void twoTimesWithTheEndAfterTheStartAreAPeriod() {
        assertThat(new WeeklyAwayEntry(DayOfWeek.TUESDAY, "08:00", " 17:30").check(ENGLISH))
                .isEqualTo(new Checked.Accepted(new WeeklyAwayPeriod(DayOfWeek.TUESDAY, LocalTime.of(8, 0),
                        LocalTime.of(17, 30))));
    }

    @Test
    void aTimeThatIsNotATimeSaysHowToTypeIt() {
        var problem = new Checked.Refused("Type the times as hours and minutes, such as 07:30.");

        assertThat(new WeeklyAwayEntry(DayOfWeek.MONDAY, "8 o'clock", "17:00").check(ENGLISH)).isEqualTo(problem);
        assertThat(new WeeklyAwayEntry(DayOfWeek.MONDAY, "08:00", "").check(ENGLISH)).isEqualTo(problem);
    }

    @Test
    void anEndNotAfterTheStartIsTheRecordsProblem() {
        assertThat(new WeeklyAwayEntry(DayOfWeek.MONDAY, "17:00", "08:00").check(ENGLISH))
                .isEqualTo(new Checked.Refused("Must end after it starts, on the same day."));
    }
}

package nl.enadeag.carchargeplanner.desktop.dashboard.away;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.desktop.text.Language;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AwayMapperTest {

    private static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");
    /** A Friday. */
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 3);

    private static OneOffAwayPeriod part(LocalDate day, int startHour, int endHour) {
        return OneOffAwayPeriod.on(day, LocalTime.of(startHour, 0), LocalTime.of(endHour, 30), BRUSSELS);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "ENGLISH | Today, 10:00 – 12:30 | Tomorrow, all day | Wed 8 Jul, 09:00 – 17:30 | Thu 9 Jul 18:00 – Sat 11 Jul 12:00",
        "DUTCH | Vandaag, 10:00 – 12:30 | Morgen, hele dag | Wo 8 jul, 09:00 – 17:30 | Do 9 jul 18:00 – Za 11 jul 12:00",
    })
    void theComingPeriodsAreToldByDayAndTimeAndThoseThatEndedAreLeftOut(Language language, String today,
            String tomorrow, String nextWeek, String overDays) {
        var now = TODAY.atTime(11, 0).atZone(BRUSSELS).toInstant();
        var ended = part(TODAY, 6, 8);
        var overSeveralDays = new OneOffAwayPeriod(TODAY.plusDays(6).atTime(18, 0).atZone(BRUSSELS).toInstant(),
                TODAY.plusDays(8).atTime(12, 0).atZone(BRUSSELS).toInstant());
        var periods = List.of(ended, part(TODAY, 10, 12), OneOffAwayPeriod.allDay(TODAY.plusDays(1), BRUSSELS),
                part(TODAY.plusDays(5), 9, 17), overSeveralDays);

        var model = new AwayMapper(BRUSSELS, now, new Messages(language)).map(periods);

        assertThat(model.coming()).extracting(AwayModel.Listed::text).containsExactly(today, tomorrow, nextWeek, overDays);
        assertThat(model.coming()).extracting(AwayModel.Listed::period).doesNotContain(ended);
        assertThat(model.coming().get(1).removeSpoken())
                .isEqualTo(new Messages(language).text(TextKey.ONE_OFF_REMOVE_SPOKEN, tomorrow));
    }
}

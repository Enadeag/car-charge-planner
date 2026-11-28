package nl.enadeag.carchargeplanner.desktop.dashboard.battery;

import static nl.enadeag.carchargeplanner.desktop.dashboard.PlannedDays.TWO_DAYS;
import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.desktop.text.Language;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.engine.Outlook;
import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.dashboard.BatteryLevels;
import nl.enadeag.carchargeplanner.engine.dashboard.ChargeLevels;
import nl.enadeag.carchargeplanner.engine.dashboard.DashboardDay;
import nl.enadeag.carchargeplanner.engine.dashboard.DayChart;
import nl.enadeag.carchargeplanner.engine.dashboard.DayName;
import nl.enadeag.carchargeplanner.engine.dashboard.NoWindowReason;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class BatteryMapperTest {

    private static final Messages ENGLISH = new Messages(Language.ENGLISH);
    private static final LocalDate TOMORROW = LocalDate.of(2026, 7, 4);
    private static final DayChart CHART = new DayChart(6, 20, 1, List.of(), 0, List.of(), Optional.empty(), List.of(),
            Optional.empty());

    private static BatteryModel map(Outlook.Planned planned, int day, Messages messages) {
        var dashboard = planned.dashboard(List.of(), List.of());
        return new BatteryMapper(messages).map(dashboard.days().get(day), dashboard.levels());
    }

    @Test
    void todaysBatteryIsNowAndCanBeEntered() {
        var battery = map(TWO_DAYS, 0, ENGLISH);

        assertThat(battery).isEqualTo(new BatteryModel(50, 74, 30, 80, "50 → 74%", "min 30%", "target 80%", true));
    }

    @Test
    void anotherDaysBatteryIsAtItsStartAndCannotBeEntered() {
        var battery = map(TWO_DAYS, 1, ENGLISH);

        assertThat(battery.editable()).isFalse();
        assertThat(battery.levelPct()).isEqualTo(62);
    }

    @Test
    void inDutchTheWordsAreDutch() {
        var battery = map(TWO_DAYS, 1, new Messages(Language.DUTCH));

        assertThat(battery.minimumText()).isEqualTo("min. 30%");
    }

    /** Every half from 0 to 100 and the closest numbers on either side of it, with what a language writes for each. */
    private static Map<Double, String> written(Messages messages) {
        var written = new LinkedHashMap<Double, String>();
        for (int half = 0; half <= 200; half++) {
            double value = half / 2.0;
            for (double near : new double[] {Math.nextDown(value), value, Math.nextUp(value), value + 0.25}) {
                if (near >= 0 && near <= 100) {
                    written.put(near, messages.whole(near));
                }
            }
        }
        return written;
    }

    private static BatteryLevels departing(double start, double departure, double after) {
        return new BatteryLevels(new Percent(start), new Percent(after), List.of(new Percent(departure)),
                Optional.of(new Percent(0)));
    }

    @ParameterizedTest
    @EnumSource(Language.class)
    void theEngineSaysTheBatteryChargesBeforeTheTripExactlyWhenTheWrittenStartAndDepartureDiffer(Language language) {
        var written = written(new Messages(language));
        var disagree = new ArrayList<String>();

        written.forEach((start, startText) -> written.forEach((departure, departureText) -> {
            if (departing(start, departure, 100).chargesBeforeTrip() == startText.equals(departureText)) {
                disagree.add(start + " and " + departure);
            }
        }));

        assertThat(written).containsEntry(49.5, "50").containsEntry(Math.nextDown(49.5), "49");
        assertThat(disagree).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(Language.class)
    void theEngineSaysTheBatteryChargesAfterTheReturnExactlyWhenTheWrittenDepartureAndHighestLevelDiffer(
            Language language) {
        var written = written(new Messages(language));
        var disagree = new ArrayList<String>();

        written.forEach((departure, departureText) -> written.forEach((after, afterText) -> {
            if (after >= departure
                    && departing(0, departure, after).chargesAfterReturn() == departureText.equals(afterText)) {
                disagree.add(departure + " and " + after);
            }
        }));

        assertThat(disagree).isEmpty();
    }

    /** A departure day's levels in words, as {@code BatteryMapper} gives the bar. */
    private static String levels(Messages messages, double start, double departure, double after, double end) {
        var levels = new BatteryLevels(new Percent(start), new Percent(after), List.of(new Percent(departure)),
                Optional.of(new Percent(end)));
        var day = new DashboardDay(new DayName.Tomorrow(TOMORROW), Optional.empty(), List.of(),
                Optional.of(NoWindowReason.DEPARTURE), levels, Optional.empty(), CHART);
        return new BatteryMapper(messages).map(day, new ChargeLevels(new Percent(30), new Percent(80))).levels();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            50 | 50 | 50 | 31 | 50 → 31% after the trip       | 50 → 31% na de rit
            50 | 80 | 80 | 61 | 50 → 80%, 61% after the trip  | 50 → 80%, 61% na de rit
            50 | 50 | 80 | 71 | 50%, 80% after the return     | 50%, 80% na terugkomst
            50 | 60 | 80 | 71 | 50 → 60%, 80% after the return | 50 → 60%, 80% na terugkomst
            """)
    void aDepartureDaysLevelsSayWhetherTheDayChargesBeforeTheTripAfterTheReturnOrBoth(double start,
            double departure, double after, double end, String english, String dutch) {
        assertThat(levels(ENGLISH, start, departure, after, end)).isEqualTo(english);
        assertThat(levels(new Messages(Language.DUTCH), start, departure, after, end)).isEqualTo(dutch);
    }
}

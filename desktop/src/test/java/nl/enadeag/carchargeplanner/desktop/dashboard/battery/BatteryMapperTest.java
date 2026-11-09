package nl.enadeag.carchargeplanner.desktop.dashboard.battery;

import static nl.enadeag.carchargeplanner.desktop.dashboard.PlannedDays.TODAY;
import static nl.enadeag.carchargeplanner.desktop.dashboard.PlannedDays.TWO_DAYS;
import static nl.enadeag.carchargeplanner.desktop.dashboard.PlannedDays.at;
import static nl.enadeag.carchargeplanner.desktop.dashboard.PlannedDays.day;
import static nl.enadeag.carchargeplanner.desktop.dashboard.PlannedDays.planned;
import static nl.enadeag.carchargeplanner.desktop.dashboard.PlannedDays.sunny;
import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.desktop.text.Language;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.engine.Outlook;
import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.dashboard.BatteryLevels;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class BatteryMapperTest {

    private static final Messages ENGLISH = new Messages(Language.ENGLISH);

    private static BatteryModel map(Outlook.Planned planned, int day, Messages messages) {
        var dashboard = planned.dashboard(List.of(), List.of());
        return new BatteryMapper(messages).map(dashboard.days().get(day), dashboard.levels());
    }

    @Test
    void todaysBatteryIsNowAndCanBeEntered() {
        var battery = map(TWO_DAYS, 0, ENGLISH);

        assertThat(battery).isEqualTo(new BatteryModel(50, 74, 30, 80, "50 → 74%", "min 30%", "target 80%", true,
                "Car battery 50% now, 74% after charging; minimum 30%, target 80%. "
                        + "The level is assumed: enter the level the car shows."));
    }

    @Test
    void anotherDaysBatteryIsAtItsStartAndCannotBeEntered() {
        var battery = map(TWO_DAYS, 1, ENGLISH);

        assertThat(battery.editable()).isFalse();
        assertThat(battery.levelPct()).isEqualTo(62);
        assertThat(battery.spoken()).isEqualTo(
                "Car battery 62% at the start of tomorrow, 62% after charging; minimum 30%, target 80%.");
    }

    @Test
    void anEnteredLevelIsNotCalledAssumed() {
        var entered = planned(at(TODAY, 8, 12), false, day(TODAY, 50, 74, sunny(TODAY, 4.2)));

        assertThat(map(entered, 0, ENGLISH).spoken())
                .isEqualTo("Car battery 50% now, 74% after charging; minimum 30%, target 80%.");
    }

    @Test
    void inDutchTheWordsAreDutch() {
        var battery = map(TWO_DAYS, 1, new Messages(Language.DUTCH));

        assertThat(battery.minimumText()).isEqualTo("min. 30%");
        assertThat(battery.spoken()).isEqualTo(
                "Accu van de auto 62% aan het begin van morgen, na het laden 62%; minimum 30%, doel 80%.");
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

    @ParameterizedTest
    @EnumSource(Language.class)
    void theEngineSaysTheBatteryChargesBeforeTheTripExactlyWhenTheWrittenLevelsDiffer(Language language) {
        var written = written(new Messages(language));
        var disagree = new ArrayList<String>();

        written.forEach((start, startText) -> written.forEach((after, afterText) -> {
            if (BatteryLevels.of(new Percent(start), new Percent(after)).chargesBeforeTrip() == startText.equals(afterText)) {
                disagree.add(start + " and " + after);
            }
        }));

        assertThat(written).containsEntry(49.5, "50").containsEntry(Math.nextDown(49.5), "49");
        assertThat(disagree).isEmpty();
    }
}

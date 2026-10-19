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
import org.junit.jupiter.api.Test;

class BatteryMapperTest {

    private static final Messages ENGLISH = new Messages(Language.ENGLISH);

    @Test
    void todaysBatteryIsNowAndCanBeEntered() {
        var battery = new BatteryMapper(TWO_DAYS, ENGLISH).map(TWO_DAYS.days().getFirst());

        assertThat(battery).isEqualTo(new BatteryModel(50, 74, 30, 80, "50 → 74%", "min 30%", "target 80%", true,
                "Car battery 50% now, 74% after charging; minimum 30%, target 80%. "
                        + "The level is assumed: enter the level the car shows."));
    }

    @Test
    void anotherDaysBatteryIsAtItsStartAndCannotBeEntered() {
        var battery = new BatteryMapper(TWO_DAYS, ENGLISH).map(TWO_DAYS.days().get(1));

        assertThat(battery.editable()).isFalse();
        assertThat(battery.levelPct()).isEqualTo(62);
        assertThat(battery.spoken()).isEqualTo(
                "Car battery 62% at the start of tomorrow, 62% after charging; minimum 30%, target 80%.");
    }

    @Test
    void anEnteredLevelIsNotCalledAssumed() {
        var entered = planned(at(TODAY, 8, 12), false, day(TODAY, 50, 74, sunny(TODAY, 4.2)));

        assertThat(new BatteryMapper(entered, ENGLISH).map(entered.days().getFirst()).spoken()).doesNotContain("assumed");
    }

    @Test
    void inDutchTheWordsAreDutch() {
        var battery = new BatteryMapper(TWO_DAYS, new Messages(Language.DUTCH)).map(TWO_DAYS.days().get(1));

        assertThat(battery.minimumText()).isEqualTo("min. 30%");
        assertThat(battery.spoken()).isEqualTo(
                "Accu van de auto 62% aan het begin van morgen, na het laden 62%; minimum 30%, doel 80%.");
    }
}

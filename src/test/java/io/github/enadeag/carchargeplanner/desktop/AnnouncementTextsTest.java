package io.github.enadeag.carchargeplanner.desktop;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.enadeag.carchargeplanner.engine.config.Percent;
import io.github.enadeag.carchargeplanner.engine.config.Phases;
import io.github.enadeag.carchargeplanner.engine.notify.Announcement;
import io.github.enadeag.carchargeplanner.engine.notify.AnnouncementKind;
import io.github.enadeag.carchargeplanner.engine.plan.Window;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/** What a notification says; the Python version's test_messages_say_what_to_set. */
class AnnouncementTextsTest {

    private static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");
    private static final LocalDate DAY = LocalDate.of(2026, 7, 3);

    private static Window window(int startHour, int endHour, int amps, double powerKw, double solarKwh, double importKwh) {
        return new Window(DAY.atTime(startHour, 0).atZone(BRUSSELS).toInstant(),
                DAY.atTime(endHour, 0).atZone(BRUSSELS).toInstant(), amps, powerKw, solarKwh, importKwh);
    }

    private static NotificationText text(AnnouncementKind kind, Window window, Phases phases) {
        return new AnnouncementTexts(new Messages(Language.ENGLISH)).notification(new Announcement(kind, window, BRUSSELS, phases, new Percent(30)));
    }

    @Test
    void aChargeWindowSaysWhenWhatToSetAndHowMuchIsSolar() {
        var text = text(AnnouncementKind.CHARGE_WINDOW, window(10, 15, 7, 1.6, 7.5, 0.1), Phases.ONE);

        assertThat(text.title()).isEqualTo("Charge window 10:00 – 15:00");
        assertThat(text.body()).isEqualTo("Set the current to 7 A (1.6 kW) in your charger's app. About 7.6 kWh, 99 % solar.");
    }

    @Test
    void inDutchTheNotificationSaysTheSameWithADecimalComma() {
        var announcement = new Announcement(AnnouncementKind.CHARGE_WINDOW, window(10, 15, 7, 1.6, 7.5, 0.1), BRUSSELS,
                Phases.ONE, new Percent(30));

        var text = new AnnouncementTexts(new Messages(Language.DUTCH)).notification(announcement);

        assertThat(text.title()).isEqualTo("Laadvenster 10:00 – 15:00");
        assertThat(text.body()).isEqualTo("Stel in de app van je laadpaal de stroom in op 7 A (1,6 kW). Ongeveer 7,6 kWh, 99% zon.");
    }

    @Test
    void onThreePhasesTheCurrentIsPerPhase() {
        var text = text(AnnouncementKind.CHARGE_WINDOW, window(10, 15, 7, 4.8, 20, 0), Phases.THREE);

        assertThat(text.body()).startsWith("Set the current to 7 A per phase (4.8 kW) in your charger's app.");
    }

    @Test
    void aGridChargeSaysItKeepsTheBatteryAboveTheMinimum() {
        var text = text(AnnouncementKind.GRID_CHARGE, window(0, 6, 13, 3.0, 0, 17.9), Phases.ONE);

        assertThat(text.title()).isEqualTo("Grid charge window 00:00 – 06:00");
        assertThat(text.body()).isEqualTo(
                "Set the current to 13 A (3.0 kW) in your charger's app. About 17.9 kWh, needed to stay above 30 %.");
    }
}

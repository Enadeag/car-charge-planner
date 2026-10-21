package nl.enadeag.carchargeplanner.desktop.text;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.desktop.system.NotificationText;
import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.config.Phases;
import nl.enadeag.carchargeplanner.engine.notify.Announcement;
import nl.enadeag.carchargeplanner.engine.notify.AnnouncementKind;
import nl.enadeag.carchargeplanner.engine.plan.Window;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** What a notification says: what to set on the charger, and when. */
class AnnouncementTextsTest {

    private static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");
    private static final LocalDate DAY = LocalDate.of(2026, 7, 3);

    private static Window window(int startHour, int endHour, int amps, double powerKw, double solarKwh, double importKwh) {
        return new Window(DAY.atTime(startHour, 0).atZone(BRUSSELS).toInstant(),
                DAY.atTime(endHour, 0).atZone(BRUSSELS).toInstant(), amps, powerKw, solarKwh, importKwh);
    }

    private static NotificationText text(AnnouncementKind kind, Window window, Phases phases) {
        var limit = kind == AnnouncementKind.GRID_CHARGE ? Optional.of(new Percent(65)) : Optional.<Percent>empty();
        return new AnnouncementTexts(new LanguageChoice(Language.ENGLISH))
                .notification(new Announcement(kind, window, BRUSSELS, phases, new Percent(30), limit));
    }

    @Test
    void aChargeWindowSaysWhenWhatToSetAndHowMuchIsSolar() {
        var text = text(AnnouncementKind.SOLAR_WINDOW, window(10, 15, 7, 1.6, 7.5, 0.1), Phases.ONE);

        assertThat(text.title()).isEqualTo("Charge window 10:00 – 15:00");
        assertThat(text.body())
                .isEqualTo("Set the charge current to 7 A (1.6 kW) in your charger's app. About 7.6 kWh, 99% solar.");
    }

    @Test
    void inDutchTheNotificationSaysTheSameWithADecimalComma() {
        var announcement = new Announcement(AnnouncementKind.SOLAR_WINDOW, window(10, 15, 7, 1.6, 7.5, 0.1), BRUSSELS,
                Phases.ONE, new Percent(30), Optional.empty());

        var text = new AnnouncementTexts(new LanguageChoice(Language.DUTCH)).notification(announcement);

        assertThat(text.title()).isEqualTo("Laadvenster 10:00 – 15:00");
        assertThat(text.body()).isEqualTo(
                "Stel in de app van je laadpaal de laadstroom in op 7 A (1,6 kW). Ongeveer 7,6 kWh, 99% zonnestroom.");
    }

    @Test
    void theNextNotificationSpeaksTheLanguageChosenSince() {
        var language = new LanguageChoice(Language.ENGLISH);
        var texts = new AnnouncementTexts(language);
        var announcement = new Announcement(AnnouncementKind.SOLAR_WINDOW, window(10, 15, 7, 1.6, 7.5, 0.1), BRUSSELS,
                Phases.ONE, new Percent(30), Optional.empty());
        assertThat(texts.notification(announcement).title()).isEqualTo("Charge window 10:00 – 15:00");

        language.choose(Language.DUTCH);

        assertThat(texts.notification(announcement).title()).isEqualTo("Laadvenster 10:00 – 15:00");
    }

    @Test
    void onThreePhasesTheCurrentIsPerPhase() {
        var text = text(AnnouncementKind.SOLAR_WINDOW, window(10, 15, 7, 4.8, 20, 0), Phases.THREE);

        assertThat(text.body()).startsWith("Set the charge current to 7 A per phase (4.8 kW) in your charger's app.");
    }

    @Test
    void aGridChargeSaysItKeepsTheBatteryAboveTheMinimumAndTheChargeLimitToSet() {
        var text = text(AnnouncementKind.GRID_CHARGE, window(21, 23, 13, 3.0, 0, 6.0), Phases.ONE);

        assertThat(text.title()).isEqualTo("Grid charge window 21:00 – 23:00");
        assertThat(text.body()).isEqualTo(
                "Set the charge current to 13 A (3.0 kW) in your charger's app. About 6.0 kWh, needed to stay above "
                        + "30%. Set the charge limit in the car or the charger to 65%.");
    }

    @Test
    void inDutchAGridChargeNamesTheLaadlimiet() {
        var announcement = new Announcement(AnnouncementKind.GRID_CHARGE, window(21, 23, 13, 3.0, 0, 6.0), BRUSSELS,
                Phases.ONE, new Percent(30), Optional.of(new Percent(65)));

        var text = new AnnouncementTexts(new LanguageChoice(Language.DUTCH)).notification(announcement);

        assertThat(text.body()).isEqualTo("Stel in de app van je laadpaal de laadstroom in op 13 A (3,0 kW). Ongeveer "
                + "6,0 kWh, nodig om boven 30% te blijven. Stel de laadlimiet in de auto of de laadpaal in op 65%.");
    }

    @Test
    void theTestNotificationSaysItIsATestInTheLanguageSpokenNow() {
        var language = new LanguageChoice(Language.ENGLISH);
        var texts = new AnnouncementTexts(language);
        assertThat(texts.testNotification("Car charge planner")).isEqualTo(new NotificationText("Test notification",
                "This is how Car charge planner tells you a charge window starts soon. Click it to open the dashboard."));

        language.choose(Language.DUTCH);

        assertThat(texts.testNotification("Car charge planner")).isEqualTo(new NotificationText("Testmelding",
                "Zo vertelt Car charge planner je dat een laadvenster bijna begint. Klik erop om het dashboard te openen."));
    }
}

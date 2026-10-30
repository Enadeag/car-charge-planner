package nl.enadeag.carchargeplanner.desktop.notify;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.desktop.app.Units;
import nl.enadeag.carchargeplanner.desktop.system.NotificationTarget;
import nl.enadeag.carchargeplanner.desktop.system.NotificationText;
import nl.enadeag.carchargeplanner.desktop.text.Language;
import nl.enadeag.carchargeplanner.desktop.text.LanguageChoice;
import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.config.Phases;
import nl.enadeag.carchargeplanner.engine.notify.Announcement;
import nl.enadeag.carchargeplanner.engine.notify.AnnouncementKind;
import nl.enadeag.carchargeplanner.engine.notify.BatteryCheck;
import nl.enadeag.carchargeplanner.engine.notify.Notice;
import nl.enadeag.carchargeplanner.engine.notify.TripChargeStart;
import nl.enadeag.carchargeplanner.engine.notify.TripShort;
import nl.enadeag.carchargeplanner.engine.plan.TripGridCharge;
import nl.enadeag.carchargeplanner.engine.plan.Window;
import nl.enadeag.carchargeplanner.engine.trip.Trip;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** What a notification says: what to set on the charger, and when; and what a trip asks of the owner. */
class AnnouncementTextsTest {

    private static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");
    private static final LocalDate DAY = LocalDate.of(2026, 7, 3);

    private static Window window(int startHour, int endHour, int amps, double powerKw, double solarKwh, double importKwh) {
        return new Window(DAY.atTime(startHour, 0).atZone(BRUSSELS).toInstant(),
                DAY.atTime(endHour, 0).atZone(BRUSSELS).toInstant(), amps, powerKw, solarKwh, importKwh);
    }

    /** Sunday 5 July 2026 at 08:30 in Brussels, there and back by 18:00. */
    private static final Trip TRIP = new Trip(DAY.plusDays(2).atTime(8, 30).atZone(BRUSSELS).toInstant(),
            DAY.plusDays(2).atTime(18, 0).atZone(BRUSSELS).toInstant(), 420, false);

    private final AtomicReference<Units> units = new AtomicReference<>(Units.METRIC);

    private AnnouncementTexts english() {
        return new AnnouncementTexts(new LanguageChoice(Language.ENGLISH), units::get);
    }

    private AnnouncementTexts dutch() {
        return new AnnouncementTexts(new LanguageChoice(Language.DUTCH), units::get);
    }

    private NotificationText text(AnnouncementKind kind, Window window, Phases phases) {
        var limit = kind == AnnouncementKind.GRID_CHARGE ? Optional.of(new Percent(65)) : Optional.<Percent>empty();
        return english()
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

        var text = dutch().notification(announcement);

        assertThat(text.title()).isEqualTo("Laadvenster 10:00 – 15:00");
        assertThat(text.body()).isEqualTo(
                "Stel in de app van je laadpaal de laadstroom in op 7 A (1,6 kW). Ongeveer 7,6 kWh, 99% zonnestroom.");
    }

    @Test
    void theNextNotificationSpeaksTheLanguageChosenSince() {
        var language = new LanguageChoice(Language.ENGLISH);
        var texts = new AnnouncementTexts(language, () -> Units.METRIC);
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

        var text = dutch().notification(announcement);

        assertThat(text.body()).isEqualTo("Stel in de app van je laadpaal de laadstroom in op 13 A (3,0 kW). Ongeveer "
                + "6,0 kWh, nodig om boven 30% te blijven. Stel de laadlimiet in de auto of de laadpaal in op 65%.");
    }

    @Test
    void theTestNotificationSaysItIsATestInTheLanguageSpokenNow() {
        var language = new LanguageChoice(Language.ENGLISH);
        var texts = new AnnouncementTexts(language, () -> Units.METRIC);
        assertThat(texts.testNotification("Car charge planner")).isEqualTo(new NotificationText("Test notification",
                "This is how Car charge planner tells you a charge window starts soon. Click it to open the dashboard."));

        language.choose(Language.DUTCH);

        assertThat(texts.testNotification("Car charge planner")).isEqualTo(new NotificationText("Testmelding",
                "Zo vertelt Car charge planner je dat een laadvenster bijna begint. Klik erop om het dashboard te openen."));
    }

    private static TripChargeStart chargeStart(TripGridCharge.Start start) {
        var window = new Window(DAY.plusDays(1).atTime(21, 0).atZone(BRUSSELS).toInstant(),
                DAY.plusDays(1).atTime(23, 30).atZone(BRUSSELS).toInstant(), 16, 3.7, 0, 9.2);
        return new TripChargeStart(TRIP, window, start, BRUSSELS, Phases.ONE, new Percent(94));
    }

    @Test
    void aTripsGridChargeAsPlannedSaysWhenToStartForWhichTripAndWhatToSet() {
        var text = english().notification(chargeStart(TripGridCharge.Start.PLANNED));

        assertThat(text).isEqualTo(new NotificationText("Grid charge window 21:00 – 23:30",
                "Start charging at 21:00 for your trip on Sunday at 08:30, and set the charge limit to 94%. "
                        + "Set the charge current to 16 A (3.7 kW) in your charger's app."));
    }

    @Test
    void aTripsGridChargeThatHasToStartNowSaysSoAndWhy() {
        var text = english().notification(chargeStart(TripGridCharge.Start.NOW));

        assertThat(text).isEqualTo(new NotificationText("Start charging now",
                "Start charging now for your trip on Sunday at 08:30, and set the charge limit to 94%: little time is "
                        + "left before the departure. Set the charge current to 16 A (3.7 kW) in your charger's app."));
    }

    @Test
    void aTripsGridChargeOutsideTheChargingHoursSaysWhyItCannotWait() {
        var text = english().notification(chargeStart(TripGridCharge.Start.NOW_OUTSIDE_CHARGING_HOURS));

        assertThat(text.body()).isEqualTo("Start charging now for your trip on Sunday at 08:30, and set the charge "
                + "limit to 94%, although that is outside your charging hours: waiting for them would leave too little "
                + "time. Set the charge current to 16 A (3.7 kW) in your charger's app.");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "PLANNED | Laden uit het net 21:00 – 23:30 | Start het laden om 21:00 voor je rit van zondag 08:30, en zet de "
                + "laadlimiet op 94%. Stel in de app van je laadpaal de laadstroom in op 16 A (3,7 kW).",
        "NOW | Start nu met laden | Start nu met laden voor je rit van zondag 08:30, en zet de laadlimiet op 94%: "
                + "er is weinig tijd tot het vertrek. Stel in de app van je laadpaal de laadstroom in op 16 A "
                + "(3,7 kW).",
        "NOW_OUTSIDE_CHARGING_HOURS | Start nu met laden | Start nu met laden voor je rit van zondag 08:30, en zet de "
                + "laadlimiet op 94%, ook al is dat buiten je laaduren: wachten laat te weinig tijd over. Stel in de "
                + "app van je laadpaal de laadstroom in op 16 A (3,7 kW)."})
    void inDutchATripsGridChargeNamesTheRitAndTheLaadlimiet(TripGridCharge.Start start, String title, String body) {
        assertThat(dutch().notification(chargeStart(start))).isEqualTo(new NotificationText(title, body));
    }

    @Test
    void aTripsGridChargeOnThreePhasesGivesTheCurrentPerPhase() {
        var start = chargeStart(TripGridCharge.Start.PLANNED);
        var text = english().notification(new TripChargeStart(TRIP, start.window(), start.start(), BRUSSELS,
                Phases.THREE, new Percent(94)));

        assertThat(text.body()).endsWith("Set the charge current to 16 A per phase (3.7 kW) in your charger's app.");
    }

    @Test
    void theBatteryCheckAsksAboutTheLevelThePlanAssumesAndOpensItsEntry() {
        Notice check = new BatteryCheck(TRIP, new Percent(50), BRUSSELS);

        assertThat(english().notification(check)).isEqualTo(new NotificationText("Check the battery level",
                "Your trip on Sunday at 08:30 needs charging. Is the battery at about 50%? Click to enter the level "
                        + "the car shows.", NotificationTarget.BATTERY_LEVEL));
        assertThat(dutch().notification(check)).isEqualTo(new NotificationText("Controleer het accuniveau",
                "Je rit van zondag 08:30 vraagt laden. Staat de accu op ongeveer 50%? Klik om het accuniveau in te "
                        + "vullen dat de auto aangeeft.", NotificationTarget.BATTERY_LEVEL));
    }

    @Test
    void aTripThatIsShortSaysHowFarRoundedUpAndToPlanAChargingStop() {
        Notice tripShort = new TripShort(TRIP, 40.2, BRUSSELS);

        assertThat(english().notification(tripShort)).isEqualTo(new NotificationText("Plan a charging stop",
                "For your trip on Sunday at 08:30 you are 41 km short: plan a charging stop."));
        assertThat(dutch().notification(tripShort)).isEqualTo(new NotificationText("Plan een laadstop",
                "Voor je rit van zondag 08:30 kom je 41 km tekort: plan een laadstop."));
    }

    @Test
    void theDistanceShortIsInTheUnitsChosenNow() {
        units.set(Units.IMPERIAL);

        assertThat(english().notification(new TripShort(TRIP, 40.2, BRUSSELS)).body())
                .isEqualTo("For your trip on Sunday at 08:30 you are 25 mi short: plan a charging stop.");
    }

    @Test
    void onlyTheBatteryCheckOpensTheBatteryLevel() {
        assertThat(english().notification(chargeStart(TripGridCharge.Start.NOW)).opens())
                .isEqualTo(NotificationTarget.DASHBOARD);
        assertThat(text(AnnouncementKind.GRID_CHARGE, window(21, 23, 13, 3.0, 0, 6.0), Phases.ONE).opens())
                .isEqualTo(NotificationTarget.DASHBOARD);
    }
}

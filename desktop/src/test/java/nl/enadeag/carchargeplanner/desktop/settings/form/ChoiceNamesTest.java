package nl.enadeag.carchargeplanner.desktop.settings.form;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.desktop.app.Appearance;
import nl.enadeag.carchargeplanner.desktop.app.DisplayMode;
import nl.enadeag.carchargeplanner.desktop.app.Units;
import nl.enadeag.carchargeplanner.desktop.text.Language;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.engine.config.CompassPoint;
import nl.enadeag.carchargeplanner.engine.config.NotifyLeadChoices;
import nl.enadeag.carchargeplanner.engine.config.Phases;
import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import nl.enadeag.carchargeplanner.engine.config.TiltChoice;
import nl.enadeag.carchargeplanner.engine.forecast.ProviderInfo;
import java.time.DayOfWeek;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChoiceNamesTest {

    private static final ProviderId SUNNY = new ProviderId("sunny");
    private static final List<ProviderInfo> KNOWN = List.of(new ProviderInfo(SUNNY, "Sunny", "", "", "", "",
            Duration.ofHours(1), Duration.ofDays(2), Duration.ofHours(6), List.of()));

    private final ChoiceNames english = new ChoiceNames(new Messages(Language.ENGLISH));
    private final ChoiceNames dutch = new ChoiceNames(new Messages(Language.DUTCH));

    @Test
    void eachAppSettingIsNamedInTheOwnersLanguage() {
        assertThat(List.of(DisplayMode.values()).stream().map(english::mode))
                .containsExactly("Automatic", "Tray icon", "Window");
        assertThat(dutch.mode(DisplayMode.TRAY)).isEqualTo("Pictogram in het systeemvak");
        assertThat(List.of(Appearance.values()).stream().map(english::appearance))
                .containsExactly("Follow the system", "Light", "Dark");
        assertThat(List.of(Units.values()).stream().map(english::units))
                .containsExactly("Metric (km, kWh/100 km)", "Imperial (miles, mi/kWh)");
    }

    @Test
    void thePanelsDirectionsRunClockwiseFromNorthAndTheTiltsFromFlat() {
        assertThat(List.of(CompassPoint.values()).stream().map(english::direction)).containsExactly("North",
                "North-east", "East", "South-east", "South", "South-west", "West", "North-west");
        assertThat(List.of(CompassPoint.values()).stream().map(dutch::direction)).containsExactly("Noord",
                "Noordoost", "Oost", "Zuidoost", "Zuid", "Zuidwest", "West", "Noordwest");
        assertThat(List.of(TiltChoice.values()).stream().map(english::tilt))
                .containsExactly("Flat", "Pitched", "Steep", "Upright");
        assertThat(List.of(TiltChoice.values()).stream().map(dutch::tilt))
                .containsExactly("Plat", "Schuin", "Steil", "Rechtop");
    }

    @Test
    void theNotificationLeadsAreNamedFromTheStartOnAndAnyOtherByItsMinutes() {
        assertThat(NotifyLeadChoices.STANDARD.stream().map(english::notifyLead)).containsExactly("At the start",
                "5 minutes ahead", "10 minutes ahead", "15 minutes ahead", "30 minutes ahead", "60 minutes ahead");
        assertThat(NotifyLeadChoices.STANDARD.stream().map(dutch::notifyLead)).containsExactly("Bij het begin",
                "5 minuten ervoor", "10 minuten ervoor", "15 minuten ervoor", "30 minuten ervoor", "60 minuten ervoor");
        assertThat(english.notifyLead(Duration.ofMinutes(20))).isEqualTo("20 minutes ahead");
        assertThat(english.notifyLead(Duration.ofMinutes(1))).isEqualTo("1 minute ahead");
        assertThat(dutch.notifyLead(Duration.ofMinutes(1))).isEqualTo("1 minuut ervoor");
        assertThat(english.notifyLead(Duration.ofMinutes(1440))).isEqualTo("1440 minutes ahead");
        assertThat(dutch.notifyLead(Duration.ofMinutes(1440))).isEqualTo("1440 minuten ervoor");
    }

    @Test
    void aLanguageIsNamedInItselfWhateverTheAppSpeaks() {
        assertThat(english.language(Language.DUTCH)).isEqualTo("Nederlands");
        assertThat(dutch.language(Language.DUTCH)).isEqualTo("Nederlands");
        assertThat(english.systemLanguage()).isEqualTo("Follow the system");
    }

    @Test
    void theChargersPhasesAreNamed() {
        assertThat(english.phases(Phases.ONE)).isEqualTo("One phase");
        assertThat(english.phases(Phases.THREE)).isEqualTo("Three phases");
        assertThat(dutch.phases(Phases.THREE)).isEqualTo("Drie fasen");
    }

    @Test
    void aWeekdayIsWrittenInFullWithACapital() {
        assertThat(english.weekday(DayOfWeek.MONDAY)).isEqualTo("Monday");
        assertThat(dutch.weekday(DayOfWeek.WEDNESDAY)).isEqualTo("Woensdag");
    }

    @Test
    void aKnownProviderIsNamedAndAnUnknownOneByItsId() {
        assertThat(english.provider(SUNNY, KNOWN)).isEqualTo("Sunny");
        assertThat(english.provider(new ProviderId("gone"), KNOWN)).isEqualTo("gone (not available)");
    }

    @Test
    void theFallbackIsAProviderOrNone() {
        assertThat(english.provider(SUNNY, KNOWN)).isEqualTo("Sunny");
        assertThat(english.noFallback()).isEqualTo("None");
    }
}

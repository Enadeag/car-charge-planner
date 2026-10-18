package io.github.enadeag.carchargeplanner.desktop.settings.form;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.enadeag.carchargeplanner.desktop.app.Appearance;
import io.github.enadeag.carchargeplanner.desktop.app.DisplayMode;
import io.github.enadeag.carchargeplanner.desktop.app.Units;
import io.github.enadeag.carchargeplanner.desktop.text.Language;
import io.github.enadeag.carchargeplanner.desktop.text.Messages;
import io.github.enadeag.carchargeplanner.engine.config.Phases;
import io.github.enadeag.carchargeplanner.engine.config.ProviderId;
import io.github.enadeag.carchargeplanner.engine.forecast.ProviderInfo;
import java.time.DayOfWeek;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
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
    void aLanguageIsNamedInItselfWhateverTheAppSpeaks() {
        assertThat(english.language(Optional.of(Language.DUTCH))).isEqualTo("Nederlands");
        assertThat(dutch.language(Optional.of(Language.DUTCH))).isEqualTo("Nederlands");
        assertThat(english.language(Optional.empty())).isEqualTo("Follow the system");
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
        assertThat(english.fallback(Optional.of(SUNNY), KNOWN)).isEqualTo("Sunny");
        assertThat(english.fallback(Optional.empty(), KNOWN)).isEqualTo("None");
    }
}

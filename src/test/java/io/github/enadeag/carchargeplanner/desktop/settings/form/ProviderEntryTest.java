package io.github.enadeag.carchargeplanner.desktop.settings.form;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.enadeag.carchargeplanner.desktop.text.Language;
import io.github.enadeag.carchargeplanner.desktop.text.Messages;
import io.github.enadeag.carchargeplanner.engine.config.ProviderId;
import io.github.enadeag.carchargeplanner.engine.forecast.ProviderInfo;
import io.github.enadeag.carchargeplanner.engine.forecast.SettingFormat;
import io.github.enadeag.carchargeplanner.engine.forecast.SettingSpec;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProviderEntryTest {

    private static final Messages ENGLISH = new Messages(Language.ENGLISH);
    private static final String REQUIRED = "Required: this provider cannot be asked without it.";
    private static final ProviderInfo INFO = new ProviderInfo(new ProviderId("needy"), "Needy", "", "", "", "",
            Duration.ofHours(1), Duration.ofDays(2), Duration.ofHours(6), List.of(
                    new SettingSpec("api_key", "API key", "", false, true, SettingFormat.LETTERS_AND_DIGITS),
                    new SettingSpec("base_url", "Server", "", true, false, SettingFormat.SERVER_ADDRESS),
                    new SettingSpec("site", "Site", "", false, false, SettingFormat.TEXT)));

    @Test
    void everyRequiredSettingWithAValueOfTheRightFormHasNoProblem() {
        var entry = new ProviderEntry(INFO, Map.of("site", "home", "base_url", "https://example.org"),
                Map.of("api_key", "abc123"), Set.of());

        assertThat(entry.problems(ENGLISH)).isEmpty();
    }

    @Test
    void aRequiredSettingWithoutAValueIsRequired() {
        assertThat(new ProviderEntry(INFO, Map.of(), Map.of(), Set.of()).problems(ENGLISH))
                .containsExactly(Map.entry("api_key", REQUIRED), Map.entry("site", REQUIRED));
    }

    @Test
    void aLockedSecretCountsAsThere() {
        assertThat(new ProviderEntry(INFO, Map.of("site", "home"), Map.of(), Set.of("api_key")).problems(ENGLISH))
                .isEmpty();
    }

    @Test
    void aValueOfTheWrongFormSaysWhatItShouldLookLike() {
        var problems = new ProviderEntry(INFO, Map.of("site", "home\nrow", "base_url", "http://example.org"),
                Map.of("api_key", "abc-123"), Set.of()).problems(ENGLISH);

        assertThat(problems).containsOnlyKeys("api_key", "base_url", "site");
        assertThat(problems.get("api_key")).startsWith("Use only letters and digits");
        assertThat(problems.get("base_url")).startsWith("Use an address that starts with https://");
        assertThat(problems.get("site")).isEqualTo("Use one line, without line breaks or other control characters.");
    }

    @Test
    void aSecretTypedAmongThePlainSettingsIsNotUsed() {
        assertThat(new ProviderEntry(INFO, Map.of("site", "home", "api_key", "abc123"), Map.of(), Set.of())
                .problems(ENGLISH)).containsExactly(Map.entry("api_key", REQUIRED));
    }
}

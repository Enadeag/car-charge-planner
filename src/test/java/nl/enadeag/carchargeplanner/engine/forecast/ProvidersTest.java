package nl.enadeag.carchargeplanner.engine.forecast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProvidersTest {

    private static final SettingSpec ACCOUNT = new SettingSpec("account", "Account", "Your account name.", false, false);
    private static final SettingSpec TOKEN = new SettingSpec("token", "Token", "Your token.", false, true);
    private static final SettingSpec NOTE = new SettingSpec("note", "Note", "Anything.", true, false);

    private static ProviderInfo info(String id, List<SettingSpec> settings) {
        return new ProviderInfo(new ProviderId(id), "Tiny", "", "", "", "", Duration.ofHours(1), Duration.ofDays(2), Duration.ofHours(6),
                settings);
    }

    private static ForecastProvider tiny(String id) {
        return new ForecastProvider() {
            @Override
            public ProviderInfo info() {
                return ProvidersTest.info(id, List.of());
            }

            @Override
            public List<ForecastStep> fetch(SystemDescription system, Map<String, String> settings, Instant now) {
                return List.of();
            }
        };
    }

    @Test
    void providersAreFoundById() {
        var tiny = tiny("tiny");
        var providers = new Providers(List.of(tiny));

        assertThat(providers.find(new ProviderId("tiny"))).containsSame(tiny);
        assertThat(providers.ids()).containsExactly(new ProviderId("tiny"));
    }

    @Test
    void anUnknownIdIsNotFound() {
        assertThat(new Providers(List.of()).find(new ProviderId("nope"))).isEmpty();
    }

    @Test
    void twoProvidersWithTheSameIdAreRefused() {
        assertThatThrownBy(() -> new Providers(List.of(tiny("tiny"), tiny("tiny"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("two providers");
    }

    @Test
    void aProviderGetsItsDeclaredSettingsWithSecretsFromTheSecretStore() {
        var needy = info("needy", List.of(ACCOUNT, TOKEN, NOTE));
        var got = needy.settingsFrom(Map.of("account", "me", "note", "", "stray", "x"), Map.of("token", "t", "account", "ignored"));
        // a secret is read only from the secret store, a plain one only from the config; undeclared keys are dropped
        assertThat(got).isEqualTo(Map.of("account", "me", "token", "t"));
    }

    @Test
    void aSecretSettingFoundInTheConfigFileIsNotUsed() {
        var needy = info("needy", List.of(ACCOUNT, TOKEN, NOTE));
        var got = needy.settingsFrom(Map.of("account", "me", "token", "from-the-config-file"), Map.of());

        assertThat(got).isEqualTo(Map.of("account", "me"));
        assertThat(needy.missingRequired(got)).containsExactly("Token");
    }

    @Test
    void missingRequiredSettingsAreNamed() {
        var needy = info("needy", List.of(ACCOUNT, TOKEN, NOTE));
        assertThat(needy.missingRequired(Map.of())).containsExactly("Account", "Token");
        assertThat(needy.missingRequired(Map.of("token", "t"))).containsExactly("Account");
    }
}

package io.github.enadeag.carchargeplanner.engine.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Which forecast providers the owner chose, and their settings that are not secret (secret ones are in the keychain).
 * A provider this version does not know is not refused here: the forecast service says so when it is asked. In JSON
 * the ids are plain texts, and an empty fallback means there is none.
 *
 * @param primary the primary forecast provider
 * @param fallback the fallback provider, different from the primary, or empty for none
 * @param settings per provider, setting key to value; secret settings are not here
 */
public record ForecastSelection(
        ProviderId primary, Optional<ProviderId> fallback, Map<ProviderId, Map<String, String>> settings) {

    /** What a setting key looks like: lower case letters, digits, hyphens and underscores. */
    public static final Pattern SETTING_KEY = Pattern.compile("[a-z0-9][a-z0-9_-]*");

    /**
     * Checks the setting names, and copies the settings.
     *
     * @throws IllegalArgumentException when a setting name does not have the allowed form, or the fallback is the
     *     primary
     */
    public ForecastSelection {
        Checks.require(!fallback.equals(Optional.of(primary)), "fallback must be different from primary");
        settings = checkedCopy(settings);
    }

    /** As the config file has it: the fallback is a text, empty for none, and the settings' ids are texts. */
    @JsonCreator
    private static ForecastSelection fromJson(
            @JsonProperty("primary") ProviderId primary,
            @JsonProperty("fallback") String fallback,
            @JsonProperty("settings") Map<String, Map<String, String>> settings) {
        Checks.require(fallback.isEmpty() || ProviderId.isValid(fallback),
                "fallback must be empty or a provider id such as forecast-solar");
        var byId = new LinkedHashMap<ProviderId, Map<String, String>>();
        settings.forEach((id, values) -> {
            Checks.require(ProviderId.isValid(id), "settings: '" + id + "' is not a provider id");
            byId.put(new ProviderId(id), values);
        });
        return new ForecastSelection(primary,
                fallback.isEmpty() ? Optional.empty() : Optional.of(new ProviderId(fallback)), byId);
    }

    private static Map<ProviderId, Map<String, String>> checkedCopy(Map<ProviderId, Map<String, String>> settings) {
        var copy = new LinkedHashMap<ProviderId, Map<String, String>>();
        settings.forEach((id, values) -> {
            values.keySet().forEach(key -> Checks.require(SETTING_KEY.matcher(key).matches(),
                    "settings." + id + ": '" + key + "' is not a setting name"));
            copy.put(id, Map.copyOf(values));
        });
        return Map.copyOf(copy);
    }

    /**
     * The providers to ask, in order.
     *
     * @return the primary, then the fallback when there is one
     */
    public List<ProviderId> order() {
        return fallback.map(id -> List.of(primary, id)).orElse(List.of(primary));
    }

    /**
     * One provider's settings from the config file.
     *
     * @param providerId the provider whose settings are wanted; it need not be chosen or known
     * @return its settings, empty when the file has none for it
     */
    public Map<String, String> settingsOf(ProviderId providerId) {
        return settings.getOrDefault(providerId, Map.of());
    }
}

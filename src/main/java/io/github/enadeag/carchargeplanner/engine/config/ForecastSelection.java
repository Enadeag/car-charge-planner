package io.github.enadeag.carchargeplanner.engine.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Which forecast providers the owner chose, and their settings that are not secret (secret ones are in the keychain).
 * A provider this version does not know is not refused here: the forecast service says so when it is asked.
 *
 * @param fallback the fallback provider, or empty for none
 * @param settings per provider id, setting key to value
 */
public record ForecastSelection(String primary, String fallback, Map<String, Map<String, String>> settings) {

    public static final Pattern PROVIDER_ID = Pattern.compile("[a-z0-9][a-z0-9-]*");
    public static final Pattern SETTING_KEY = Pattern.compile("[a-z0-9][a-z0-9_-]*");

    public ForecastSelection {
        Checks.require(PROVIDER_ID.matcher(primary).matches(), "primary must be a provider id such as open-meteo");
        Checks.require(fallback.isEmpty() || PROVIDER_ID.matcher(fallback).matches(),
                "fallback must be empty or a provider id such as forecast-solar");
        Checks.require(!fallback.equals(primary), "fallback must be different from primary");
        settings = checkedCopy(settings);
    }

    private static Map<String, Map<String, String>> checkedCopy(Map<String, Map<String, String>> settings) {
        var copy = new LinkedHashMap<String, Map<String, String>>();
        settings.forEach((id, values) -> {
            Checks.require(PROVIDER_ID.matcher(id).matches(), "settings: '" + id + "' is not a provider id");
            values.keySet().forEach(key -> Checks.require(SETTING_KEY.matcher(key).matches(),
                    "settings." + id + ": '" + key + "' is not a setting name"));
            copy.put(id, Map.copyOf(values));
        });
        return Map.copyOf(copy);
    }

    /** The providers to ask, in order: the primary, then the fallback when there is one. */
    public List<String> order() {
        return fallback.isEmpty() ? List.of(primary) : List.of(primary, fallback);
    }

    public Map<String, String> settingsOf(String providerId) {
        return settings.getOrDefault(providerId, Map.of());
    }
}

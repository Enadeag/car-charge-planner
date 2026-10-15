package io.github.enadeag.carchargeplanner.engine.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Which forecast providers the owner chose, and their settings that are not secret (secret ones are in the keychain).
 * A provider this version does not know is not refused here: the forecast service says so when it is asked.
 *
 * @param primary the id of the primary forecast provider
 * @param fallback the id of the fallback provider, or empty for none
 * @param settings per provider id, setting key to value; secret settings are not here
 */
public record ForecastSelection(String primary, String fallback, Map<String, Map<String, String>> settings) {

    /** What a provider id looks like: lower case letters, digits and hyphens. */
    public static final Pattern PROVIDER_ID = Pattern.compile("[a-z0-9][a-z0-9-]*");
    /** What a setting key looks like: lower case letters, digits, hyphens and underscores. */
    public static final Pattern SETTING_KEY = Pattern.compile("[a-z0-9][a-z0-9_-]*");

    /**
     * Checks the ids and setting names, and copies the settings.
     *
     * @throws IllegalArgumentException when an id or setting name does not have the allowed form, or the fallback is
     *     the primary
     */
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

    /**
     * The providers to ask, in order.
     *
     * @return the primary's id, then the fallback's when there is one
     */
    public List<String> order() {
        return fallback.isEmpty() ? List.of(primary) : List.of(primary, fallback);
    }

    /**
     * One provider's settings from the config file.
     *
     * @param providerId the provider whose settings are wanted; it need not be chosen or known
     * @return its settings, empty when the file has none for it
     */
    public Map<String, String> settingsOf(String providerId) {
        return settings.getOrDefault(providerId, Map.of());
    }
}

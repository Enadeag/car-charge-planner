package nl.enadeag.carchargeplanner.engine.config;

import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting;
import nl.enadeag.carchargeplanner.engine.setting.Setting;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Which forecast providers the owner chose, and their settings that are not secret (secret ones are in the keychain).
 * A provider this version does not know is not refused here: the forecast service says so when it is asked.
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
        if (fallback.equals(Optional.of(primary))) {
            throw new InvalidSetting(Setting.FALLBACK_PROVIDER, InvalidSetting.Rule.SAME_AS_PRIMARY,
                    "fallback must be different from primary");
        }
        settings = checkedCopy(settings);
    }

    private static Map<ProviderId, Map<String, String>> checkedCopy(Map<ProviderId, Map<String, String>> settings) {
        var copy = new LinkedHashMap<ProviderId, Map<String, String>>();
        settings.forEach((id, values) -> {
            for (var key : values.keySet()) {
                if (!SETTING_KEY.matcher(key).matches()) {
                    throw new IllegalArgumentException("settings." + id + ": '" + key + "' is not a setting name");
                }
            }
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

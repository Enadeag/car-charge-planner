package io.github.enadeag.carchargeplanner.engine.forecast;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a forecast provider tells about itself.
 *
 * @param id short and stable: used in the config file and the cache file's name
 * @param attribution the credit the provider's terms ask for, shown with the forecast
 * @param sends in plain words, what leaves the computer
 * @param minRefresh it is never asked more often than this
 * @param horizon how far ahead it forecasts
 * @param maxUsefulAge a forecast older than this is not worth keeping when it cannot be refreshed
 */
public record ProviderInfo(
        String id,
        String name,
        String description,
        String termsUrl,
        String attribution,
        String sends,
        Duration minRefresh,
        Duration horizon,
        Duration maxUsefulAge,
        List<SettingSpec> settings) {

    public ProviderInfo {
        settings = List.copyOf(settings);
    }

    /** What the provider's fetch is given: its declared settings only, the secret ones from {@code secret}. */
    public Map<String, String> settingsFrom(Map<String, String> plain, Map<String, String> secret) {
        var values = new LinkedHashMap<String, String>();
        for (var spec : settings) {
            var value = (spec.secret() ? secret : plain).getOrDefault(spec.key(), "");
            if (!value.isEmpty()) {
                values.put(spec.key(), value);
            }
        }
        return values;
    }

    /** The labels of the required settings that have no value. */
    public List<String> missingRequired(Map<String, String> values) {
        return settings.stream()
                .filter(spec -> !spec.optional() && values.getOrDefault(spec.key(), "").isEmpty())
                .map(SettingSpec::label)
                .toList();
    }
}

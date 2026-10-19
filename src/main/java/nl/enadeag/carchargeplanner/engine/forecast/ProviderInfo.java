package nl.enadeag.carchargeplanner.engine.forecast;

import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a forecast provider tells about itself.
 *
 * @param id the provider's short, stable name
 * @param name the provider's name
 * @param description a few sentences about the service
 * @param termsUrl the https address of the service's terms
 * @param attribution the credit the provider's terms ask for
 * @param sends in plain words, what is sent to the service
 * @param minRefresh the shortest time the provider allows between two requests
 * @param horizon how far ahead it forecasts
 * @param maxUsefulAge the age after which a forecast of the provider is no longer useful
 * @param settings the settings it asks for, in order
 */
public record ProviderInfo(
        ProviderId id,
        String name,
        String description,
        String termsUrl,
        String attribution,
        String sends,
        Duration minRefresh,
        Duration horizon,
        Duration maxUsefulAge,
        List<SettingSpec> settings) {

    /** Copies the settings. */
    public ProviderInfo {
        settings = List.copyOf(settings);
    }

    /**
     * What the provider's fetch is given: its declared settings that have a value, and nothing else.
     *
     * @param plain settings from the config file; a secret setting found here is not used
     * @param secret settings as stored with the API keys or as the owner typed them; only the secret ones are used
     * @return per declared setting key, its value; empty values are left out
     */
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

    /**
     * The required settings that have no value, to tell the owner before anything is sent.
     *
     * @param values the settings the provider would be given, as {@link #settingsFrom} makes them
     * @return the labels of the required settings that are missing or empty, in declared order
     */
    public List<String> missingRequired(Map<String, String> values) {
        return settings.stream()
                .filter(spec -> !spec.optional() && values.getOrDefault(spec.key(), "").isEmpty())
                .map(SettingSpec::label)
                .toList();
    }

    /**
     * The values of the secret settings among these, such as API keys, to keep out of what is shown or logged.
     *
     * @param values settings of this provider, as {@link #settingsFrom} makes them; others are ignored
     * @return the values of the declared secret settings that have one, in declared order
     */
    public List<String> secretValues(Map<String, String> values) {
        return settings.stream()
                .filter(SettingSpec::secret)
                .map(spec -> values.getOrDefault(spec.key(), ""))
                .filter(value -> !value.isBlank())
                .toList();
    }
}

package nl.enadeag.carchargeplanner.engine.route;

import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import nl.enadeag.carchargeplanner.engine.forecast.SettingSpec;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * What a route service tells about itself.
 *
 * @param id the service's short, stable name, under which its secret settings are kept
 * @param name the service's name
 * @param termsUrl the https address of the service's terms
 * @param attribution the credit the service's terms ask for
 * @param sends in plain words, what is sent to the service
 * @param cooldown how long the service is not asked after it said it was asked too often without saying when to ask
 *     again, more than 0
 * @param settings the settings it asks for, in order
 */
public record RouteServiceInfo(
        ProviderId id,
        String name,
        String termsUrl,
        String attribution,
        String sends,
        Duration cooldown,
        List<SettingSpec> settings) {

    /**
     * Checks the cool-down and copies the settings.
     *
     * @throws IllegalArgumentException when the cool-down is not more than 0
     */
    public RouteServiceInfo {
        if (cooldown.isNegative() || cooldown.isZero()) {
            throw new IllegalArgumentException("a cool-down must be more than 0, not " + cooldown);
        }
        settings = List.copyOf(settings);
    }

    /**
     * The required settings that have no value, to tell the owner before anything is sent.
     *
     * @param values the settings the service would be given
     * @return the labels of the required settings that are missing or blank, in declared order
     */
    public List<String> missingRequired(Map<String, String> values) {
        return settings.stream()
                .filter(spec -> !spec.optional() && values.getOrDefault(spec.name(), "").isBlank())
                .map(SettingSpec::label)
                .toList();
    }

    /**
     * The values of the secret settings among these, such as API keys, to keep out of what is shown or logged.
     *
     * @param values settings of this service; others are ignored
     * @return the values of the declared secret settings that have one, in declared order
     */
    public List<String> secretValues(Map<String, String> values) {
        return settings.stream()
                .filter(SettingSpec::secret)
                .map(spec -> values.getOrDefault(spec.name(), ""))
                .filter(value -> !value.isBlank())
                .toList();
    }
}

package io.github.enadeag.carchargeplanner.engine.secrets;

import io.github.enadeag.carchargeplanner.engine.config.ProviderId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The providers' secret settings, such as API keys. Empty values are left out.
 *
 * @param byProvider per provider, setting key to value
 */
public record Secrets(Map<ProviderId, Map<String, String>> byProvider) {

    /** No secret settings at all. */
    public static final Secrets NONE = new Secrets(Map.of());

    /** What a setting key looks like, so a name in the keychain can be split up again and written safely. */
    private static final Pattern NAME = Pattern.compile("[a-z0-9][a-z0-9_-]*");

    /** Leaves out empty values and providers without any, and copies the rest. */
    public Secrets {
        var kept = new LinkedHashMap<ProviderId, Map<String, String>>();
        byProvider.forEach((id, values) -> {
            var filled = new LinkedHashMap<>(values);
            filled.values().removeIf(String::isEmpty);
            if (!filled.isEmpty()) {
                kept.put(id, Map.copyOf(filled));
            }
        });
        byProvider = Map.copyOf(kept);
    }

    /**
     * One provider's secret settings.
     *
     * @param providerId the provider whose settings are wanted; it need not have any
     * @return its settings, empty when it has none
     */
    public Map<String, String> of(ProviderId providerId) {
        return byProvider.getOrDefault(providerId, Map.of());
    }

    /**
     * Refuses setting names that could not be split up again from an entry's name or written safely; provider ids
     * always can.
     *
     * @throws SecretsProblem when a setting name has characters that are not allowed
     */
    void checkNames() throws SecretsProblem {
        for (var values : byProvider.values()) {
            if (!values.keySet().stream().allMatch(key -> NAME.matcher(key).matches())) {
                throw new SecretsProblem("A setting name has characters that are not allowed");
            }
        }
    }
}

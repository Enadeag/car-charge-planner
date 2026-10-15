package io.github.enadeag.carchargeplanner.engine.secrets;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The providers' secret settings, such as API keys. Empty values are left out.
 *
 * @param byProvider per provider id, setting key to value
 */
public record Secrets(Map<String, Map<String, String>> byProvider) {

    /** No secret settings at all. */
    public static final Secrets NONE = new Secrets(Map.of());

    /** The same rule for provider ids and setting keys, so a name in the keychain can be split up again. */
    private static final Pattern NAME = Pattern.compile("[a-z0-9][a-z0-9_-]*");

    /** Leaves out empty values and providers without any, and copies the rest. */
    public Secrets {
        var kept = new LinkedHashMap<String, Map<String, String>>();
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
     * @param providerId the provider whose settings are wanted, by its id; it need not have any
     * @return its settings, empty when it has none
     */
    public Map<String, String> of(String providerId) {
        return byProvider.getOrDefault(providerId, Map.of());
    }

    /**
     * Refuses names that could not be split up again from an entry's name or written safely.
     *
     * @throws SecretsProblem when a provider id or setting name has characters that are not allowed
     */
    void checkNames() throws SecretsProblem {
        for (var entry : byProvider.entrySet()) {
            boolean named = NAME.matcher(entry.getKey()).matches()
                    && entry.getValue().keySet().stream().allMatch(key -> NAME.matcher(key).matches());
            if (!named) {
                throw new SecretsProblem("A provider id or setting name has characters that are not allowed");
            }
        }
    }
}

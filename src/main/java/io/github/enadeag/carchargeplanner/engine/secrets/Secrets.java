package io.github.enadeag.carchargeplanner.engine.secrets;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The providers' secret settings, such as API keys: per provider id, setting key to value. Empty values are left out,
 * so storing them forgets a key.
 */
public record Secrets(Map<String, Map<String, String>> byProvider) {

    public static final Secrets NONE = new Secrets(Map.of());

    /** The same rule for provider ids and setting keys, so a name in the keychain can be split up again. */
    private static final Pattern NAME = Pattern.compile("[a-z0-9][a-z0-9_-]*");

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

    public Map<String, String> of(String providerId) {
        return byProvider.getOrDefault(providerId, Map.of());
    }

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

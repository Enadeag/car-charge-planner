package io.github.enadeag.carchargeplanner.engine.forecast;

/**
 * One setting a provider asks for, such as a server address or an API key.
 *
 * @param key a short stable name: lower case letters, digits, hyphens and underscores
 * @param label the setting's name in words
 * @param hint a sentence that explains what to fill in
 * @param optional whether it may be left empty
 * @param secret whether the value is a secret, such as an API key
 */
public record SettingSpec(String key, String label, String hint, boolean optional, boolean secret) {
}

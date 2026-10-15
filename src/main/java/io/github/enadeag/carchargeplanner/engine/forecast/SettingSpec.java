package io.github.enadeag.carchargeplanner.engine.forecast;

/**
 * One setting a provider asks for, such as a server address or an API key.
 *
 * @param key short and stable: the name in the config file or the keychain
 * @param label the setting's name, for the owner
 * @param hint a sentence that helps the owner fill it in
 * @param optional whether it may be left empty
 * @param secret kept in the keychain, never in the config file
 */
public record SettingSpec(String key, String label, String hint, boolean optional, boolean secret) {
}

package nl.enadeag.carchargeplanner.engine.forecast;

/**
 * One setting a provider asks for, such as a server address or an API key.
 *
 * @param name a short stable name: lower case letters, digits, hyphens and underscores
 * @param label the setting in words, as the owner sees it
 * @param hint a sentence that explains what to fill in
 * @param optional whether it may be left empty
 * @param secret whether the value is a secret, such as an API key
 * @param format what a value must look like
 */
public record SettingSpec(String name, String label, String hint, boolean optional, boolean secret,
        SettingFormat format) {

    /**
     * A setting that takes any text.
     *
     * @param name a short stable name: lower case letters, digits, hyphens and underscores
     * @param label the setting in words, as the owner sees it
     * @param hint a sentence that explains what to fill in
     * @param optional whether it may be left empty
     * @param secret whether the value is a secret, such as an API key
     */
    public SettingSpec(String name, String label, String hint, boolean optional, boolean secret) {
        this(name, label, hint, optional, secret, SettingFormat.TEXT);
    }
}

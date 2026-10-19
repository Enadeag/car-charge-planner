package nl.enadeag.carchargeplanner.desktop.settings.form;

import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.forecast.ProviderInfo;
import nl.enadeag.carchargeplanner.engine.forecast.SettingFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A forecast provider's settings as its card holds them.
 *
 * @param info what the provider tells about itself, with the settings it asks for
 * @param plain the settings that are not secret, as entered, per key; empty ones left out
 * @param secret the secret settings, as entered, per key; empty ones left out
 * @param locked the keys of secret settings that are stored but cannot be read while the keyring is locked
 */
public record ProviderEntry(ProviderInfo info, Map<String, String> plain, Map<String, String> secret,
        Set<String> locked) {

    /** Copies the maps and the set. */
    public ProviderEntry {
        plain = Map.copyOf(plain);
        secret = Map.copyOf(secret);
        locked = Set.copyOf(locked);
    }

    /**
     * The problems the card's fields show: a required setting without a value, and a value of the wrong form, such as
     * a server address a key may not be sent to. A locked secret counts as there: it is stored, only not readable now.
     *
     * @param messages the texts, in the owner's language
     * @return per setting key the problem its field shows, the wrong form over the missing value; empty when every
     *     required setting has a value and every value has the right form
     */
    public Map<String, String> problems(Messages messages) {
        var problems = new LinkedHashMap<String, String>();
        var entered = info.settingsFrom(plain, secret);
        var missing = info.missingRequired(entered);
        for (var setting : info.settings()) {
            if (missing.contains(setting.label()) && !locked.contains(setting.key())) {
                problems.put(setting.key(), messages.text(TextKey.PROBLEM_REQUIRED));
            }
        }
        for (var setting : info.settings()) {
            if (!setting.format().accepts(entered.getOrDefault(setting.key(), ""))) {
                problems.put(setting.key(), messages.text(problem(setting.format())));
            }
        }
        return problems;
    }

    private static TextKey problem(SettingFormat format) {
        return switch (format) {
            case SERVER_ADDRESS -> TextKey.PROBLEM_SERVER_ADDRESS;
            case LETTERS_AND_DIGITS -> TextKey.PROBLEM_LETTERS_AND_DIGITS;
            case TEXT -> TextKey.PROBLEM_ONE_LINE;
        };
    }
}

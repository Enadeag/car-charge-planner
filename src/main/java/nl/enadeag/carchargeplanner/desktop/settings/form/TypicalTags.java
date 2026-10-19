package nl.enadeag.carchargeplanner.desktop.settings.form;

import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Which fields of a card hold a typical value: tagged as such until the owner enters a value of their own. Values
 * filled in by Settings itself, as it loads or as the owner asks for typical values, keep the tag.
 */
public final class TypicalTags {

    private final Map<ConfigSetting, Consumer<String>> tags = new EnumMap<>(ConfigSetting.class);
    private final Set<ConfigSetting> tagged = EnumSet.noneOf(ConfigSetting.class);
    private final Set<ConfigSetting> loaded = EnumSet.noneOf(ConfigSetting.class);
    private final String tag;
    private boolean filling;

    /**
     * Makes the tags for one card.
     *
     * @param tag what a tagged field says, such as "Typical value", in the owner's language
     */
    public TypicalTags(String tag) {
        this.tag = tag;
    }

    /**
     * Follows one field; call {@link #edited(ConfigSetting)} whenever its value changes.
     *
     * @param setting the setting the field holds
     * @param showTag shows a text as the field's tag: the tag, or empty for none
     */
    public void follow(ConfigSetting setting, Consumer<String> showTag) {
        tags.put(setting, showTag);
    }

    /**
     * Takes a field's tag away when the owner changed its value; a value Settings fills in itself keeps it.
     *
     * @param setting the followed setting whose field changed
     */
    public void edited(ConfigSetting setting) {
        if (!filling && tagged.remove(setting)) {
            tags.get(setting).accept("");
        }
    }

    /**
     * Fills the fields with stored values and tags the typical ones, as the values not edited.
     *
     * @param typical the stored typical values; settings of other cards are ignored
     * @param fill puts the stored values in the fields
     */
    public void load(Set<ConfigSetting> typical, Runnable fill) {
        tag(typical, fill);
        loaded.clear();
        loaded.addAll(tagged);
    }

    /**
     * Fills every followed field with a typical value and tags it, as the owner asked.
     *
     * @param fill puts the typical values in the fields
     */
    public void useTypical(Runnable fill) {
        tag(tags.keySet(), fill);
    }

    /**
     * Changes how the fields show their values without changing what they stand for, such as other units; the tags
     * stay.
     *
     * @param change shows the same values another way
     */
    public void keepTagsWhile(Runnable change) {
        filling = true;
        try {
            change.run();
        } finally {
            filling = false;
        }
    }

    /**
     * The settings tagged now.
     *
     * @return the followed settings that hold a typical value
     */
    public Set<ConfigSetting> tagged() {
        return Set.copyOf(tagged);
    }

    /**
     * The typical settings to store: the stored ones, with the followed settings as tagged now.
     *
     * @param stored the stored typical settings, also of other cards
     * @return those of other cards unchanged, and of this card the ones tagged now
     */
    public Set<ConfigSetting> appliedTo(Set<ConfigSetting> stored) {
        var applied = EnumSet.noneOf(ConfigSetting.class);
        applied.addAll(stored);
        applied.removeAll(tags.keySet());
        applied.addAll(tagged);
        return Set.copyOf(applied);
    }

    /**
     * Whether the tags differ from the stored ones.
     *
     * @return true when saving would change which values are typical
     */
    public boolean isChanged() {
        return !tagged.equals(loaded);
    }

    private void tag(Set<ConfigSetting> typical, Runnable fill) {
        keepTagsWhile(fill);
        tagged.clear();
        typical.stream().filter(tags::containsKey).forEach(tagged::add);
        tags.forEach((setting, showTag) -> showTag.accept(tagged.contains(setting) ? tag : ""));
    }
}

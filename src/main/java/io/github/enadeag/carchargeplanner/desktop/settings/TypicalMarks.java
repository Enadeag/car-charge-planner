package io.github.enadeag.carchargeplanner.desktop.settings;

import io.github.enadeag.carchargeplanner.engine.setting.Setting;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Which fields of a card hold a typical value: tagged as such until the owner enters a value of their own. Values
 * filled in by Settings itself, as it loads or as the owner asks for typical values, keep the tag.
 */
final class TypicalMarks {

    private final Map<Setting, FieldRow> rows = new EnumMap<>(Setting.class);
    private final Set<Setting> marked = EnumSet.noneOf(Setting.class);
    private final Set<Setting> loaded = EnumSet.noneOf(Setting.class);
    private final String tag;
    private boolean filling;

    /**
     * Makes the marks for one card.
     *
     * @param tag what a tagged field says, such as "Typical value", in the owner's language
     */
    TypicalMarks(String tag) {
        this.tag = tag;
    }

    /**
     * Follows one field: an edit of the owner's takes its tag away.
     *
     * @param setting the setting the field holds
     * @param row the field's row, which shows the tag
     * @param input the field's control
     */
    void follow(Setting setting, FieldRow row, Input input) {
        rows.put(setting, row);
        input.onChange(() -> {
            if (!filling && marked.remove(setting)) {
                row.showTag("");
            }
        });
    }

    /**
     * Fills the fields with stored values and tags the typical ones, as the values not edited.
     *
     * @param typical the stored typical values; settings of other cards are ignored
     * @param fill puts the stored values in the fields
     */
    void load(Set<Setting> typical, Runnable fill) {
        mark(typical, fill);
        loaded.clear();
        loaded.addAll(marked);
    }

    /**
     * Fills every followed field with a typical value and tags it, as the owner asked.
     *
     * @param fill puts the typical values in the fields
     */
    void useTypical(Runnable fill) {
        mark(rows.keySet(), fill);
    }

    /**
     * The settings tagged now.
     *
     * @return the followed settings that hold a typical value
     */
    Set<Setting> marked() {
        return Set.copyOf(marked);
    }

    /**
     * Whether the tags differ from the stored ones.
     *
     * @return true when saving would change which values are typical
     */
    boolean isChanged() {
        return !marked.equals(loaded);
    }

    private void mark(Set<Setting> typical, Runnable fill) {
        filling = true;
        try {
            fill.run();
        } finally {
            filling = false;
        }
        marked.clear();
        typical.stream().filter(rows::containsKey).forEach(marked::add);
        rows.forEach((setting, row) -> row.showTag(marked.contains(setting) ? tag : ""));
    }
}

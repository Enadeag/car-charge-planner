package nl.enadeag.carchargeplanner.desktop.settings.form;

import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting;
import java.util.EnumSet;
import java.util.Set;

/**
 * Which fields of a card hold a typical value: marked as such until the owner enters a value of their own. Values
 * filled in by Settings itself, as it loads or as the owner asks for typical values, keep the mark. No field shows
 * its mark; the marks decide which values are stored as typical.
 */
public final class TypicalMarks {

    private final Set<ConfigSetting> followed = EnumSet.noneOf(ConfigSetting.class);
    private final Set<ConfigSetting> marked = EnumSet.noneOf(ConfigSetting.class);
    private final Set<ConfigSetting> loaded = EnumSet.noneOf(ConfigSetting.class);
    private boolean filling;

    /** Makes the marks for one card, which follow no field until {@link #follow(ConfigSetting)}. */
    public TypicalMarks() {
    }

    /**
     * Follows one field; call {@link #edited(ConfigSetting)} whenever its value changes.
     *
     * @param setting the setting the field holds
     */
    public void follow(ConfigSetting setting) {
        followed.add(setting);
    }

    /**
     * Takes a field's mark away when the owner changed its value; a value Settings fills in itself keeps it.
     *
     * @param setting the followed setting whose field changed
     */
    public void edited(ConfigSetting setting) {
        if (!filling) {
            marked.remove(setting);
        }
    }

    /**
     * Fills the fields with stored values and marks the typical ones, as the values not edited.
     *
     * @param typical the stored typical values; settings of other cards are ignored
     * @param fill puts the stored values in the fields
     */
    public void load(Set<ConfigSetting> typical, Runnable fill) {
        mark(typical, fill);
        loaded.clear();
        loaded.addAll(marked);
    }

    /**
     * Fills every followed field with a typical value and marks it, as the owner asked.
     *
     * @param fill puts the typical values in the fields
     */
    public void useTypical(Runnable fill) {
        mark(followed, fill);
    }

    /**
     * Fills some followed fields with a typical value and marks them, as the owner asked; the other fields keep their
     * values and their marks.
     *
     * @param settings the followed settings to fill
     * @param fill puts the typical values in those fields
     */
    public void useTypical(Set<ConfigSetting> settings, Runnable fill) {
        keepMarksWhile(fill);
        settings.stream().filter(followed::contains).forEach(marked::add);
    }

    /**
     * Changes how the fields show their values without changing what they stand for, such as other units; the marks
     * stay.
     *
     * @param change shows the same values another way
     */
    public void keepMarksWhile(Runnable change) {
        filling = true;
        try {
            change.run();
        } finally {
            filling = false;
        }
    }

    /**
     * The settings marked now.
     *
     * @return the followed settings that hold a typical value
     */
    public Set<ConfigSetting> marked() {
        return Set.copyOf(marked);
    }

    /**
     * The typical settings to store: the stored ones, with the followed settings as marked now.
     *
     * @param stored the stored typical settings, also of other cards
     * @return those of other cards unchanged, and of this card the ones marked now
     */
    public Set<ConfigSetting> appliedTo(Set<ConfigSetting> stored) {
        var applied = EnumSet.noneOf(ConfigSetting.class);
        applied.addAll(stored);
        applied.removeAll(followed);
        applied.addAll(marked);
        return Set.copyOf(applied);
    }

    /**
     * Whether the marks differ from the stored ones.
     *
     * @return true when saving would change which values are typical
     */
    public boolean isChanged() {
        return !marked.equals(loaded);
    }

    private void mark(Set<ConfigSetting> typical, Runnable fill) {
        keepMarksWhile(fill);
        marked.clear();
        typical.stream().filter(followed::contains).forEach(marked::add);
    }
}

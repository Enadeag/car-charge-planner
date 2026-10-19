package nl.enadeag.carchargeplanner.desktop.settings.page;

import nl.enadeag.carchargeplanner.desktop.settings.field.FieldRow;
import nl.enadeag.carchargeplanner.desktop.settings.form.ProblemTexts;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting;
import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting;
import java.util.EnumMap;
import java.util.Map;

/** The rows of a page by the setting they hold, so a value the engine refuses shows its problem at its field. */
public final class ProblemRows {

    private final Map<ConfigSetting, FieldRow> rows = new EnumMap<>(ConfigSetting.class);
    private final Messages messages;

    /**
     * Makes the map, empty.
     *
     * @param messages the texts, in the owner's language
     */
    public ProblemRows(Messages messages) {
        this.messages = messages;
    }

    /**
     * Adds a row.
     *
     * @param setting the setting the row holds
     * @param row the field that shows the setting, where its problem shows when the engine refuses its value
     * @return the same row, to put on a card
     */
    public FieldRow add(ConfigSetting setting, FieldRow row) {
        rows.put(setting, row);
        return row;
    }

    /**
     * Shows a refused value's problem at its field.
     *
     * @param refused what the engine refused; a setting without a row here is ignored
     */
    public void show(InvalidSetting refused) {
        var row = rows.get(refused.setting());
        if (row != null) {
            row.showProblem(ProblemTexts.of(refused.rule(), messages));
        }
    }

    /** Takes every problem away. */
    public void clear() {
        rows.values().forEach(FieldRow::clearProblem);
    }
}

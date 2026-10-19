package nl.enadeag.carchargeplanner.desktop.settings;

import nl.enadeag.carchargeplanner.desktop.settings.form.ProblemTexts;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting;
import nl.enadeag.carchargeplanner.engine.setting.Setting;
import java.util.EnumMap;
import java.util.Map;

/** The rows of a page by the setting they hold, so a value the engine refuses shows its problem at its field. */
final class ProblemRows {

    private final Map<Setting, FieldRow> rows = new EnumMap<>(Setting.class);
    private final Messages messages;

    /**
     * Makes the map, empty.
     *
     * @param messages the texts, in the owner's language
     */
    ProblemRows(Messages messages) {
        this.messages = messages;
    }

    /**
     * Adds a row.
     *
     * @param setting the setting the row holds
     * @param row the field that shows the setting, where its problem shows when the engine refuses its value
     * @return the same row, to put on a card
     */
    FieldRow add(Setting setting, FieldRow row) {
        rows.put(setting, row);
        return row;
    }

    /**
     * Shows a refused value's problem at its field.
     *
     * @param refused what the engine refused; a setting without a row here is ignored
     */
    void show(InvalidSetting refused) {
        var row = rows.get(refused.setting());
        if (row != null) {
            row.showProblem(ProblemTexts.of(refused.rule(), messages));
        }
    }

    /** Takes every problem away. */
    void clear() {
        rows.values().forEach(FieldRow::clearProblem);
    }
}

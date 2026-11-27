package nl.enadeag.carchargeplanner.desktop.settings.page;

import nl.enadeag.carchargeplanner.desktop.settings.field.FieldRow;
import nl.enadeag.carchargeplanner.desktop.settings.field.NumberField;
import nl.enadeag.carchargeplanner.desktop.settings.form.ProblemTexts;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting;
import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting;
import java.util.EnumMap;
import java.util.Map;

/**
 * The rows of a page by the setting they hold, so a value the engine refuses shows its problem at its field, and a
 * number field's own problem shows before the engine sees the number.
 */
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
     * What a field says about a value the engine refused: for a number outside its setting's limit, the limit as its
     * field shows numbers, else the sentence for the rule.
     *
     * @param refused what the engine refused
     * @return the problem, in the owner's language
     */
    public String text(InvalidSetting refused) {
        var row = rows.get(refused.setting());
        if (refused.rule() == InvalidSetting.Rule.OUT_OF_RANGE && row != null
                && row.input() instanceof NumberField number) {
            return number.outOfRange();
        }
        return ProblemTexts.of(refused.rule(), messages);
    }

    /**
     * Shows a refused value's problem at its field.
     *
     * @param refused what the engine refused; a setting without a row here is ignored
     */
    public void show(InvalidSetting refused) {
        show(refused.setting(), text(refused));
    }

    /**
     * Shows a problem the form found itself at its field, such as a choice not made.
     *
     * @param setting the setting whose field shows it; one without a row here is ignored
     * @param problem what the field says, in the owner's language
     */
    public void show(ConfigSetting setting, String problem) {
        var row = rows.get(setting);
        if (row != null) {
            row.showProblem(problem);
        }
    }

    /**
     * Shows at each number field what is wrong with its text, such as no number, so every field gets its own.
     *
     * @return true when a field's text is wrong, and the engine should not check the numbers
     */
    public boolean showTyping() {
        var wrong = false;
        for (var row : rows.values()) {
            if (row.input() instanceof NumberField number && number.problem().isPresent()) {
                row.showProblem(number.problem().get());
                wrong = true;
            }
        }
        return wrong;
    }

    /**
     * Whether a number field's text is wrong; shows nothing.
     *
     * @return true when a field's text is no number it takes
     */
    public boolean typingIsWrong() {
        return rows.values().stream()
                .anyMatch(row -> row.input() instanceof NumberField number && number.problem().isPresent());
    }

    /**
     * Whether a field shows a problem now.
     *
     * @param setting the setting whose field it is
     * @return true when it has a row here that shows one
     */
    public boolean shows(ConfigSetting setting) {
        var row = rows.get(setting);
        return row != null && row.hasProblem();
    }

    /** Takes every problem away. */
    public void clear() {
        rows.values().forEach(FieldRow::clearProblem);
    }
}

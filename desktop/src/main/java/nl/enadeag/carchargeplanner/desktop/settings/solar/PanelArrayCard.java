package nl.enadeag.carchargeplanner.desktop.settings.solar;

import nl.enadeag.carchargeplanner.desktop.settings.field.ChoiceInput;
import nl.enadeag.carchargeplanner.desktop.settings.field.FieldRow;
import nl.enadeag.carchargeplanner.desktop.settings.field.Input;
import nl.enadeag.carchargeplanner.desktop.settings.field.NumberInput;
import nl.enadeag.carchargeplanner.desktop.settings.field.TextInput;
import nl.enadeag.carchargeplanner.desktop.settings.field.WholeNumberInput;
import nl.enadeag.carchargeplanner.desktop.settings.form.ChoiceNames;
import nl.enadeag.carchargeplanner.desktop.settings.form.NumberRange;
import nl.enadeag.carchargeplanner.desktop.settings.form.PanelArrayEntry;
import nl.enadeag.carchargeplanner.desktop.settings.form.TypicalTags;
import nl.enadeag.carchargeplanner.desktop.settings.page.CardView;
import nl.enadeag.carchargeplanner.desktop.settings.page.ConfigPart;
import nl.enadeag.carchargeplanner.desktop.settings.page.Fold;
import nl.enadeag.carchargeplanner.desktop.settings.page.ProblemRows;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.desktop.theme.Labels;
import nl.enadeag.carchargeplanner.engine.config.CompassPoint;
import nl.enadeag.carchargeplanner.engine.config.PanelArray;
import nl.enadeag.carchargeplanner.engine.config.PanelSize;
import nl.enadeag.carchargeplanner.engine.config.TiltChoice;
import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;

/**
 * One panel array: its number of panels, direction, tilt, year placed and power per panel, the peak power they give
 * together, and folded under "More settings" its losses and its name. An array stored by its peak power alone keeps it
 * until the owner enters the panels; a tilt or direction stored in degrees that are not among the choices shows as the
 * nearest choice and stays as stored until the owner chooses.
 */
final class PanelArrayCard {

    private final Messages messages;
    private final Supplier<Year> thisYear;
    private final TextInput name = new TextInput();
    private final WholeNumberInput count = new WholeNumberInput("");
    private final ChoiceInput<CompassPoint> direction;
    private final ChoiceInput<TiltChoice> tilt;
    private final WholeNumberInput year = new WholeNumberInput("");
    private final WholeNumberInput power = new WholeNumberInput("Wp");
    private final NumberInput losses;
    private final Label nowSet = Labels.paragraph("", "note");
    private final Label together = Labels.paragraph("", "field-label");
    private final FieldRow powerRow;
    private final Fold more;
    private final ProblemRows problems;
    private final TypicalTags typical;
    private final Button remove;
    private final List<FieldRow> rows = new ArrayList<>();
    private final CardView card;
    private OptionalDouble storedTilt = OptionalDouble.empty();
    private OptionalDouble storedDirection = OptionalDouble.empty();
    /** The stored peak power while the array is kept as one; null for an array of panels or a new one. */
    private PanelSize.Peak kept;

    /**
     * Makes the card, empty until {@link #load(PanelArray, Set)} or {@link #loadNew()}.
     *
     * @param remove what the card's Remove button does: take this card's array away; given this card
     * @param thisYear the current year, which the year placed may not be after
     */
    PanelArrayCard(Consumer<PanelArrayCard> remove, Supplier<Year> thisYear, Messages messages) {
        this.messages = messages;
        this.thisYear = thisYear;
        var names = new ChoiceNames(messages);
        direction = new ChoiceInput<>(List.of(CompassPoint.values()), names::direction);
        tilt = new ChoiceInput<>(List.of(TiltChoice.values()), names::tilt);
        losses = new NumberInput(new NumberRange(0, 99, 1, 0), "%", messages);
        problems = new ProblemRows(messages);
        typical = new TypicalTags(messages.text(TextKey.TYPICAL_TAG));
        this.remove = new Button(messages.text(TextKey.PANELS_REMOVE));
        this.remove.setOnAction(_ -> remove.accept(this));
        this.remove.setMinWidth(Region.USE_PREF_SIZE);
        // with larger text the button wraps below rather than being cut short
        var buttons = new FlowPane(this.remove);
        buttons.getStyleClass().add("card-buttons");
        powerRow = row(ConfigSetting.PANEL_POWER, TextKey.FIELD_PANEL_POWER, power);
        more = new Fold(messages.text(TextKey.MORE_SETTINGS),
                row(ConfigSetting.PANEL_LOSSES, TextKey.FIELD_PANEL_LOSSES, TextKey.FIELD_PANEL_LOSSES_HINT, losses),
                listed(FieldRow.of(messages, TextKey.FIELD_PANEL_NAME, TextKey.FIELD_PANEL_NAME_HINT, name)));
        card = new CardView("", messages.text(TextKey.CARD_PANEL_ARRAY_HINT), nowSet,
                row(ConfigSetting.PANEL_COUNT, TextKey.FIELD_PANEL_COUNT, count),
                row(ConfigSetting.PANEL_DIRECTION, TextKey.FIELD_PANEL_DIRECTION, direction),
                row(ConfigSetting.PANEL_TILT, TextKey.FIELD_PANEL_TILT, tilt),
                row(ConfigSetting.PANEL_YEAR, TextKey.FIELD_PANEL_YEAR, year),
                powerRow, together, more, buttons);
        // the count stands for the stored peak power, so entering one ends a typical size too
        typical.follow(ConfigSetting.PANEL_SIZE, _ -> { });
        count.onChange(() -> typical.edited(ConfigSetting.PANEL_SIZE));
        List.of(count, year, power).forEach(input -> input.onChange(this::showFigures));
    }

    void load(PanelArray array, Set<ConfigSetting> typicalValues) {
        var shown = PanelArrayEntry.of(array);
        storedTilt = shown.tiltDeg();
        storedDirection = shown.azimuthDeg();
        kept = shown.kept().orElse(null);
        typical.load(typicalValues, () -> {
            name.load(shown.name());
            count.load(shown.count());
            year.load(shown.yearPlaced());
            power.load(shown.wattPeak());
            direction.load(CompassPoint.nearest(array.azimuthDeg()));
            tilt.load(TiltChoice.nearest(array.tiltDeg()));
            losses.load(shown.lossesPct());
        });
        problems.clear();
        showFigures();
    }

    /** Shows a new array: nothing entered or chosen, and the typical losses tagged as such. */
    void loadNew() {
        var shown = PanelArrayEntry.empty();
        storedTilt = OptionalDouble.empty();
        storedDirection = OptionalDouble.empty();
        kept = null;
        typical.load(Set.of(ConfigSetting.PANEL_LOSSES), () -> {
            name.load("");
            count.load("");
            year.load("");
            power.load("");
            direction.loadNone(messages.text(TextKey.CHOOSE));
            tilt.loadNone(messages.text(TextKey.CHOOSE));
            losses.load(shown.lossesPct());
        });
        problems.clear();
        showFigures();
    }

    /**
     * Gives the card its place among the arrays, for its title; with one array there is nothing to remove. With more
     * than one, a screen reader reads each field and button with the array's title, so the arrays' twins differ.
     *
     * @param index the array's place, from 0
     * @param count how many arrays there are, at least 1
     */
    void placeAmong(int index, int count) {
        var title = messages.text(TextKey.CARD_PANEL_ARRAY, index + 1);
        card.setTitle(title);
        remove.setVisible(count > 1);
        remove.setManaged(count > 1);
        // with more than one array, a screen reader tells their fields and buttons apart by the array's number
        for (var row : rows) {
            row.setSpokenName(count > 1 ? messages.text(TextKey.IN_GROUP, row.name(), title) : row.name());
        }
        remove.setAccessibleText(count > 1 ? messages.text(TextKey.IN_GROUP, remove.getText(), title) : null);
        more.setSpokenName(count > 1 ? messages.text(TextKey.IN_GROUP, more.name(), title) : more.name());
    }

    /**
     * The array as the card shows it, after its records checked it; only reads, as {@link ConfigPart#read} does.
     *
     * @return the array, or empty when a value is refused or missing
     */
    Optional<PanelArray> read() {
        return entry().check(thisYear.get(), messages) instanceof PanelArrayEntry.Checked.Accepted(var array)
                ? Optional.of(array) : Optional.empty();
    }

    /**
     * Shows the problem of a refused or missing value at its field; a problem shown before is taken away first. The
     * fields under "More settings" never have one: the losses field keeps its value in range and the name takes any.
     *
     * @return true when a value is refused or missing
     */
    boolean showProblems() {
        problems.clear();
        losses.commitTyped();
        if (entry().check(thisYear.get(), messages) instanceof PanelArrayEntry.Checked.Refused(var setting,
                var problem)) {
            problems.show(setting, problem);
            return true;
        }
        return false;
    }

    Set<ConfigSetting> typical() {
        return typical.tagged();
    }

    /** Showing the nearest choice for stored degrees is no change: only a choice of the owner's is. */
    boolean isChanged() {
        return typical.isChanged() || inputs().stream().anyMatch(Input::isChanged);
    }

    /**
     * @param action what to run, on the JavaFX thread
     */
    void onChange(Runnable action) {
        inputs().forEach(input -> input.onChange(action));
    }

    /**
     * The card itself.
     *
     * @return the same card every time
     */
    CardView view() {
        return card;
    }

    private PanelArrayEntry entry() {
        var tiltDeg = tilt.isChanged() && tilt.value() != null ? OptionalDouble.of(tilt.value().degrees()) : storedTilt;
        var azimuthDeg = direction.isChanged() && direction.value() != null
                ? OptionalDouble.of(direction.value().azimuthDeg()) : storedDirection;
        return new PanelArrayEntry(name.value(), count.value(), year.value(), power.value(), tiltDeg, azimuthDeg,
                losses.value(), Optional.ofNullable(kept));
    }

    /** The note on a stored peak power, the estimate shown in the empty power field, and the sum below. */
    private void showFigures() {
        var entry = entry();
        var keeps = entry.keepsThePeakPower();
        nowSet.setText(kept == null ? ""
                : messages.text(TextKey.PANELS_NOW_SET, messages.number(kept.kwp(), decimalsOf(kept.kwp()))));
        nowSet.setVisible(keeps);
        nowSet.setManaged(keeps);
        var typicalPower = entry.typicalWattPeak(thisYear.get());
        power.showWhileEmpty(typicalPower.isPresent() ? String.valueOf(typicalPower.getAsInt()) : "");
        powerRow.setHint(typicalPower.isPresent() && power.value().isEmpty()
                ? messages.text(TextKey.FIELD_PANEL_POWER_HINT_TYPICAL, String.valueOf(typicalPower.getAsInt()),
                        year.value())
                : messages.text(TextKey.FIELD_PANEL_POWER_HINT));
        var sum = entry.together(thisYear.get());
        together.setText(sum.isPresent() ? messages.text(TextKey.PANELS_TOGETHER, messages.number(sum.getAsDouble(), 1))
                : "");
        together.setVisible(sum.isPresent());
        together.setManaged(sum.isPresent());
    }

    /** One decimal, as the sum shows it, unless the stored peak power has a second one. */
    private static int decimalsOf(double kwp) {
        return Math.round(kwp * 10) / 10.0 == kwp ? 1 : 2;
    }

    private List<Input> inputs() {
        return List.of(count, direction, tilt, year, power, losses, name);
    }

    private FieldRow listed(FieldRow row) {
        rows.add(row);
        return row;
    }

    private FieldRow row(ConfigSetting setting, TextKey label, Input input) {
        return tagged(setting, listed(problems.add(setting, new FieldRow(messages.text(label), "", input))), input);
    }

    private FieldRow row(ConfigSetting setting, TextKey label, TextKey hint, Input input) {
        return tagged(setting, listed(problems.add(setting, FieldRow.of(messages, label, hint, input))), input);
    }

    private FieldRow tagged(ConfigSetting setting, FieldRow row, Input input) {
        typical.follow(setting, row::showTag);
        input.onChange(() -> typical.edited(setting));
        return row;
    }
}

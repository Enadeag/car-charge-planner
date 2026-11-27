package nl.enadeag.carchargeplanner.desktop.settings.car;

import nl.enadeag.carchargeplanner.desktop.settings.field.FieldRow;
import nl.enadeag.carchargeplanner.desktop.settings.field.NumberInput;
import nl.enadeag.carchargeplanner.desktop.settings.form.Limit;
import nl.enadeag.carchargeplanner.desktop.settings.form.NumberRange;
import nl.enadeag.carchargeplanner.desktop.settings.form.TypicalMarks;
import nl.enadeag.carchargeplanner.desktop.settings.page.ConfigPart;
import nl.enadeag.carchargeplanner.desktop.settings.page.ProblemRows;
import nl.enadeag.carchargeplanner.desktop.settings.page.TypicalButton;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.config.EngineConfig;
import nl.enadeag.carchargeplanner.engine.config.House;
import nl.enadeag.carchargeplanner.engine.config.TypicalValues;
import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting;
import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.scene.Node;

/**
 * The house's own use, with a typical value on request, which the page folds under "More settings". The field asks for
 * kWh per day and refuses only what the engine refuses, a negative use; the settings keep the average in kW, which a
 * field the owner did not change gives back exactly.
 */
public final class HouseCard implements ConfigPart {

    private static final double HOURS_PER_DAY = 24;

    private final NumberInput perDay;
    private final ProblemRows problems;
    private final TypicalMarks typical;
    private final List<Node> rows;

    /**
     * Makes the part, empty until {@link #load(EngineConfig)}.
     *
     * @param messages the texts, in the owner's language
     */
    public HouseCard(Messages messages) {
        perDay = new NumberInput(NumberRange.of(Limit.of(ConfigSetting.HOUSE_BASELOAD), 1, 1),
                messages.text(TextKey.UNIT_KWH_PER_DAY), messages);
        problems = new ProblemRows(messages);
        typical = new TypicalMarks();
        var row = problems.add(ConfigSetting.HOUSE_BASELOAD,
                FieldRow.of(messages, TextKey.FIELD_HOUSEHOLD_USE, TextKey.FIELD_HOUSEHOLD_USE_HINT, perDay));
        typical.follow(ConfigSetting.HOUSE_BASELOAD);
        perDay.onChange(() -> typical.edited(ConfigSetting.HOUSE_BASELOAD));
        // named by its field, as the car's button is by its own
        rows = List.of(row,
                TypicalButton.line(this::useTypical, messages));
    }

    /**
     * What the page folds under "More settings" of the house: the household use's row and its typical value's
     * button.
     *
     * @return the same rows every time
     */
    public List<Node> rows() {
        return rows;
    }

    @Override
    public void load(EngineConfig config) {
        var storedKw = config.system().house().baseloadKw();
        typical.load(config.typical().system(), () -> perDay.load(storedKw * HOURS_PER_DAY));
        problems.clear();
    }

    @Override
    public Optional<EngineConfig> read(EngineConfig config) {
        if (!isChanged()) {
            // the stored kW as it is: kWh per day divided by 24 may differ from it in the last digit
            return Optional.of(config);
        }
        if (problems.typingIsWrong()) {
            return Optional.empty();
        }
        var system = config.system();
        return house(_ -> { }).map(house -> config.withSystem(system.withHouseAndCar(house, system.car()),
                new TypicalValues(typical.appliedTo(config.typical().system()), config.typical().panels())));
    }

    /** The typed text is confirmed first, so the field shows what is checked and saved. */
    @Override
    public boolean showProblems() {
        problems.clear();
        perDay.commitTyped();
        if (!isChanged()) {
            return false;
        }
        return problems.showTyping() || house(problems::show).isEmpty();
    }

    @Override
    public void onChange(Runnable action) {
        perDay.onChange(action);
    }

    @Override
    public boolean isChanged() {
        return typical.isChanged() || perDay.isChanged();
    }

    /** The house as entered, after its record checked it; a refused value goes to {@code refused}. */
    private Optional<House> house(Consumer<InvalidSetting> refused) {
        try {
            return Optional.of(new House(perDay.value() / HOURS_PER_DAY));
        } catch (InvalidSetting e) {
            refused.accept(e);
            return Optional.empty();
        }
    }

    private void useTypical() {
        typical.useTypical(() -> perDay.set(House.TYPICAL.baseloadKw() * HOURS_PER_DAY));
    }
}

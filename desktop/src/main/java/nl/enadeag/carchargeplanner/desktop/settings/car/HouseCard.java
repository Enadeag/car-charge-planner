package nl.enadeag.carchargeplanner.desktop.settings.car;

import nl.enadeag.carchargeplanner.desktop.settings.field.FieldRow;
import nl.enadeag.carchargeplanner.desktop.settings.field.NumberInput;
import nl.enadeag.carchargeplanner.desktop.settings.form.NumberRange;
import nl.enadeag.carchargeplanner.desktop.settings.form.TypicalTags;
import nl.enadeag.carchargeplanner.desktop.settings.page.ConfigPart;
import nl.enadeag.carchargeplanner.desktop.settings.page.Fold;
import nl.enadeag.carchargeplanner.desktop.settings.page.ProblemRows;
import nl.enadeag.carchargeplanner.desktop.settings.page.TypicalButton;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.config.EngineConfig;
import nl.enadeag.carchargeplanner.engine.config.House;
import nl.enadeag.carchargeplanner.engine.config.TypicalValues;
import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting;
import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.scene.layout.VBox;

/**
 * The house's own use, with a typical value on request, folded under "More settings". The field asks for kWh per day,
 * from 0 to 480; the settings keep the average in kW, which a field the owner did not change gives back exactly. The
 * fold is closed each time the card loads; a problem in the field opens it and holds it open until it is gone.
 */
public final class HouseCard implements ConfigPart {

    private static final double HOURS_PER_DAY = 24;

    private final NumberInput perDay;
    private final ProblemRows problems;
    private final TypicalTags typical;
    private final Fold fold;
    private final VBox card;
    private double storedKw = House.TYPICAL.baseloadKw();

    /**
     * Makes the card, empty until {@link #load(EngineConfig)}.
     *
     * @param messages the texts, in the owner's language
     */
    public HouseCard(Messages messages) {
        perDay = new NumberInput(new NumberRange(0, 480, 1, 1), messages.text(TextKey.UNIT_KWH_PER_DAY), messages);
        problems = new ProblemRows(messages);
        typical = new TypicalTags(messages.text(TextKey.TYPICAL_TAG));
        var row = problems.add(ConfigSetting.HOUSE_BASELOAD,
                FieldRow.of(messages, TextKey.FIELD_HOUSEHOLD_USE, TextKey.FIELD_HOUSEHOLD_USE_HINT, perDay));
        typical.follow(ConfigSetting.HOUSE_BASELOAD, row::showTag);
        perDay.onChange(() -> typical.edited(ConfigSetting.HOUSE_BASELOAD));
        // a screen reader tells this button from the car's by the field it is for
        fold = new Fold(messages.text(TextKey.MORE_SETTINGS), row,
                TypicalButton.line(this::useTypical, messages.text(TextKey.FIELD_HOUSEHOLD_USE), messages));
        card = new VBox(fold);
        card.getStyleClass().addAll("card", "settings-card");
    }

    /**
     * The card itself: the fold and nothing else.
     *
     * @return the same card every time
     */
    public VBox view() {
        return card;
    }

    @Override
    public void load(EngineConfig config) {
        storedKw = config.system().house().baseloadKw();
        typical.load(config.typical().system(), () -> perDay.load(storedKw * HOURS_PER_DAY));
        problems.clear();
        fold.holdOpen(false);
        fold.setOpen(false);
    }

    @Override
    public Optional<EngineConfig> read(EngineConfig config) {
        if (!isChanged()) {
            return Optional.of(config);
        }
        return house(_ -> { }).map(house -> {
            var system = config.system();
            return config.withSystem(system.withHouseAndCar(house, system.car()),
                    new TypicalValues(typical.appliedTo(config.typical().system()), config.typical().panels()));
        });
    }

    @Override
    public boolean showProblems() {
        problems.clear();
        if (!isChanged()) {
            fold.holdOpen(false);
            return false;
        }
        perDay.commitTyped();
        var refused = house(problems::show).isEmpty();
        fold.holdOpen(refused);
        return refused;
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
        // kWh per day back to kW only for a changed field, so the stored value is never rounded on its way back
        var kw = perDay.isChanged() ? perDay.value() / HOURS_PER_DAY : storedKw;
        try {
            return Optional.of(new House(kw));
        } catch (InvalidSetting e) {
            refused.accept(e);
            return Optional.empty();
        }
    }

    private void useTypical() {
        typical.useTypical(() -> perDay.set(House.TYPICAL.baseloadKw() * HOURS_PER_DAY));
    }
}

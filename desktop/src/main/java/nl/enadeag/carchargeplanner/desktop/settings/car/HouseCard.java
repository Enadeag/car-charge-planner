package nl.enadeag.carchargeplanner.desktop.settings.car;

import nl.enadeag.carchargeplanner.desktop.settings.field.FieldRow;
import nl.enadeag.carchargeplanner.desktop.settings.field.NumberInput;
import nl.enadeag.carchargeplanner.desktop.settings.form.NumberRange;
import nl.enadeag.carchargeplanner.desktop.settings.form.TypicalTags;
import nl.enadeag.carchargeplanner.desktop.settings.page.ConfigPart;
import nl.enadeag.carchargeplanner.desktop.settings.page.Fold;
import nl.enadeag.carchargeplanner.desktop.settings.page.TypicalButton;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.config.EngineConfig;
import nl.enadeag.carchargeplanner.engine.config.House;
import nl.enadeag.carchargeplanner.engine.config.TypicalValues;
import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting;
import java.util.Optional;
import javafx.scene.layout.VBox;

/**
 * The house's own use, with a typical value on request, folded under "More settings". The field asks for kWh per day,
 * from 0 to 480; the settings keep the average in kW, which a field the owner did not change gives back exactly. The
 * fold is closed each time the card loads. The field keeps every value it holds within that range, so the card never
 * has a problem to show.
 */
public final class HouseCard implements ConfigPart {

    private static final double HOURS_PER_DAY = 24;

    private final NumberInput perDay;
    private final TypicalTags typical;
    private final Fold fold;
    private final VBox card;

    /**
     * Makes the card, empty until {@link #load(EngineConfig)}.
     *
     * @param messages the texts, in the owner's language
     */
    public HouseCard(Messages messages) {
        perDay = new NumberInput(new NumberRange(0, 480, 1, 1), messages.text(TextKey.UNIT_KWH_PER_DAY), messages);
        typical = new TypicalTags(messages.text(TextKey.TYPICAL_TAG));
        var row = FieldRow.of(messages, TextKey.FIELD_HOUSEHOLD_USE, TextKey.FIELD_HOUSEHOLD_USE_HINT, perDay);
        typical.follow(ConfigSetting.HOUSE_BASELOAD, row::showTag);
        perDay.onChange(() -> typical.edited(ConfigSetting.HOUSE_BASELOAD));
        // named by its field, as the car's button is by its own
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
        var storedKw = config.system().house().baseloadKw();
        typical.load(config.typical().system(), () -> perDay.load(storedKw * HOURS_PER_DAY));
        fold.setOpen(false);
    }

    @Override
    public Optional<EngineConfig> read(EngineConfig config) {
        if (!isChanged()) {
            // the stored kW as it is: kWh per day divided by 24 may differ from it in the last digit
            return Optional.of(config);
        }
        var system = config.system();
        var house = new House(perDay.value() / HOURS_PER_DAY);
        return Optional.of(config.withSystem(system.withHouseAndCar(house, system.car()),
                new TypicalValues(typical.appliedTo(config.typical().system()), config.typical().panels())));
    }

    /** Nothing is refused, see the class; the typed text is confirmed so the field shows what is saved. */
    @Override
    public boolean showProblems() {
        perDay.commitTyped();
        return false;
    }

    @Override
    public void onChange(Runnable action) {
        perDay.onChange(action);
    }

    @Override
    public boolean isChanged() {
        return typical.isChanged() || perDay.isChanged();
    }

    private void useTypical() {
        typical.useTypical(() -> perDay.set(House.TYPICAL.baseloadKw() * HOURS_PER_DAY));
    }
}

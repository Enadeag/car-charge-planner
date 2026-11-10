package nl.enadeag.carchargeplanner.desktop.settings.car;

import nl.enadeag.carchargeplanner.desktop.settings.field.FieldRow;
import nl.enadeag.carchargeplanner.desktop.settings.field.NumberInput;
import nl.enadeag.carchargeplanner.desktop.settings.form.NumberRange;
import nl.enadeag.carchargeplanner.desktop.settings.form.TypicalTags;
import nl.enadeag.carchargeplanner.desktop.settings.page.CardView;
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
import java.util.Optional;
import java.util.function.Consumer;

/** The house's own use of power, with a typical value on request. */
public final class HouseCard implements ConfigPart {

    private final NumberInput baseload;
    private final ProblemRows problems;
    private final TypicalTags typical;
    private final CardView card;

    /**
     * Makes the card, empty until {@link #load(EngineConfig)}.
     *
     * @param messages the texts, in the owner's language
     */
    public HouseCard(Messages messages) {
        baseload = new NumberInput(new NumberRange(0, 20, 0.05, 2), "kW", messages);
        problems = new ProblemRows(messages);
        typical = new TypicalTags(messages.text(TextKey.TYPICAL_TAG));
        var row = problems.add(ConfigSetting.HOUSE_BASELOAD,
                FieldRow.of(messages, TextKey.FIELD_BASELOAD, TextKey.FIELD_BASELOAD_HINT, baseload));
        typical.follow(ConfigSetting.HOUSE_BASELOAD, row::showTag);
        baseload.onChange(() -> typical.edited(ConfigSetting.HOUSE_BASELOAD));
        card = new CardView(messages.text(TextKey.CARD_HOUSE), messages.text(TextKey.CARD_HOUSE_HINT), row,
                TypicalButton.line(this::useTypical, messages.text(TextKey.CARD_HOUSE), messages));
    }

    /**
     * The card itself.
     *
     * @return the same card every time
     */
    public CardView view() {
        return card;
    }

    @Override
    public void load(EngineConfig config) {
        typical.load(config.typical().system(), () -> baseload.load(config.system().house().baseloadKw()));
        problems.clear();
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
            return false;
        }
        baseload.commitTyped();
        return house(problems::show).isEmpty();
    }

    @Override
    public void onChange(Runnable action) {
        baseload.onChange(action);
    }

    @Override
    public boolean isChanged() {
        return typical.isChanged() || baseload.isChanged();
    }

    /** The house as entered, after its record checked it; a refused value goes to {@code refused}. */
    private Optional<House> house(Consumer<InvalidSetting> refused) {
        try {
            return Optional.of(new House(baseload.value()));
        } catch (InvalidSetting e) {
            refused.accept(e);
            return Optional.empty();
        }
    }

    private void useTypical() {
        typical.useTypical(() -> baseload.set(House.TYPICAL.baseloadKw()));
    }
}

package nl.enadeag.carchargeplanner.desktop.settings;

import nl.enadeag.carchargeplanner.desktop.settings.form.NumberRange;
import nl.enadeag.carchargeplanner.desktop.settings.form.TypicalMarks;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.config.EngineConfig;
import nl.enadeag.carchargeplanner.engine.config.House;
import nl.enadeag.carchargeplanner.engine.config.TypicalValues;
import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting;
import nl.enadeag.carchargeplanner.engine.setting.Setting;
import java.util.Optional;

/** The house's own use of power, with a typical value on request. */
final class HouseCard implements ConfigPart {

    private final NumberField baseload;
    private final ProblemRows problems;
    private final TypicalMarks typical;
    private final Card card;

    /**
     * Makes the card, empty until {@link #load(EngineConfig)}.
     *
     * @param messages the texts, in the owner's language
     */
    HouseCard(Messages messages) {
        baseload = new NumberField(new NumberRange(0, 20, 0.05, 2), "kW", messages);
        problems = new ProblemRows(messages);
        typical = new TypicalMarks(messages.text(TextKey.TYPICAL_TAG));
        var row = problems.add(Setting.HOUSE_BASELOAD,
                FieldRow.of(messages, TextKey.FIELD_BASELOAD, TextKey.FIELD_BASELOAD_HINT, baseload));
        typical.follow(Setting.HOUSE_BASELOAD, row::showTag);
        baseload.onChange(() -> typical.edited(Setting.HOUSE_BASELOAD));
        card = new Card(messages.text(TextKey.CARD_HOUSE), messages.text(TextKey.CARD_HOUSE_HINT), row,
                TypicalButton.line(this::useTypical, messages.text(TextKey.CARD_HOUSE), messages));
    }

    /**
     * The card itself.
     *
     * @return the same card every time
     */
    Card view() {
        return card;
    }

    @Override
    public void load(EngineConfig config) {
        typical.load(config.typical().system(), () -> baseload.load(config.system().house().baseloadKw()));
        problems.clear();
    }

    @Override
    public Optional<EngineConfig> applyTo(EngineConfig config) {
        problems.clear();
        if (!isChanged()) {
            return Optional.of(config);
        }
        try {
            var house = new House(baseload.value());
            var system = config.system();
            return Optional.of(config.withSystem(system.withHouseAndCar(house, system.car()),
                    new TypicalValues(typical.appliedTo(config.typical().system()), config.typical().panels())));
        } catch (InvalidSetting e) {
            problems.show(e);
            return Optional.empty();
        }
    }

    @Override
    public void onChange(Runnable action) {
        baseload.onChange(action);
    }

    @Override
    public boolean isChanged() {
        return typical.isChanged() || baseload.isChanged();
    }

    private void useTypical() {
        typical.useTypical(() -> baseload.set(House.TYPICAL.baseloadKw()));
    }
}

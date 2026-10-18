package io.github.enadeag.carchargeplanner.desktop.settings;

import io.github.enadeag.carchargeplanner.desktop.text.Messages;
import io.github.enadeag.carchargeplanner.desktop.text.TextKey;
import io.github.enadeag.carchargeplanner.engine.config.EngineConfig;
import io.github.enadeag.carchargeplanner.engine.config.House;
import io.github.enadeag.carchargeplanner.engine.config.TypicalValues;
import io.github.enadeag.carchargeplanner.engine.setting.InvalidSetting;
import io.github.enadeag.carchargeplanner.engine.setting.Setting;
import java.util.HashSet;
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
        baseload = new NumberField(new NumberField.Range(0, 20, 0.05, 2), "kW", messages);
        problems = new ProblemRows(messages);
        typical = new TypicalMarks(messages.text(TextKey.TYPICAL_TAG));
        var row = problems.add(Setting.HOUSE_BASELOAD,
                FieldRow.of(messages, TextKey.FIELD_BASELOAD, TextKey.FIELD_BASELOAD_HINT, baseload));
        typical.follow(Setting.HOUSE_BASELOAD, row, baseload);
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
            var marked = new HashSet<>(config.typical().system());
            marked.remove(Setting.HOUSE_BASELOAD);
            marked.addAll(typical.marked());
            var system = config.system();
            return Optional.of(config.withSystem(system.withHouseAndCar(house, system.car()),
                    new TypicalValues(marked, config.typical().panels())));
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

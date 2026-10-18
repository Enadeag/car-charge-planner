package io.github.enadeag.carchargeplanner.desktop.settings;

import io.github.enadeag.carchargeplanner.desktop.settings.form.ChargerEntry;
import io.github.enadeag.carchargeplanner.desktop.settings.form.NumberRange;
import io.github.enadeag.carchargeplanner.desktop.settings.form.TypicalMarks;
import io.github.enadeag.carchargeplanner.desktop.text.Messages;
import io.github.enadeag.carchargeplanner.desktop.text.TextKey;
import io.github.enadeag.carchargeplanner.engine.config.Charger;
import io.github.enadeag.carchargeplanner.engine.config.EngineConfig;
import io.github.enadeag.carchargeplanner.engine.config.Phases;
import io.github.enadeag.carchargeplanner.engine.config.TypicalValues;
import io.github.enadeag.carchargeplanner.engine.setting.InvalidSetting;
import io.github.enadeag.carchargeplanner.engine.setting.Setting;
import java.util.List;
import java.util.Optional;
import javafx.scene.layout.Region;

/** The wall charger, as far as it limits the charge power, with typical values on request. */
final class ChargerPage implements ConfigPage {

    private final Messages messages;
    private final PageView view;
    private final ChoiceField<Phases> phases;
    private final NumberField minAmps;
    private final NumberField maxAmps;
    private final NumberField volts;
    private final NumberField efficiency;
    private final ProblemRows problems;
    private final TypicalMarks typical;
    private final Card card;

    /**
     * Makes the page, empty until {@link #load(EngineConfig)}.
     *
     * @param messages the texts, in the owner's language
     */
    ChargerPage(Messages messages) {
        this.messages = messages;
        this.view = new PageView(messages.text(Category.CHARGER.title()));
        phases = new ChoiceField<>(List.of(Phases.values()),
                choice -> messages.text(choice == Phases.ONE ? TextKey.PHASES_ONE : TextKey.PHASES_THREE));
        var amps = new NumberRange(1, 80, 1, 0);
        minAmps = new NumberField(amps, "A", messages);
        maxAmps = new NumberField(amps, "A", messages);
        volts = new NumberField(new NumberRange(100, 400, 1, 0), "V", messages);
        efficiency = new NumberField(new NumberRange(1, 100, 1, 0), "%", messages);
        problems = new ProblemRows(messages);
        typical = new TypicalMarks(messages.text(TextKey.TYPICAL_TAG));
        card = new Card(messages.text(TextKey.CARD_CHARGER), messages.text(TextKey.CARD_CHARGER_HINT),
                row(Setting.CHARGER_PHASES, TextKey.FIELD_PHASES, TextKey.FIELD_PHASES_HINT, phases),
                row(Setting.CHARGER_MIN_CURRENT, TextKey.FIELD_MIN_CURRENT, TextKey.FIELD_MIN_CURRENT_HINT, minAmps),
                row(Setting.CHARGER_MAX_CURRENT, TextKey.FIELD_MAX_CURRENT, TextKey.FIELD_MAX_CURRENT_HINT, maxAmps),
                row(Setting.CHARGER_VOLTAGE, TextKey.FIELD_VOLTAGE, TextKey.FIELD_VOLTAGE_HINT, volts),
                row(Setting.CHARGER_EFFICIENCY, TextKey.FIELD_EFFICIENCY, TextKey.FIELD_EFFICIENCY_HINT, efficiency),
                TypicalButton.line(this::useTypical, messages.text(TextKey.CARD_CHARGER), messages));
    }

    @Override
    public Category category() {
        return Category.CHARGER;
    }

    @Override
    public Region view() {
        return view;
    }

    @Override
    public void onChange(Runnable action) {
        inputs().forEach(input -> input.onChange(action));
    }

    @Override
    public boolean isChanged() {
        return typical.isChanged() || inputs().stream().anyMatch(Input::isChanged);
    }

    @Override
    public void load(EngineConfig config) {
        var shown = ChargerEntry.of(config.system().charger());
        typical.load(config.typical().system(), () -> {
            phases.load(shown.phases());
            minAmps.load(shown.minAmps());
            maxAmps.load(shown.maxAmps());
            volts.load(shown.volts());
            efficiency.load(shown.efficiencyPct());
        });
        problems.clear();
        view.show(card);
    }

    @Override
    public void showUnavailable(String reason) {
        view.show(NoConfigCard.card(reason, messages));
    }

    @Override
    public Optional<EngineConfig> applyTo(EngineConfig config) {
        problems.clear();
        if (!isChanged()) {
            return Optional.of(config);
        }
        try {
            var charger = new ChargerEntry(phases.value(), minAmps.value(), maxAmps.value(), volts.value(),
                    efficiency.value()).charger();
            return Optional.of(config.withSystem(config.system().withCharger(charger),
                    new TypicalValues(typical.appliedTo(config.typical().system()), config.typical().panels())));
        } catch (InvalidSetting e) {
            problems.show(e);
            return Optional.empty();
        }
    }

    private void useTypical() {
        var common = ChargerEntry.of(Charger.TYPICAL);
        typical.useTypical(() -> {
            phases.set(common.phases());
            minAmps.set(common.minAmps());
            maxAmps.set(common.maxAmps());
            volts.set(common.volts());
            efficiency.set(common.efficiencyPct());
        });
    }

    private List<Input> inputs() {
        return List.of(phases, minAmps, maxAmps, volts, efficiency);
    }

    private FieldRow row(Setting setting, TextKey name, TextKey hint, Input input) {
        var row = problems.add(setting, FieldRow.of(messages, name, hint, input));
        typical.follow(setting, row::showTag);
        input.onChange(() -> typical.edited(setting));
        return row;
    }
}

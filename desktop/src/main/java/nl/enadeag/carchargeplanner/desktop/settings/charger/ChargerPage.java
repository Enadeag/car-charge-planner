package nl.enadeag.carchargeplanner.desktop.settings.charger;

import nl.enadeag.carchargeplanner.desktop.settings.field.ChoiceInput;
import nl.enadeag.carchargeplanner.desktop.settings.field.FieldRow;
import nl.enadeag.carchargeplanner.desktop.settings.field.Input;
import nl.enadeag.carchargeplanner.desktop.settings.field.NumberInput;
import nl.enadeag.carchargeplanner.desktop.settings.form.ChargerEntry;
import nl.enadeag.carchargeplanner.desktop.settings.form.ChoiceNames;
import nl.enadeag.carchargeplanner.desktop.settings.form.NumberRange;
import nl.enadeag.carchargeplanner.desktop.settings.form.TypicalMarks;
import nl.enadeag.carchargeplanner.desktop.settings.page.CardView;
import nl.enadeag.carchargeplanner.desktop.settings.page.Category;
import nl.enadeag.carchargeplanner.desktop.settings.page.ConfigPage;
import nl.enadeag.carchargeplanner.desktop.settings.page.Fold;
import nl.enadeag.carchargeplanner.desktop.settings.page.NoConfigCard;
import nl.enadeag.carchargeplanner.desktop.settings.page.PageView;
import nl.enadeag.carchargeplanner.desktop.settings.page.ProblemRows;
import nl.enadeag.carchargeplanner.desktop.settings.page.TypicalButton;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.config.ChargePowerChoice;
import nl.enadeag.carchargeplanner.engine.config.Charger;
import nl.enadeag.carchargeplanner.engine.config.EngineConfig;
import nl.enadeag.carchargeplanner.engine.config.HomeChargePower;
import nl.enadeag.carchargeplanner.engine.config.Phases;
import nl.enadeag.carchargeplanner.engine.config.TypicalValues;
import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting;
import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import javafx.scene.layout.Region;

/**
 * The wall charger, as far as it limits the charge power: the charge power at home, chosen from a few or "Other", and
 * folded under "More settings" the phases, the currents, the voltage and the efficiency, with typical values on
 * request for the last three. The power shows the choice the values stand for, else "Other"; choosing a power sets
 * the phases, the highest current and 230 V, and choosing "Other" opens the fold and changes no value. The fold is
 * closed on a choice and open on "Other" as the page loads, and opens while one of its fields shows a problem.
 */
public final class ChargerPage implements ConfigPage {

    private static final Set<ConfigSetting> FILLED_BY_TYPICAL = Set.of(ConfigSetting.CHARGER_MIN_CURRENT,
            ConfigSetting.CHARGER_VOLTAGE, ConfigSetting.CHARGER_EFFICIENCY);

    private final Messages messages;
    private final boolean setUp;
    private final PageView view;
    private final ChoiceInput<ChargePowerChoice> power;
    private final FieldRow powerRow;
    private final ChoiceInput<Phases> phases;
    private final NumberInput minAmps;
    private final NumberInput maxAmps;
    private final NumberInput volts;
    private final NumberInput efficiency;
    private final ProblemRows problems;
    private final TypicalMarks typical;
    private final Fold more;
    private final CardView card;
    private double loadedEfficiency;
    /** True while the page puts values in its fields itself, so the power and the values do not follow each other. */
    private boolean following;

    /**
     * Makes the page, empty until {@link #load(EngineConfig)}.
     *
     * @param setUp true in set-up, which shows a charger whose phases and highest current are typical values with no
     *     power chosen, "Choose…", and does not go on until one is
     * @param messages the texts, in the owner's language
     */
    public ChargerPage(boolean setUp, Messages messages) {
        this.messages = messages;
        this.setUp = setUp;
        this.view = new PageView(messages.text(Category.CHARGER.title()));
        var names = new ChoiceNames(messages);
        power = new ChoiceInput<>(List.of(ChargePowerChoice.values()), names::chargePower);
        phases = new ChoiceInput<>(List.of(Phases.values()), names::phases);
        var amps = new NumberRange(1, 80, 1, 0);
        minAmps = new NumberInput(amps, "A", messages);
        maxAmps = new NumberInput(amps, "A", messages);
        volts = new NumberInput(new NumberRange(100, 400, 1, 0), "V", messages);
        efficiency = new NumberInput(new NumberRange(1, 100, 1, 0), "%", messages);
        problems = new ProblemRows(messages);
        typical = new TypicalMarks();
        powerRow = FieldRow.of(messages, TextKey.FIELD_CHARGE_POWER, TextKey.FIELD_CHARGE_POWER_HINT, power);
        more = new Fold(messages.text(TextKey.MORE_SETTINGS),
                row(ConfigSetting.CHARGER_PHASES, TextKey.FIELD_PHASES, TextKey.FIELD_PHASES_HINT, phases),
                row(ConfigSetting.CHARGER_MIN_CURRENT, TextKey.FIELD_MIN_CURRENT, TextKey.FIELD_MIN_CURRENT_HINT,
                        minAmps),
                row(ConfigSetting.CHARGER_MAX_CURRENT, TextKey.FIELD_MAX_CURRENT, TextKey.FIELD_MAX_CURRENT_HINT,
                        maxAmps),
                row(ConfigSetting.CHARGER_VOLTAGE, TextKey.FIELD_VOLTAGE, TextKey.FIELD_VOLTAGE_HINT, volts),
                row(ConfigSetting.CHARGER_EFFICIENCY, TextKey.FIELD_EFFICIENCY, TextKey.FIELD_EFFICIENCY_HINT,
                        efficiency),
                TypicalButton.line(this::useTypical, messages.text(TextKey.CARD_CHARGER), messages));
        card = new CardView(messages.text(TextKey.CARD_CHARGER), messages.text(TextKey.CARD_CHARGER_HINT), powerRow,
                more);
        power.onChange(this::powerChosen);
        List.of(phases, maxAmps, volts).forEach(input -> input.onChange(this::followValues));
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
        power.onChange(action);
        inputs().forEach(input -> input.onChange(action));
    }

    /** The power is not itself stored: only the values it sets, or the typical marks it takes away, are a change. */
    @Override
    public boolean isChanged() {
        return typical.isChanged() || inputs().stream().anyMatch(Input::isChanged);
    }

    @Override
    public void load(EngineConfig config) {
        var charger = config.system().charger();
        var shown = ChargerEntry.of(charger);
        var typicalSettings = config.typical().system();
        loadedEfficiency = charger.efficiency();
        following = true;
        try {
            typical.load(typicalSettings, () -> {
                phases.load(shown.phases());
                minAmps.load(shown.minAmps());
                maxAmps.load(shown.maxAmps());
                volts.load(shown.volts());
                efficiency.load(shown.efficiencyPct());
            });
            if (setUp && typicalSettings.contains(ConfigSetting.CHARGER_PHASES)
                    && typicalSettings.contains(ConfigSetting.CHARGER_MAX_CURRENT)) {
                power.loadNone(messages.text(TextKey.CHOOSE));
            } else {
                power.load(ChargePowerChoice.of(charger));
            }
        } finally {
            following = false;
        }
        problems.clear();
        powerRow.clearProblem();
        more.holdOpen(false);
        more.setOpen(power.value() == ChargePowerChoice.OTHER);
        view.show(card);
    }

    @Override
    public void showUnavailable(String reason) {
        view.show(NoConfigCard.card(reason, messages));
    }

    @Override
    public Optional<EngineConfig> read(EngineConfig config) {
        if (power.value() == null) {
            return Optional.empty();
        }
        if (!isChanged()) {
            return Optional.of(config);
        }
        return charger(_ -> { }).map(charger -> config.withSystem(config.system().withCharger(charger),
                new TypicalValues(typical.appliedTo(config.typical().system()), config.typical().panels())));
    }

    /**
     * Shows "Choose one." at the power while none is chosen, and a value the records refuse at its field. The fields
     * keep every value in their ranges, so the one value refused is a highest current below the lowest, under the
     * fold, which then opens and stays open until the next check finds it put right.
     */
    @Override
    public boolean showProblems() {
        problems.clear();
        powerRow.clearProblem();
        more.holdOpen(false);
        var refused = power.value() == null;
        if (refused) {
            powerRow.showProblem(messages.text(TextKey.PROBLEM_NOT_CHOSEN));
        }
        if (!isChanged()) {
            return refused;
        }
        inputs().forEach(Input::commitTyped);
        return charger(problem -> {
            problems.show(problem);
            more.holdOpen(true);
        }).isEmpty() || refused;
    }

    /**
     * The charger as entered, after its record checked it; a refused value goes to {@code refused}. An efficiency the
     * owner left alone is the loaded one exactly, as a share in % divided by 100 can differ from it in the last digit.
     */
    private Optional<Charger> charger(Consumer<InvalidSetting> refused) {
        try {
            var entered = new ChargerEntry(phases.value(), minAmps.value(), maxAmps.value(), volts.value(),
                    efficiency.value()).charger();
            return Optional.of(efficiency.isChanged() ? entered : new Charger(entered.phases(), entered.minAmps(),
                    entered.maxAmps(), entered.volts(), loadedEfficiency));
        } catch (InvalidSetting e) {
            refused.accept(e);
            return Optional.empty();
        }
    }

    private void powerChosen() {
        if (following) {
            return;
        }
        powerRow.clearProblem();
        var chosen = power.value().power();
        if (chosen.isEmpty()) {
            more.setOpen(true);
            return;
        }
        var choice = chosen.get();
        following = true;
        try {
            phases.set(choice.phases());
            maxAmps.set(choice.maxAmps());
            volts.set(HomeChargePower.VOLTS);
        } finally {
            following = false;
        }
        // the phases and the current are the owner's even where they did not change; the voltage only where it did
        typical.edited(ConfigSetting.CHARGER_PHASES);
        typical.edited(ConfigSetting.CHARGER_MAX_CURRENT);
    }

    private void followValues() {
        if (following || power.value() == null) {
            return;
        }
        following = true;
        try {
            power.set(ChargePowerChoice.matching(phases.value(), (int) Math.round(maxAmps.value()), volts.value()));
        } finally {
            following = false;
        }
    }

    private void useTypical() {
        var common = ChargerEntry.of(Charger.TYPICAL);
        typical.useTypical(FILLED_BY_TYPICAL, () -> {
            minAmps.set(common.minAmps());
            volts.set(common.volts());
            efficiency.set(common.efficiencyPct());
        });
    }

    private List<Input> inputs() {
        return List.of(phases, minAmps, maxAmps, volts, efficiency);
    }

    private FieldRow row(ConfigSetting setting, TextKey name, TextKey hint, Input input) {
        var row = problems.add(setting, FieldRow.of(messages, name, hint, input));
        typical.follow(setting);
        input.onChange(() -> typical.edited(setting));
        return row;
    }
}

package io.github.enadeag.carchargeplanner.desktop.settings;

import io.github.enadeag.carchargeplanner.desktop.text.Messages;
import io.github.enadeag.carchargeplanner.desktop.text.TextKey;
import io.github.enadeag.carchargeplanner.engine.config.EngineConfig;
import io.github.enadeag.carchargeplanner.engine.config.Percent;
import io.github.enadeag.carchargeplanner.engine.config.PlanningPreferences;
import io.github.enadeag.carchargeplanner.engine.setting.InvalidSetting;
import io.github.enadeag.carchargeplanner.engine.setting.Setting;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javafx.scene.layout.Region;

/** The planning preferences: the charge levels, how cautious the plan is, and when the owner is told. */
final class PlanningPage implements ConfigPage {

    private static final NumberField.Range PERCENT = new NumberField.Range(0, 100, 5, 0);
    private static final NumberField.Range MINUTES = new NumberField.Range(1, 1440, 5, 0);

    private final Messages messages;
    private final PageView view;
    private final NumberField minimum;
    private final NumberField target;
    private final NumberField margin;
    private final NumberField importTolerance;
    private final NumberField minWindow;
    private final NumberField notifyLead;
    private final Map<Setting, FieldRow> rows = new EnumMap<>(Setting.class);
    private final Card[] cards;

    /**
     * Makes the page, empty until {@link #load(EngineConfig)}.
     *
     * @param messages the texts, in the owner's language
     */
    PlanningPage(Messages messages) {
        this.messages = messages;
        this.view = new PageView(messages.text(Category.PLANNING.title()));
        minimum = new NumberField(PERCENT, "%", messages);
        target = new NumberField(PERCENT, "%", messages);
        margin = new NumberField(new NumberField.Range(1, 100, 5, 0), "%", messages);
        importTolerance = new NumberField(new NumberField.Range(0, 100, 0.05, 2), "kWh", messages);
        minWindow = new NumberField(MINUTES, messages.text(TextKey.UNIT_MINUTES), messages);
        notifyLead = new NumberField(new NumberField.Range(0, 1440, 5, 0), messages.text(TextKey.UNIT_MINUTES), messages);
        cards = new Card[] {
            new Card(messages.text(TextKey.CARD_CHARGE_LEVELS), messages.text(TextKey.CARD_CHARGE_LEVELS_HINT),
                    row(Setting.MINIMUM_CHARGE, TextKey.FIELD_MINIMUM_CHARGE, TextKey.FIELD_MINIMUM_CHARGE_HINT, minimum),
                    row(Setting.TARGET_CHARGE, TextKey.FIELD_TARGET_CHARGE, TextKey.FIELD_TARGET_CHARGE_HINT, target)),
            new Card(messages.text(TextKey.CARD_PLAN), messages.text(TextKey.CARD_PLAN_HINT),
                    row(Setting.PLANNING_MARGIN, TextKey.FIELD_MARGIN, TextKey.FIELD_MARGIN_HINT, margin),
                    row(Setting.IMPORT_TOLERANCE, TextKey.FIELD_IMPORT_TOLERANCE, TextKey.FIELD_IMPORT_TOLERANCE_HINT,
                            importTolerance),
                    row(Setting.MINIMUM_WINDOW, TextKey.FIELD_MIN_WINDOW, TextKey.FIELD_MIN_WINDOW_HINT, minWindow)),
            new Card(messages.text(TextKey.CARD_NOTIFICATIONS), messages.text(TextKey.CARD_NOTIFICATIONS_HINT),
                    row(Setting.NOTIFY_LEAD, TextKey.FIELD_NOTIFY_LEAD, TextKey.FIELD_NOTIFY_LEAD_HINT, notifyLead))
        };
    }

    @Override
    public Category category() {
        return Category.PLANNING;
    }

    @Override
    public Region view() {
        return view;
    }

    @Override
    public void onChange(Runnable action) {
        fields().forEach(field -> field.onChange(action));
    }

    @Override
    public boolean isChanged() {
        return fields().stream().anyMatch(NumberField::isChanged);
    }

    @Override
    public void load(EngineConfig config) {
        var planning = config.planning();
        minimum.load(planning.minimumPct().value());
        target.load(planning.targetPct().value());
        margin.load(planning.margin() * 100);
        importTolerance.load(planning.importToleranceKwh());
        minWindow.load(planning.minWindow().toMinutes());
        notifyLead.load(planning.notifyLead().toMinutes());
        rows.values().forEach(FieldRow::clearProblem);
        view.show(cards);
    }

    @Override
    public void showUnavailable(String reason) {
        view.show(NoConfigCard.card(reason, messages));
    }

    @Override
    public Optional<EngineConfig> applyTo(EngineConfig config) {
        rows.values().forEach(FieldRow::clearProblem);
        if (!isChanged()) {
            return Optional.of(config);
        }
        try {
            var planning = new PlanningPreferences(new Percent(minimum.value()), new Percent(target.value()),
                    margin.value() / 100, importTolerance.value(),
                    Duration.ofMinutes(minWindow.wholeValue()), Duration.ofMinutes(notifyLead.wholeValue()));
            return Optional.of(config.withPlanning(planning));
        } catch (InvalidSetting e) {
            rows.get(e.setting()).showProblem(ProblemTexts.of(e.rule(), messages));
            return Optional.empty();
        }
    }

    private List<NumberField> fields() {
        return List.of(minimum, target, margin, importTolerance, minWindow, notifyLead);
    }

    private FieldRow row(Setting setting, TextKey name, TextKey hint, Input input) {
        var row = new FieldRow(messages.text(name), messages.text(hint), input);
        rows.put(setting, row);
        return row;
    }
}

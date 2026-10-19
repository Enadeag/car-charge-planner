package nl.enadeag.carchargeplanner.desktop.settings.planning;

import nl.enadeag.carchargeplanner.desktop.settings.field.FieldRow;
import nl.enadeag.carchargeplanner.desktop.settings.field.Input;
import nl.enadeag.carchargeplanner.desktop.settings.field.NumberField;
import nl.enadeag.carchargeplanner.desktop.settings.form.NumberRange;
import nl.enadeag.carchargeplanner.desktop.settings.form.PlanningEntry;
import nl.enadeag.carchargeplanner.desktop.settings.form.ProblemTexts;
import nl.enadeag.carchargeplanner.desktop.settings.page.Card;
import nl.enadeag.carchargeplanner.desktop.settings.page.Category;
import nl.enadeag.carchargeplanner.desktop.settings.page.ConfigPage;
import nl.enadeag.carchargeplanner.desktop.settings.page.NoConfigCard;
import nl.enadeag.carchargeplanner.desktop.settings.page.PageView;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.config.EngineConfig;
import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting;
import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javafx.scene.layout.Region;

/** The planning preferences: the charge levels, how cautious the plan is, and when the owner is told. */
public final class PlanningPage implements ConfigPage {

    private static final NumberRange PERCENT = new NumberRange(0, 100, 5, 0);
    private static final NumberRange MINUTES = new NumberRange(1, 1440, 5, 0);

    private final Messages messages;
    private final PageView view;
    private final NumberField minimum;
    private final NumberField target;
    private final NumberField margin;
    private final NumberField importTolerance;
    private final NumberField minWindow;
    private final NumberField notifyLead;
    private final Map<ConfigSetting, FieldRow> rows = new EnumMap<>(ConfigSetting.class);
    private final Card[] cards;

    /**
     * Makes the page, empty until {@link #load(EngineConfig)}.
     *
     * @param messages the texts, in the owner's language
     */
    public PlanningPage(Messages messages) {
        this.messages = messages;
        this.view = new PageView(messages.text(Category.PLANNING.title()));
        minimum = new NumberField(PERCENT, "%", messages);
        target = new NumberField(PERCENT, "%", messages);
        margin = new NumberField(new NumberRange(1, 100, 5, 0), "%", messages);
        importTolerance = new NumberField(new NumberRange(0, 100, 0.05, 2), "kWh", messages);
        minWindow = new NumberField(MINUTES, messages.text(TextKey.UNIT_MINUTES), messages);
        notifyLead = new NumberField(new NumberRange(0, 1440, 5, 0), messages.text(TextKey.UNIT_MINUTES), messages);
        cards = new Card[] {
            new Card(messages.text(TextKey.CARD_CHARGE_LEVELS), messages.text(TextKey.CARD_CHARGE_LEVELS_HINT),
                    row(ConfigSetting.MINIMUM_CHARGE, TextKey.FIELD_MINIMUM_CHARGE, TextKey.FIELD_MINIMUM_CHARGE_HINT,
                            minimum),
                    row(ConfigSetting.TARGET_CHARGE, TextKey.FIELD_TARGET_CHARGE, TextKey.FIELD_TARGET_CHARGE_HINT,
                            target)),
            new Card(messages.text(TextKey.CARD_PLAN), messages.text(TextKey.CARD_PLAN_HINT),
                    row(ConfigSetting.PLANNING_MARGIN, TextKey.FIELD_MARGIN, TextKey.FIELD_MARGIN_HINT, margin),
                    row(ConfigSetting.IMPORT_TOLERANCE, TextKey.FIELD_IMPORT_TOLERANCE,
                            TextKey.FIELD_IMPORT_TOLERANCE_HINT, importTolerance),
                    row(ConfigSetting.MINIMUM_WINDOW, TextKey.FIELD_MIN_WINDOW, TextKey.FIELD_MIN_WINDOW_HINT,
                            minWindow)),
            new Card(messages.text(TextKey.CARD_NOTIFICATIONS), messages.text(TextKey.CARD_NOTIFICATIONS_HINT),
                    row(ConfigSetting.NOTIFY_LEAD, TextKey.FIELD_NOTIFY_LEAD, TextKey.FIELD_NOTIFY_LEAD_HINT,
                            notifyLead))
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
        var shown = PlanningEntry.of(config.planning());
        minimum.load(shown.minimumPct());
        target.load(shown.targetPct());
        margin.load(shown.marginPct());
        importTolerance.load(shown.importToleranceKwh());
        minWindow.load(shown.minWindowMinutes());
        notifyLead.load(shown.notifyLeadMinutes());
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
        fields().forEach(NumberField::commitTyped);
        try {
            var planning = new PlanningEntry(minimum.value(), target.value(), margin.value(), importTolerance.value(),
                    minWindow.value(), notifyLead.value()).planning();
            return Optional.of(config.withPlanning(planning));
        } catch (InvalidSetting e) {
            rows.get(e.setting()).showProblem(ProblemTexts.of(e.rule(), messages));
            return Optional.empty();
        }
    }

    private List<NumberField> fields() {
        return List.of(minimum, target, margin, importTolerance, minWindow, notifyLead);
    }

    private FieldRow row(ConfigSetting setting, TextKey name, TextKey hint, Input input) {
        var row = new FieldRow(messages.text(name), messages.text(hint), input);
        rows.put(setting, row);
        return row;
    }
}

package nl.enadeag.carchargeplanner.desktop.settings.planning;

import nl.enadeag.carchargeplanner.desktop.settings.field.ChoiceInput;
import nl.enadeag.carchargeplanner.desktop.settings.field.FieldRow;
import nl.enadeag.carchargeplanner.desktop.settings.field.Input;
import nl.enadeag.carchargeplanner.desktop.settings.field.NumberInput;
import nl.enadeag.carchargeplanner.desktop.settings.field.TimeSpanInput;
import nl.enadeag.carchargeplanner.desktop.settings.form.ChoiceNames;
import nl.enadeag.carchargeplanner.desktop.settings.form.Limit;
import nl.enadeag.carchargeplanner.desktop.settings.form.NumberRange;
import nl.enadeag.carchargeplanner.desktop.settings.form.PlanningEntry;
import nl.enadeag.carchargeplanner.desktop.settings.page.CardView;
import nl.enadeag.carchargeplanner.desktop.settings.page.Category;
import nl.enadeag.carchargeplanner.desktop.settings.page.ConfigPage;
import nl.enadeag.carchargeplanner.desktop.settings.page.Fold;
import nl.enadeag.carchargeplanner.desktop.settings.page.NoConfigCard;
import nl.enadeag.carchargeplanner.desktop.settings.page.PageView;
import nl.enadeag.carchargeplanner.desktop.settings.page.ProblemRows;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.config.EngineConfig;
import nl.enadeag.carchargeplanner.engine.config.NotifyLeadChoices;
import nl.enadeag.carchargeplanner.engine.config.PlanningPreferences;
import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * The planning preferences: the charge levels, when the owner can start a charge and when the owner is told, then
 * folded under "More settings" how cautious the plan is: the margin, the import tolerance and the shortest window. The
 * lead is chosen from a few; a stored lead that is none of them is offered too while it is the one chosen. The fold is
 * closed each time the page loads, and opens while one of its fields shows a problem.
 */
public final class PlanningPage implements ConfigPage {

    private final Messages messages;
    private final PageView view;
    private final NumberInput minimum;
    private final NumberInput target;
    private final NumberInput margin;
    private final NumberInput importTolerance;
    private final NumberInput minWindow;
    private final ChoiceInput<Duration> notifyLead;
    private final TimeSpanInput chargingHours;
    private final ProblemRows problems;
    private final Fold more;
    private final Region[] cards;

    /**
     * Makes the page, empty until {@link #load(EngineConfig)}.
     *
     * @param messages the texts, in the owner's language
     */
    public PlanningPage(Messages messages) {
        this.messages = messages;
        this.view = new PageView(messages.text(Category.PLANNING.title()));
        minimum = new NumberInput(NumberRange.of(Limit.of(ConfigSetting.MINIMUM_CHARGE), 5, 0), "%", messages);
        target = new NumberInput(NumberRange.of(Limit.of(ConfigSetting.TARGET_CHARGE), 5, 0), "%", messages);
        margin = new NumberInput(NumberRange.of(Limit.of(ConfigSetting.PLANNING_MARGIN), 5, 0), "%", messages);
        importTolerance = new NumberInput(NumberRange.of(Limit.of(ConfigSetting.IMPORT_TOLERANCE), 0.05, 2), "kWh",
                messages);
        minWindow = new NumberInput(NumberRange.whole(Limit.of(ConfigSetting.MINIMUM_WINDOW), 5),
                messages.text(TextKey.UNIT_MINUTES), messages);
        problems = new ProblemRows(messages);
        notifyLead = new ChoiceInput<>(NotifyLeadChoices.STANDARD, new ChoiceNames(messages)::notifyLead);
        notifyLead.onChange(() -> notifyLead.offer(NotifyLeadChoices.offered(notifyLead.value())));
        chargingHours = new TimeSpanInput(messages.text(TextKey.CHARGING_HOURS_AND));
        more = new Fold(messages.text(TextKey.MORE_SETTINGS),
                row(ConfigSetting.PLANNING_MARGIN, TextKey.FIELD_MARGIN, TextKey.FIELD_MARGIN_HINT, margin),
                row(ConfigSetting.IMPORT_TOLERANCE, TextKey.FIELD_IMPORT_TOLERANCE,
                        TextKey.FIELD_IMPORT_TOLERANCE_HINT, importTolerance),
                row(ConfigSetting.MINIMUM_WINDOW, TextKey.FIELD_MIN_WINDOW, TextKey.FIELD_MIN_WINDOW_HINT,
                        minWindow));
        var moreCard = new VBox(more);
        moreCard.getStyleClass().addAll("card", "settings-card");
        cards = new Region[] {
            new CardView(messages.text(TextKey.CARD_CHARGE_LEVELS), messages.text(TextKey.CARD_CHARGE_LEVELS_HINT),
                    row(ConfigSetting.MINIMUM_CHARGE, TextKey.FIELD_MINIMUM_CHARGE, TextKey.FIELD_MINIMUM_CHARGE_HINT,
                            minimum),
                    row(ConfigSetting.TARGET_CHARGE, TextKey.FIELD_TARGET_CHARGE, TextKey.FIELD_TARGET_CHARGE_HINT,
                            target)),
            new CardView(messages.text(TextKey.CARD_CHARGING_HOURS), "",
                    row(ConfigSetting.CHARGING_HOURS, TextKey.FIELD_CHARGING_HOURS, TextKey.FIELD_CHARGING_HOURS_HINT,
                            chargingHours)),
            new CardView(messages.text(TextKey.CARD_NOTIFICATIONS), messages.text(TextKey.CARD_NOTIFICATIONS_HINT),
                    new FieldRow(messages.text(TextKey.FIELD_NOTIFY_LEAD),
                            messages.text(TextKey.FIELD_NOTIFY_LEAD_HINT), notifyLead)),
            moreCard
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
        notifyLead.onChange(action);
        chargingHours.onChange(action);
    }

    @Override
    public boolean isChanged() {
        return fields().stream().anyMatch(NumberInput::isChanged) || notifyLead.isChanged()
                || chargingHours.isChanged();
    }

    @Override
    public void load(EngineConfig config) {
        var shown = PlanningEntry.of(config.planning());
        minimum.load(shown.minimumPct());
        target.load(shown.targetPct());
        margin.load(shown.marginPct());
        importTolerance.load(shown.importToleranceKwh());
        minWindow.load(shown.minWindowMinutes());
        var lead = config.planning().notifyLead();
        notifyLead.offer(NotifyLeadChoices.offered(lead));
        notifyLead.load(lead);
        chargingHours.load(config.planning().chargingHours().start(), config.planning().chargingHours().end());
        problems.clear();
        more.holdOpen(false);
        more.setOpen(false);
        view.show(cards);
    }

    @Override
    public void showUnavailable(String reason) {
        view.show(NoConfigCard.card(reason, messages));
    }

    @Override
    public Optional<EngineConfig> read(EngineConfig config) {
        if (!isChanged()) {
            return Optional.of(config);
        }
        if (problems.typingIsWrong()) {
            return Optional.empty();
        }
        return planning(_ -> { }).map(config::withPlanning);
    }

    /** The fold opens while one of its fields shows a problem. */
    @Override
    public boolean showProblems() {
        problems.clear();
        more.holdOpen(false);
        if (!isChanged()) {
            return false;
        }
        fields().forEach(NumberInput::commitTyped);
        var refused = problems.showTyping()
                || planning(problem -> problems.show(problem.setting(), problem.problem())).isEmpty();
        more.holdOpen(List.of(ConfigSetting.PLANNING_MARGIN, ConfigSetting.IMPORT_TOLERANCE,
                ConfigSetting.MINIMUM_WINDOW).stream().anyMatch(problems::shows));
        return refused;
    }

    /**
     * The preferences as entered, after their records checked them; a refused value goes to {@code refused}. The
     * shortest window is a whole number in an int's range, as its field takes no other.
     */
    private Optional<PlanningPreferences> planning(Consumer<PlanningEntry.Checked.Refused> refused) {
        var entry = new PlanningEntry(minimum.value(), target.value(), margin.value(), importTolerance.value(),
                (long) minWindow.value(), notifyLead.value(), chargingHours.startText(), chargingHours.endText());
        return switch (entry.check(messages, problems::text)) {
            case PlanningEntry.Checked.Accepted accepted -> Optional.of(accepted.planning());
            case PlanningEntry.Checked.Refused problem -> {
                refused.accept(problem);
                yield Optional.empty();
            }
        };
    }

    private List<NumberInput> fields() {
        return List.of(minimum, target, margin, importTolerance, minWindow);
    }

    private FieldRow row(ConfigSetting setting, TextKey name, TextKey hint, Input input) {
        return problems.add(setting, new FieldRow(messages.text(name), messages.text(hint), input));
    }
}

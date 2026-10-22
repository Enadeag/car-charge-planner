package nl.enadeag.carchargeplanner.desktop.settings.calendar;

import nl.enadeag.carchargeplanner.desktop.settings.field.FieldRow;
import nl.enadeag.carchargeplanner.desktop.settings.field.FileInput;
import nl.enadeag.carchargeplanner.desktop.settings.field.Input;
import nl.enadeag.carchargeplanner.desktop.settings.field.NumberInput;
import nl.enadeag.carchargeplanner.desktop.settings.field.SecretInput;
import nl.enadeag.carchargeplanner.desktop.settings.field.SwitchInput;
import nl.enadeag.carchargeplanner.desktop.settings.field.TimeInput;
import nl.enadeag.carchargeplanner.desktop.settings.form.CalendarEntry;
import nl.enadeag.carchargeplanner.desktop.settings.form.CalendarStep;
import nl.enadeag.carchargeplanner.desktop.settings.form.CalendarSteps;
import nl.enadeag.carchargeplanner.desktop.settings.form.NumberRange;
import nl.enadeag.carchargeplanner.desktop.settings.page.CardView;
import nl.enadeag.carchargeplanner.desktop.settings.page.Category;
import nl.enadeag.carchargeplanner.desktop.settings.page.ConfigPage;
import nl.enadeag.carchargeplanner.desktop.settings.page.NoConfigCard;
import nl.enadeag.carchargeplanner.desktop.settings.page.PageView;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.desktop.theme.Labels;
import nl.enadeag.carchargeplanner.engine.calendar.CalendarSecrets;
import nl.enadeag.carchargeplanner.engine.calendar.Consent;
import nl.enadeag.carchargeplanner.engine.calendar.ConsentRecord;
import nl.enadeag.carchargeplanner.engine.calendar.Consents;
import nl.enadeag.carchargeplanner.engine.config.CalendarSettings;
import nl.enadeag.carchargeplanner.engine.config.EngineConfig;
import nl.enadeag.carchargeplanner.engine.secrets.Secrets;
import java.net.URI;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * The trips read from the calendar, in steps that open in order: the two consents, which calendar, the route
 * service's API key, and how events become trips. Each later step opens once the one before it is done. The consents
 * are stored with their date and the version of their text; unticking one that counts asks first, and on Save
 * withdraws it and wipes what the calendar brought in. The calendar's iCal link and the API key are secret settings,
 * kept with the API keys; the rest goes to the config file.
 */
public final class CalendarPage implements ConfigPage {

    /** The route service's privacy policy, which its own website links to. */
    static final URI PRIVACY_POLICY = URI.create("https://heigit.org/privacy-policy/");

    private static final NumberRange MARGIN = new NumberRange(0, CalendarSettings.MAX_TRAVEL_MARGIN.toMinutes(), 5, 0);

    private final Messages messages;
    private final PageView view;
    private final Map<Consent, SwitchInput> boxes = new EnumMap<>(Consent.class);
    private final Map<Consent, FieldRow> boxRows = new EnumMap<>(Consent.class);
    private final SecretInput link;
    private final FileInput file;
    private final SecretInput apiKey;
    private final NumberInput margin;
    private final TimeInput departure;
    private final TimeInput back;
    private final Map<CalendarEntry.Field, FieldRow> rows = new EnumMap<>(CalendarEntry.Field.class);
    private final Label withdrawQuestion = Labels.paragraph("", "field-label");
    private final Button keep;
    private final HBox withdrawBar;
    private final Map<CalendarStep, Label> lockNotes = new EnumMap<>(CalendarStep.class);
    private final Map<CalendarStep, VBox> steps = new EnumMap<>(CalendarStep.class);
    private final CardView[] cards;
    private Consents stored = Consents.NONE;
    /** The consent whose box the owner unticked and that waits for the answer to the question; null when none. */
    private Consent withdrawing;

    /**
     * Makes the page, empty until {@link #load(EngineConfig)}.
     *
     * @param openLink opens a web page in the owner's browser
     * @param messages the texts, in the owner's language
     */
    public CalendarPage(Consumer<URI> openLink, Messages messages) {
        this.messages = messages;
        this.view = new PageView(messages.text(Category.CALENDAR.title()));
        for (var consent : Consent.values()) {
            var box = new SwitchInput(messages.text(consent == Consent.READ_CALENDAR ? TextKey.CONSENT_READ_CALENDAR
                    : TextKey.CONSENT_SEND_LOCATIONS));
            box.onChange(() -> unticked(consent));
            boxes.put(consent, box);
            boxRows.put(consent, new FieldRow("", "", box));
        }
        var privacy = new Hyperlink(messages.text(TextKey.CONSENT_PRIVACY_POLICY));
        privacy.setOnAction(_ -> openLink.accept(PRIVACY_POLICY));
        var withdraw = new Button(messages.text(TextKey.CONSENT_WITHDRAW));
        withdraw.getStyleClass().add("danger");
        withdraw.setOnAction(_ -> withdraw());
        keep = new Button(messages.text(TextKey.CONSENT_KEEP));
        keep.setOnAction(_ -> keepConsent());
        withdrawQuestion.setText(messages.text(TextKey.CONSENT_WITHDRAW_QUESTION));
        HBox.setHgrow(withdrawQuestion, Priority.ALWAYS);
        withdrawQuestion.setMaxWidth(Double.MAX_VALUE);
        withdraw.setMinWidth(Region.USE_PREF_SIZE);
        keep.setMinWidth(Region.USE_PREF_SIZE);
        withdrawBar = new HBox(withdrawQuestion, keep, withdraw);
        withdrawBar.getStyleClass().add("field-line");
        show(withdrawBar, false);

        link = new SecretInput(messages);
        file = new FileInput(messages.text(TextKey.ICS_FILE_CHOOSE), messages.text(TextKey.ICS_FILE_TITLE),
                messages.text(TextKey.FIELD_ICS_FILE), "*.ics", messages);
        apiKey = new SecretInput(messages);
        margin = new NumberInput(MARGIN, messages.text(TextKey.UNIT_MINUTES), messages);
        departure = new TimeInput();
        back = new TimeInput();

        cards = new CardView[] {
            new CardView(messages.text(TextKey.CARD_CALENDAR_CONSENT),
                    messages.text(TextKey.CARD_CALENDAR_CONSENT_HINT), boxRows.get(Consent.READ_CALENDAR),
                    boxRows.get(Consent.SEND_LOCATIONS), privacy, withdrawBar),
            step(CalendarStep.SOURCE, TextKey.CARD_CALENDAR_SOURCE, TextKey.CARD_CALENDAR_SOURCE_HINT,
                    TextKey.CALENDAR_SOURCE_LOCKED,
                    row(CalendarEntry.Field.ICAL_LINK, TextKey.FIELD_ICAL_LINK, TextKey.FIELD_ICAL_LINK_HINT, link),
                    row(CalendarEntry.Field.ICS_FILE, TextKey.FIELD_ICS_FILE, TextKey.FIELD_ICS_FILE_HINT, file)),
            step(CalendarStep.API_KEY, TextKey.CARD_ROUTE_SERVICE, TextKey.CARD_ROUTE_SERVICE_HINT,
                    TextKey.ROUTE_SERVICE_LOCKED,
                    FieldRow.of(messages, TextKey.FIELD_ROUTE_KEY, TextKey.FIELD_ROUTE_KEY_HINT, apiKey)),
            step(CalendarStep.SETTINGS, TextKey.CARD_CALENDAR_TRIPS, TextKey.CARD_CALENDAR_TRIPS_HINT,
                    TextKey.CALENDAR_TRIPS_LOCKED,
                    row(CalendarEntry.Field.TRAVEL_MARGIN, TextKey.FIELD_TRAVEL_MARGIN,
                            TextKey.FIELD_TRAVEL_MARGIN_HINT, margin),
                    row(CalendarEntry.Field.ALL_DAY_DEPARTURE, TextKey.FIELD_ALL_DAY, TextKey.FIELD_ALL_DAY_HINT,
                            departure),
                    row(CalendarEntry.Field.ALL_DAY_RETURN, TextKey.FIELD_ALL_DAY_RETURN,
                            TextKey.FIELD_ALL_DAY_RETURN_HINT, back))
        };
        inputs().forEach(input -> input.onChange(this::openSteps));
    }

    @Override
    public Category category() {
        return Category.CALENDAR;
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
        return inputs().stream().anyMatch(Input::isChanged);
    }

    /**
     * Shows the stored consents and the calendar's secret settings. Call before {@link #load(EngineConfig)}, which
     * fills the fields. While the keychain is locked, a secret field without a value shows as locked.
     *
     * @param consents the consents as stored
     * @param keys the stored secret settings, of every provider
     * @param keysLocked true when the stored secret settings could not be read as the keychain is locked
     */
    public void loadStored(Consents consents, Secrets keys, boolean keysLocked) {
        stored = consents;
        var calendarLink = keys.of(CalendarSecrets.CALENDAR).getOrDefault(CalendarSecrets.ICAL_LINK, "");
        var routeKey = keys.of(CalendarSecrets.ROUTE_SERVICE).getOrDefault(CalendarSecrets.API_KEY, "");
        loadSecret(link, calendarLink, keysLocked);
        loadSecret(apiKey, routeKey, keysLocked);
    }

    @Override
    public void load(EngineConfig config) {
        withdrawing = null;
        show(withdrawBar, false);
        for (var consent : Consent.values()) {
            boxes.get(consent).load(stored.counts(consent));
            var zone = config.system().timezone();
            boxRows.get(consent).setHint(stored.record(consent).map(record -> said(record, zone)).orElse(""));
        }
        var shown = CalendarEntry.of(config.calendar(), "");
        file.load(shown.icsFile());
        margin.load(shown.marginMinutes());
        departure.load(config.calendar().allDayDeparture());
        back.load(config.calendar().allDayReturn());
        rows.values().forEach(FieldRow::clearProblem);
        openSteps();
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
        return switch (entry().check(messages)) {
            case CalendarEntry.Checked.Accepted accepted -> Optional.of(config.withCalendar(accepted.settings()));
            case CalendarEntry.Checked.Refused _ -> Optional.empty();
        };
    }

    @Override
    public boolean showProblems() {
        rows.values().forEach(FieldRow::clearProblem);
        if (!isChanged()) {
            return false;
        }
        margin.commitTyped();
        if (entry().check(messages) instanceof CalendarEntry.Checked.Refused(var field, var problem)) {
            rows.get(field).showProblem(problem);
            return true;
        }
        return false;
    }

    /**
     * The consents the owner gives, as their boxes are ticked now.
     *
     * @return the consents whose box is ticked
     */
    public Set<Consent> consents() {
        var ticked = EnumSet.noneOf(Consent.class);
        boxes.forEach((consent, box) -> {
            if (box.value()) {
                ticked.add(consent);
            }
        });
        return Set.copyOf(ticked);
    }

    /**
     * Every provider's secret settings with the calendar's as the page holds them laid over them.
     *
     * @param keys the secret settings as the other pages hold them
     * @return the secrets to keep
     */
    public Secrets keys(Secrets keys) {
        var all = new LinkedHashMap<>(keys.byProvider());
        all.put(CalendarSecrets.CALENDAR, laid(keys.of(CalendarSecrets.CALENDAR), CalendarSecrets.ICAL_LINK,
                link.value()));
        all.put(CalendarSecrets.ROUTE_SERVICE, laid(keys.of(CalendarSecrets.ROUTE_SERVICE), CalendarSecrets.API_KEY,
                apiKey.value()));
        return new Secrets(all);
    }

    private static Map<String, String> laid(Map<String, String> values, String name, String value) {
        var laid = new LinkedHashMap<>(values);
        laid.put(name, value);
        return laid;
    }

    private static void loadSecret(SecretInput field, String value, boolean keysLocked) {
        if (keysLocked && value.isEmpty()) {
            field.showLocked();
        } else {
            field.load(value);
        }
    }

    /** A box was ticked or unticked; unticking one whose consent counts asks first, and ticks it again meanwhile. */
    private void unticked(Consent consent) {
        var box = boxes.get(consent);
        if (box.value() || withdrawing == consent || !stored.counts(consent) || !box.isChanged()) {
            return;
        }
        withdrawing = consent;
        box.select(true);
        show(withdrawBar, true);
        keep.requestFocus();
    }

    /** The owner withdraws the consent: its box is unticked, and the calendar's source is emptied. */
    private void withdraw() {
        var consent = withdrawing;
        show(withdrawBar, false);
        boxes.get(consent).select(false);
        withdrawing = null;
        link.clear();
        file.clear();
        boxes.get(consent).focusable().requestFocus();
    }

    private void keepConsent() {
        var consent = withdrawing;
        withdrawing = null;
        show(withdrawBar, false);
        boxes.get(consent).focusable().requestFocus();
    }

    /** Opens the steps whose step before is done, and locks the rest, each saying why. */
    private void openSteps() {
        var progress = new CalendarSteps(boxes.get(Consent.READ_CALENDAR).value(),
                boxes.get(Consent.SEND_LOCATIONS).value(),
                !link.value().isEmpty() || link.isLocked() || !file.value().isEmpty(),
                !apiKey.value().isEmpty() || apiKey.isLocked());
        steps.forEach((step, body) -> {
            var open = progress.isOpen(step);
            body.setDisable(!open);
            show(lockNotes.get(step), !open);
        });
    }

    /** What the date of a stored consent says, in the home's time zone: when it was given, or that its text changed. */
    private String said(ConsentRecord record, ZoneId zone) {
        var date = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(messages.language().locale())
                .format(record.givenAt().atZone(zone));
        return messages.text(record.isCurrent() ? TextKey.CONSENT_GIVEN : TextKey.CONSENT_OUTDATED, date);
    }

    private CalendarEntry entry() {
        return new CalendarEntry(link.value(), file.value(), margin.value(), departure.text(), back.text());
    }

    private List<Input> inputs() {
        return List.of(boxes.get(Consent.READ_CALENDAR), boxes.get(Consent.SEND_LOCATIONS), link, file, apiKey,
                margin, departure, back);
    }

    /** A card of a step after the consents: a note while it is locked, then its fields, which only then disable. */
    private CardView step(CalendarStep step, TextKey title, TextKey hint, TextKey locked, Node... fields) {
        var note = Labels.paragraph(messages.text(locked), "note");
        var body = new VBox(fields);
        lockNotes.put(step, note);
        steps.put(step, body);
        return new CardView(messages.text(title), messages.text(hint), note, body);
    }

    private FieldRow row(CalendarEntry.Field field, TextKey name, TextKey hint, Input input) {
        var row = FieldRow.of(messages, name, hint, input);
        rows.put(field, row);
        return row;
    }

    private static void show(Node node, boolean shown) {
        node.setVisible(shown);
        node.setManaged(shown);
    }
}

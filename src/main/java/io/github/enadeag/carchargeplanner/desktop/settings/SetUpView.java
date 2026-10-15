package io.github.enadeag.carchargeplanner.desktop.settings;

import io.github.enadeag.carchargeplanner.desktop.Appearance;
import io.github.enadeag.carchargeplanner.desktop.Language;
import io.github.enadeag.carchargeplanner.desktop.Messages;
import io.github.enadeag.carchargeplanner.desktop.ScreenReader;
import io.github.enadeag.carchargeplanner.desktop.TextKey;
import io.github.enadeag.carchargeplanner.engine.config.HomeSystem;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Set-up itself: the step and a progress bar above, the step's page in the middle, and Cancel, Back and Next (Finish
 * on the last step) below. Next goes on only when the engine's records accept the step's values; Finish saves
 * everything at once. Leaving after the owner started asks first, in the bar where the buttons are. Enter is Next and
 * Escape is Cancel; while the question shows, Escape keeps going. A refused value takes the focus to its field, whose
 * help a screen reader reads with the problem first.
 */
final class SetUpView extends BorderPane {

    private final SetUpForm form;
    private final Map<SetUpStep, SetUpPage> pages;
    private final AppPage app;
    private final Runnable close;
    private final Runnable finished;
    private final Messages messages;
    private final Label stepLabel = FieldRow.paragraph("", "setup-step");
    private final ProgressBar progress = new ProgressBar();
    private final ScrollPane body = new ScrollPane();
    private final Label status = FieldRow.paragraph("", "status-text");
    private final Button back;
    private final Button next;
    private final HBox actions;
    private final HBox confirm;
    private SetUpStep step = SetUpStep.WELCOME;

    /**
     * Makes the view; nothing shows until {@link #start(SetUpStart, SetUpDraft, SetUpStep)}.
     *
     * @param form what the steps show against what is stored
     * @param pages every step's page
     * @param app the page of the app's own settings, one of the pages
     * @param close hides set-up; called after Cancel, and after Finish saved
     * @param finished called after Finish saved, after {@code close}, so the app can plan with what was saved and show
     *     the dashboard
     * @param messages the texts, in the language set-up speaks
     */
    SetUpView(SetUpForm form, Map<SetUpStep, SetUpPage> pages, AppPage app, Runnable close, Runnable finished,
            Messages messages) {
        this.form = form;
        this.pages = new EnumMap<>(pages);
        this.app = app;
        this.close = close;
        this.finished = finished;
        this.messages = messages;

        progress.setMaxWidth(Double.MAX_VALUE);
        progress.getStyleClass().add("setup-progress");
        var header = new VBox(stepLabel, progress);
        header.getStyleClass().add("setup-header");

        body.setFitToWidth(true);
        body.getStyleClass().add("settings-body");

        var cancel = new Button(messages.text(TextKey.SETUP_CANCEL));
        cancel.setOnAction(event -> cancel());
        back = new Button(messages.text(TextKey.SETUP_BACK));
        back.setOnAction(event -> show(step.previous()));
        next = new Button();
        next.setDefaultButton(true);
        next.setOnAction(event -> next());
        actions = bar(status, cancel, back, next);

        var keepGoing = new Button(messages.text(TextKey.SETUP_KEEP_GOING));
        keepGoing.setOnAction(event -> showActions());
        var leave = new Button(messages.text(TextKey.SETUP_LEAVE));
        leave.getStyleClass().add("danger");
        leave.setOnAction(event -> {
            showActions();
            close.run();
        });
        confirm = bar(FieldRow.paragraph(messages.text(TextKey.SETUP_LEAVE_QUESTION), "field-label"), keepGoing,
                leave);
        // a scene has one Escape: the bar that shows has it, and the other lets go of it first
        cancel.cancelButtonProperty().bind(actions.visibleProperty());
        keepGoing.cancelButtonProperty().bind(confirm.visibleProperty());
        status.focusTraversableProperty().bind(status.textProperty().isNotEmpty());

        setTop(header);
        setCenter(body);
        setBottom(new StackPane(actions, confirm));
        showActions();
        getStyleClass().addAll("settings", "setup");
    }

    /**
     * Makes the view with every step's page, sharing Settings' pages and cards.
     *
     * @param stores where the settings are kept, and the engine that checks, plans and searches
     * @param keysFile the file the API keys are kept in when there is no usable keychain
     * @param openLink opens a web page in the owner's browser; called on the JavaFX thread, so it should not block
     * @param reducedMotion true while the system asks for reduced motion, for the dashboard's preview
     * @param close hides set-up; called after Cancel, and after Finish saved
     * @param finished called after Finish saved, after {@code close}
     * @param messages the texts, in the language set-up speaks
     * @return the view; nothing shows until {@link #start(SetUpStart, SetUpDraft, SetUpStep)}
     */
    static SetUpView of(SettingsStores stores, Path keysFile, Consumer<URI> openLink,
            ObservableValue<Boolean> reducedMotion, Runnable close, Runnable finished, Messages messages) {
        var engine = stores.engine();
        var providers = engine.providers();
        var app = new AppPage(messages, TextKey.SETUP_DISPLAY_HINT);
        var location = new LocationPage(
                (provider, text) -> engine.searchPlaces(provider, text, messages.language().code()), messages);
        var solar = new SolarPage(messages);
        var car = new CarCard(messages);
        app.onUnitsChanged(car::showIn);
        var charger = new ChargerPage(messages);
        // a provider's own Test asks for the system as it was when the owner reached the forecast step
        var system = new AtomicReference<HomeSystem>();
        ConnectionTest tester = (provider, settings) -> engine.checkConnection(provider, system.get(), settings);
        var forecast = new ForecastPage(providers, tester, openLink, keysFile, messages);
        var house = new HouseCard(messages);
        var away = new AwayCard(messages);
        var more = new PageView(messages.text(SetUpStep.MORE.title()));
        more.show(FieldRow.paragraph(messages.text(TextKey.SETUP_MORE_HINT), "field-label"), house.view(),
                away.view());
        var carPage = new PageView(messages.text(SetUpStep.CAR.title()));
        carPage.show(car.view());

        var pages = new EnumMap<SetUpStep, SetUpPage>(SetUpStep.class);
        pages.put(SetUpStep.WELCOME, new SetUpWelcome(providers, engine::hasPlaceSearch, messages));
        pages.put(SetUpStep.APP, SetUpPage.of(app.view()));
        pages.put(SetUpStep.LOCATION, SetUpPage.of(location.view()));
        pages.put(SetUpStep.SOLAR, SetUpPage.of(solar.view()));
        pages.put(SetUpStep.CAR, SetUpPage.of(carPage));
        pages.put(SetUpStep.CHARGER, SetUpPage.of(charger.view()));
        pages.put(SetUpStep.FORECAST, SetUpPage.of(forecast.view(), entered -> system.set(entered.config().system())));
        pages.put(SetUpStep.MORE, SetUpPage.of(more));
        pages.put(SetUpStep.CHECK, new SetUpCheck((config, keys) -> {
            var primary = config.forecast().primary();
            var settings = new HashMap<>(config.forecast().settingsOf(primary));
            settings.putAll(keys.of(primary));
            return engine.checkConnection(primary, config.system(), settings);
        }, providers, messages));
        pages.put(SetUpStep.PREVIEW, new SetUpPreview(engine::preview, reducedMotion, messages));

        var parts = new EnumMap<SetUpStep, List<ConfigPart>>(SetUpStep.class);
        parts.put(SetUpStep.LOCATION, List.of(location));
        parts.put(SetUpStep.SOLAR, List.of(solar));
        parts.put(SetUpStep.CAR, List.of(car));
        parts.put(SetUpStep.CHARGER, List.of(charger));
        parts.put(SetUpStep.FORECAST, List.of(forecast));
        parts.put(SetUpStep.MORE, List.of(house, away));
        var form = new SetUpForm(new SettingsStorage(stores), parts, location, forecast, app);
        return new SetUpView(form, pages, app, close, finished, messages);
    }

    /**
     * Shows a draft on a step, with nothing asked yet.
     *
     * @param start what is stored, which Finish compares with
     * @param draft what the steps show
     * @param first the step to show
     */
    void start(SetUpStart start, SetUpDraft draft, SetUpStep first) {
        form.start(start, draft);
        status.setText("");
        showActions();
        show(first);
    }

    /**
     * Shows a step's page and asks what it asks for, such as a forecast; the focus goes to its first field.
     *
     * @param shown the step to show
     */
    void show(SetUpStep shown) {
        step = shown;
        stepLabel.setText(messages.text(TextKey.SETUP_STEP, shown.number(), SetUpStep.count()));
        progress.setProgress((double) shown.number() / SetUpStep.count());
        progress.setAccessibleText(messages.text(TextKey.SETUP_PROGRESS, shown.number(), SetUpStep.count(),
                messages.text(shown.title())));
        back.setDisable(shown == SetUpStep.WELCOME);
        next.setText(messages.text(shown.isLast() ? TextKey.SETUP_FINISH : TextKey.SETUP_NEXT));
        var page = pages.get(shown);
        body.setContent(page.view());
        body.setVvalue(0);
        page.enter(form);
        firstField(page.view()).orElse(next).requestFocus();
    }

    /**
     * The step shown now.
     *
     * @return the step
     */
    SetUpStep step() {
        return step;
    }

    /**
     * Everything as entered, to carry over to set-up made again in another language.
     *
     * @return the draft; a value refused now is the one it had before
     */
    SetUpDraft draft() {
        return form.draft();
    }

    /**
     * Calls an action whenever the owner chooses another language on the App step, and as it loads.
     *
     * @param action gets the language chosen, empty for the system's, on the JavaFX thread
     */
    void onLanguageChosen(Consumer<Optional<Language>> action) {
        app.onLanguageChanged(action);
    }

    /**
     * Calls an action whenever the owner chooses another appearance on the App step, and as it loads.
     *
     * @param action gets the appearance chosen, on the JavaFX thread
     */
    void onAppearanceChosen(Consumer<Appearance> action) {
        app.onAppearanceChanged(action);
    }

    /** Goes to the next step when this one's values are accepted; on the last, saves everything. */
    void next() {
        showActions();
        if (!form.check(step)) {
            showRefused();
            return;
        }
        status.setText("");
        if (step.isLast()) {
            finish();
        } else {
            show(step.next());
        }
    }

    /** Leaves set-up without saving, after asking once the owner started. */
    void cancel() {
        if (step == SetUpStep.WELCOME && !form.isChanged()) {
            close.run();
            return;
        }
        actions.setVisible(false);
        confirm.setVisible(true);
        confirm.getChildren().get(1).requestFocus();
    }

    /**
     * Whether the question before leaving shows.
     *
     * @return true while set-up asks whether to leave
     */
    boolean isAsking() {
        return confirm.isVisible();
    }

    private void finish() {
        var refused = form.firstRefused();
        if (refused.isPresent()) {
            show(refused.get());
            showRefused();
            return;
        }
        switch (form.save(form.config())) {
            case SaveOutcome.Saved ignored -> {
                close.run();
                finished.run();
            }
            case SaveOutcome.Refused ignored -> {
                show(SetUpStep.APP);
                showRefused();
            }
            case SaveOutcome.Failed failure -> {
                showProblem(messages.text(TextKey.SETTINGS_SAVE_FAILED, failure.detail()));
                ScreenReader.focus(status);
            }
        }
    }

    /** The step's values were refused: the status says so, and the focus goes to the first field with a problem. */
    private void showRefused() {
        showProblem(messages.text(TextKey.SETUP_REFUSED));
        ScreenReader.focus(firstProblem(pages.get(step).view()).orElse(status));
    }

    private void showProblem(String text) {
        status.setText(text);
        if (!status.getStyleClass().contains("problem")) {
            status.getStyleClass().add("problem");
        }
    }

    private void showActions() {
        status.getStyleClass().remove("problem");
        confirm.setVisible(false);
        actions.setVisible(true);
    }

    /**
     * The control whose value has a problem, first on the page: the last control before the first problem shown, as
     * a field's problem stands below it.
     */
    private static Optional<Node> firstProblem(Node page) {
        Node before = null;
        for (var node : inOrder(page)) {
            if (node.getStyleClass().contains("field-problem")) {
                return Optional.ofNullable(before);
            }
            if (node.isFocusTraversable() && !node.isDisabled()) {
                before = node;
            }
        }
        return Optional.empty();
    }

    /** The shown nodes below a node, in the order they are drawn; a control's insides are its own. */
    private static List<Node> inOrder(Node node) {
        var nodes = new ArrayList<Node>();
        if (!node.isVisible()) {
            return nodes;
        }
        nodes.add(node);
        if (node instanceof Parent parent && !(node instanceof Control)) {
            parent.getChildrenUnmodifiable().forEach(child -> nodes.addAll(inOrder(child)));
        }
        return nodes;
    }

    /** The first control on a page the owner can type or choose in, for the focus as a step shows. */
    private static Optional<Node> firstField(Node node) {
        if (node.isFocusTraversable() && !node.isDisabled() && node.isVisible()) {
            return Optional.of(node);
        }
        if (node instanceof Parent parent && node.isVisible()) {
            for (var child : parent.getChildrenUnmodifiable()) {
                var found = firstField(child);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }

    private static HBox bar(Label text, Button... buttons) {
        HBox.setHgrow(text, Priority.ALWAYS);
        text.setMaxWidth(Double.MAX_VALUE);
        var bar = new HBox(text);
        bar.getChildren().addAll(buttons);
        for (var button : buttons) {
            button.setMinWidth(Region.USE_PREF_SIZE);
        }
        bar.getStyleClass().add("settings-actions");
        return bar;
    }
}

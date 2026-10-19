package nl.enadeag.carchargeplanner.desktop.settings;

import nl.enadeag.carchargeplanner.desktop.app.AppSettings;
import nl.enadeag.carchargeplanner.desktop.app.Background;
import nl.enadeag.carchargeplanner.desktop.settings.about.AboutFacts;
import nl.enadeag.carchargeplanner.desktop.settings.about.AboutPage;
import nl.enadeag.carchargeplanner.desktop.settings.app.AppPage;
import nl.enadeag.carchargeplanner.desktop.settings.away.AwayPage;
import nl.enadeag.carchargeplanner.desktop.settings.car.CarHousePage;
import nl.enadeag.carchargeplanner.desktop.settings.charger.ChargerPage;
import nl.enadeag.carchargeplanner.desktop.settings.forecast.ForecastPage;
import nl.enadeag.carchargeplanner.desktop.settings.forecast.ProviderTester;
import nl.enadeag.carchargeplanner.desktop.settings.location.LocationPage;
import nl.enadeag.carchargeplanner.desktop.settings.page.Category;
import nl.enadeag.carchargeplanner.desktop.settings.page.ConfigPage;
import nl.enadeag.carchargeplanner.desktop.settings.page.Page;
import nl.enadeag.carchargeplanner.desktop.settings.planning.PlanningPage;
import nl.enadeag.carchargeplanner.desktop.settings.solar.SolarPage;
import nl.enadeag.carchargeplanner.desktop.settings.store.SaveOutcome;
import nl.enadeag.carchargeplanner.desktop.settings.store.SettingsStores;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.desktop.theme.ScreenReader;
import nl.enadeag.carchargeplanner.desktop.theme.Labels;
import nl.enadeag.carchargeplanner.engine.config.ConfigProblem;
import nl.enadeag.carchargeplanner.engine.forecast.ConnectionResult;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Settings itself: the categories in a sidebar, the chosen one's page beside them, and Save and Cancel below. Closing
 * with unsaved changes asks first, in the bar where the buttons are. Enter saves and Escape closes; while the question
 * shows, Escape keeps editing. After a Save the focus goes to what it says, so a screen reader reads it: the status
 * line, or the category with a refused value.
 *
 * <p>The stores are read and written in the background, as the keychain can take seconds to answer: until they are
 * read the pages cannot be edited and the status line says that they are being read, and while a Save runs it says
 * so and nothing can be saved again. One read or Save runs at a time.
 */
final class SettingsView extends BorderPane {

    /** What Settings is doing. */
    private enum State {
        /** Reading the stores; the pages show nothing yet. */
        READING,
        /** The pages show what is stored, and can be edited and saved. */
        READY,
        /** Writing what the pages show. */
        SAVING,
        /** The stores could not be read; closing and opening Settings tries again. */
        UNREADABLE
    }

    private final SettingsForm form;
    private final List<Page> pages;
    private final Runnable close;
    private final Consumer<AppSettings> saved;
    private final Background background;
    private final Messages messages;
    private final ListView<Category> categories = new ListView<>();
    private final ScrollPane body = new ScrollPane();
    private final Label status = Labels.paragraph("", "status-text");
    private final Button cancel = new Button();
    private final Button save;
    private final Button saveAndClose;
    private final HBox actions;
    private final HBox confirm;
    private final Set<Category> refused = EnumSet.noneOf(Category.class);
    /**
     * The outcome of the last Save that went on to write; null before the first, after one whose values were refused,
     * and after the stores were read again.
     */
    private SaveOutcome outcome;
    private Runnable afterClose = () -> { };
    private State state = State.READING;
    /** True while a read or a Save runs in the background. */
    private boolean working;

    /**
     * Makes the view, showing the first category. The pages are not loaded here: see {@link #reload()}.
     *
     * @param form what the pages show against what is stored
     * @param pages every category's page, in the sidebar's order
     * @param close closes Settings; called after Cancel, or after Save and close
     * @param saved gets the app settings as stored after something was saved, so the app can follow them and the plan
     *     is made again
     * @param background reads and writes the stores away from the JavaFX thread
     * @param messages the texts, in the owner's language
     */
    SettingsView(SettingsForm form, List<Page> pages, Runnable close, Consumer<AppSettings> saved,
            Background background, Messages messages) {
        this.form = form;
        this.pages = List.copyOf(pages);
        this.close = close;
        this.saved = saved;
        this.background = background;
        this.messages = messages;
        pages.forEach(page -> page.onChange(this::updateCancelText));

        categories.getItems().setAll(pages.stream().map(Page::category).toList());
        categories.setCellFactory(_ -> new CategoryCell());
        categories.setAccessibleText(messages.text(TextKey.SETTINGS_CATEGORIES));
        categories.getStyleClass().add("settings-categories");
        categories.getSelectionModel().selectedItemProperty().subscribe(this::showPage);
        // a list takes Enter and Escape for editing a row, even one that cannot be edited: here they save and close
        categories.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if ((event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.ESCAPE) && getScene() != null) {
                var button = getScene().getAccelerators().get(new KeyCodeCombination(event.getCode()));
                if (button != null) {
                    button.run();
                    event.consume();
                }
            }
        });
        var heading = Labels.line(messages.text(TextKey.SETTINGS_HEADING), "sidebar-title");
        var sidebar = new VBox(heading, categories);
        VBox.setVgrow(categories, Priority.ALWAYS);
        sidebar.getStyleClass().add("settings-sidebar");

        body.setFitToWidth(true);
        body.getStyleClass().add("settings-body");

        cancel.setOnAction(_ -> cancel());
        save = new Button(messages.text(TextKey.SETTINGS_SAVE));
        save.setDefaultButton(true);
        save.setOnAction(_ -> save(() -> { }));
        saveAndClose = new Button(messages.text(TextKey.SETTINGS_SAVE_CLOSE));
        saveAndClose.setOnAction(_ -> save(close));
        actions = bar(status, cancel, save, saveAndClose);

        var keepEditing = new Button(messages.text(TextKey.SETTINGS_KEEP_EDITING));
        keepEditing.setOnAction(_ -> showActions());
        var discard = new Button(messages.text(TextKey.SETTINGS_DISCARD));
        discard.getStyleClass().add("danger");
        discard.setOnAction(_ -> {
            showActions();
            close.run();
            afterClose.run();
        });
        confirm = bar(Labels.paragraph(messages.text(TextKey.SETTINGS_DISCARD_QUESTION), "field-label"),
                keepEditing, discard);
        // a scene has one Escape: the bar that shows has it, and the other lets go of it first
        cancel.cancelButtonProperty().bind(actions.visibleProperty());
        keepEditing.cancelButtonProperty().bind(confirm.visibleProperty());
        status.focusTraversableProperty().bind(status.textProperty().isNotEmpty());

        setLeft(sidebar);
        setCenter(body);
        setBottom(new StackPane(actions, confirm));
        showActions();
        getStyleClass().add("settings");
        categories.getSelectionModel().selectFirst();
        goTo(State.READING);
    }

    /**
     * Makes the view with every category's page; App offers to run set-up.
     *
     * @param stores where the settings are kept; nothing is read until {@link #reload()}
     * @param facts the version and the places of the files, for the About page
     * @param openLink opens a web page in the owner's browser; called on the JavaFX thread, so it should not block
     * @param close closes Settings; called after Cancel, or after Save and close
     * @param saved gets the app settings as stored after something was saved, so the app can follow them and the plan
     *     is made again
     * @param runSetUp opens set-up, after Settings closed
     * @param background does the slow work, such as reading the stores, away from the JavaFX thread
     * @param messages the texts, in the owner's language
     * @return the view, showing the first category
     */
    static SettingsView of(SettingsStores stores, AboutFacts facts, Consumer<URI> openLink, Runnable close,
            Consumer<AppSettings> saved, Runnable runSetUp, Background background, Messages messages) {
        var app = new AppPage(messages);
        var view = of(stores, facts, openLink, close, saved, app, background, messages);
        app.offerSetUp(() -> view.closeThen(runSetUp));
        return view;
    }

    /**
     * Makes the view with every category's page, as {@link #of(SettingsStores, AboutFacts, Consumer, Runnable,
     * Consumer, Runnable, Background, Messages)}, for an app that has no set-up: App does not offer it.
     *
     * @param stores where the settings are kept; nothing is read until {@link #reload()}
     * @param facts the version and the places of the files, for the About page
     * @param openLink opens a web page in the owner's browser; called on the JavaFX thread, so it should not block
     * @param close closes Settings; called after Cancel, or after Save and close
     * @param saved gets the app settings as stored after something was saved, so the app can follow them and the plan
     *     is made again
     * @param background does the slow work, such as reading the stores, away from the JavaFX thread
     * @param messages the texts, in the owner's language
     * @return the view, showing the first category
     */
    static SettingsView of(SettingsStores stores, AboutFacts facts, Consumer<URI> openLink, Runnable close,
            Consumer<AppSettings> saved, Background background, Messages messages) {
        return of(stores, facts, openLink, close, saved, new AppPage(messages), background, messages);
    }

    private static SettingsView of(SettingsStores stores, AboutFacts facts, Consumer<URI> openLink, Runnable close,
            Consumer<AppSettings> saved, AppPage app, Background background, Messages messages) {
        var solar = new SolarPage(messages);
        var location = new LocationPage(
                (provider, text) -> stores.engine().searchPlaces(provider, text, messages.language().code()),
                background, messages);
        var carHouse = new CarHousePage(messages);
        var charger = new ChargerPage(messages);
        var engine = stores.engine();
        ProviderTester tester = (provider, settings) -> {
            try {
                return engine.checkConnection(provider, engine.config().system(), settings);
            } catch (ConfigProblem e) {
                return new ConnectionResult(false, 0, e.getMessage());
            }
        };
        var forecast = new ForecastPage(engine.providers(), tester, openLink, facts.keysFile(), background, messages);
        var away = new AwayPage(messages);
        var planning = new PlanningPage(messages);
        app.onUnitsChanged(carHouse::showIn);
        var about = new AboutPage(facts, openLink, messages);
        List<ConfigPage> configPages = List.of(solar, location, carHouse, charger, forecast, away, planning);
        var form = new SettingsForm(stores, configPages, forecast, app, about);
        var pages = new ArrayList<Page>(configPages);
        pages.add(app);
        pages.add(about);
        return new SettingsView(form, pages, close, saved, background, messages);
    }

    /**
     * Reads the stores again in the background and shows what they hold, as when Settings opens; until then the pages
     * cannot be edited. Does nothing while a read or a Save runs.
     */
    void reload() {
        reload(() -> { });
    }

    /** Reads the stores again, then runs an action on the JavaFX thread once the pages show what they hold. */
    private void reload(Runnable then) {
        if (working) {
            return;
        }
        refused.clear();
        outcome = null;
        categories.refresh();
        showActions();
        goTo(State.READING);
        working = true;
        background.run("reading the settings", form::read, stored -> {
            working = false;
            form.show(stored);
            goTo(State.READY);
            then.run();
        }, failure -> {
            working = false;
            goTo(State.UNREADABLE);
            showProblem(messages.text(TextKey.SETTINGS_READ_FAILED, Background.describe(failure, messages)));
        });
    }

    /**
     * Takes the place of another view of the same stores, such as one in another language: reads the stores, and
     * shows the category and the outcome of the last Save that the other view showed.
     *
     * @param before the view this one replaces; it should have no unsaved changes, as they are read from the stores
     */
    void continueFrom(SettingsView before) {
        show(before.shown());
        reload(() -> {
            if (before.outcome != null) {
                showOutcome(before.outcome);
            } else if (!before.refused.isEmpty()) {
                showRefused(before.refused);
            }
            categories.refresh();
        });
    }

    /**
     * Whether the owner changed something since Settings opened or was last saved.
     *
     * @return true when closing would throw edits away
     */
    boolean isChanged() {
        return (state == State.READY || state == State.SAVING) && form.isChanged();
    }

    /**
     * Whether the stores are being read, so the pages cannot be edited yet.
     *
     * @return true from the time Settings opens or reads again until the pages show what is stored
     */
    boolean isReading() {
        return state == State.READING;
    }

    /**
     * Shows a category's page and puts the focus on the sidebar, where the owner can move on with the arrow keys.
     *
     * @param category the category to show
     */
    void show(Category category) {
        categories.getSelectionModel().select(category);
        categories.requestFocus();
    }

    /**
     * Checks what the pages show and stores it in the background. On a refused value its category is marked and shown
     * at once, and nothing is stored. Does nothing while the stores are read or a Save runs.
     *
     * @param then what to do once everything was stored, on the JavaFX thread, such as closing Settings
     */
    void save(Runnable then) {
        if (state != State.READY || working) {
            return;
        }
        showActions();
        var refusing = form.markRefused();
        if (!refusing.isEmpty()) {
            afterRefused(refusing);
            return;
        }
        var changes = form.changes();
        goTo(State.SAVING);
        working = true;
        Runnable prompting = () -> background.onFx(this::showUnlockPrompt);
        background.run("saving the settings", () -> form.write(changes, prompting), written -> {
            working = false;
            written.stored().ifPresent(form::show);
            goTo(State.READY);
            afterSave(written.outcome());
            if (written.outcome() instanceof SaveOutcome.Saved) {
                saved.accept(written.stored().orElseThrow().app());
                then.run();
            }
        }, failure -> {
            working = false;
            goTo(State.READY);
            afterSave(new SaveOutcome.Failed(Background.describe(failure, messages)));
        });
    }

    /**
     * The outcome of the last Save, for a test that saved.
     *
     * @return the outcome; empty before the first Save, while one runs, after one whose values were refused, and after
     *     the stores were read again
     */
    Optional<SaveOutcome> outcome() {
        return state == State.SAVING ? Optional.empty() : Optional.ofNullable(outcome);
    }

    /**
     * The system asks the owner to unlock the keychain for the Save that runs: the status says where, and a screen
     * reader reads it.
     */
    private void showUnlockPrompt() {
        if (state == State.SAVING) {
            showBusy(messages.text(TextKey.SETTINGS_SAVING_UNLOCK));
            ScreenReader.focus(status);
        }
    }

    /** Shows what a Save came to, and moves the focus to what says so. */
    private void afterSave(SaveOutcome result) {
        if (result instanceof SaveOutcome.StartAtLoginRefused(var detail)) {
            form.showStartAtLoginFailure(detail);
        }
        showOutcome(result);
        categories.refresh(); // before the focus moves there: the marked category says it has a problem
        switch (result) {
            case SaveOutcome.Saved _ -> ScreenReader.focus(status);
            case SaveOutcome.StartAtLoginRefused _ -> show(Category.APP);
            case SaveOutcome.KeychainLocked _ -> ScreenReader.focus(status);
            case SaveOutcome.KeysNotSaved _ -> ScreenReader.focus(status);
            case SaveOutcome.Failed _ -> ScreenReader.focus(status);
        }
        updateCancelText();
    }

    /** A Save stored nothing, as values were refused: their categories are marked, and the first one shows. */
    private void afterRefused(List<Category> refusing) {
        showRefused(refusing);
        categories.refresh(); // before the focus moves there: the marked category says it has a problem
        show(refusing.getFirst());
        updateCancelText();
    }

    /** The status line, and the categories marked, after a Save whose values were refused. */
    private void showRefused(Collection<Category> refusing) {
        outcome = null;
        refused.clear();
        refused.addAll(refusing);
        showProblem(messages.text(TextKey.SETTINGS_REFUSED));
    }

    /** The status line, and the categories marked, after a Save. */
    private void showOutcome(SaveOutcome result) {
        outcome = result;
        refused.clear();
        status.getStyleClass().remove("problem");
        switch (result) {
            case SaveOutcome.Saved _ -> status.setText(messages.text(TextKey.SETTINGS_SAVED));
            case SaveOutcome.StartAtLoginRefused _ -> {
                refused.add(Category.APP);
                showProblem(messages.text(TextKey.SETTINGS_REFUSED));
            }
            case SaveOutcome.KeychainLocked _ -> showProblem(messages.text(TextKey.SETTINGS_KEYCHAIN_LOCKED));
            case SaveOutcome.KeysNotSaved _ -> showProblem(messages.text(TextKey.SETTINGS_KEYS_NOT_SAVED));
            case SaveOutcome.Failed failure ->
                    showProblem(messages.text(TextKey.SETTINGS_SAVE_FAILED, failure.detail()));
        }
    }

    private void showProblem(String text) {
        status.setText(text);
        if (!status.getStyleClass().contains("problem")) {
            status.getStyleClass().add("problem");
        }
    }

    /**
     * Goes over to another state: the pages can be edited only when ready, Save only then, and the status line says
     * what Settings is doing; the page's own text says it to a screen reader too.
     */
    private void goTo(State next) {
        state = next;
        var ready = next == State.READY;
        body.setDisable(!ready);
        save.setDisable(!ready);
        saveAndClose.setDisable(!ready);
        switch (next) {
            case READING -> showBusy(messages.text(TextKey.SETTINGS_READING));
            case SAVING -> showBusy(messages.text(TextKey.SETTINGS_SAVING));
            case READY -> {
                if (status.getStyleClass().contains("busy")) {
                    status.setText("");
                }
                status.getStyleClass().remove("busy");
                body.setAccessibleText(null);
            }
            case UNREADABLE -> {
                status.getStyleClass().remove("busy");
                body.setAccessibleText(null);
            }
        }
        updateCancelText();
    }

    private void showBusy(String text) {
        status.getStyleClass().remove("problem");
        if (!status.getStyleClass().contains("busy")) {
            status.getStyleClass().add("busy");
        }
        status.setText(text);
        body.setAccessibleText(text);
    }

    /** Closes Settings, after asking when that would throw edits away. */
    void cancel() {
        closeThen(() -> { });
    }

    /**
     * Closes Settings and then runs an action, after asking when closing would throw edits away; the owner may keep
     * editing instead. While a Save runs nothing closes, but a Save that waits for the owner to unlock the keychain
     * stops waiting.
     *
     * @param next what to run once Settings closed, such as set-up
     */
    void closeThen(Runnable next) {
        if (state == State.SAVING) {
            // a save that waits for the keychain to be unlocked stops waiting; in a moment the edits are either stored
            // or the reason they are not shows
            form.stopUnlocking();
            return;
        }
        afterClose = next;
        if (state == State.READY && form.isChanged()) {
            actions.setVisible(false);
            confirm.setVisible(true);
            confirm.getChildren().get(1).requestFocus();
        } else {
            close.run();
            next.run();
        }
    }

    /**
     * The category whose page shows.
     *
     * @return the selected category
     */
    Category shown() {
        return categories.getSelectionModel().getSelectedItem();
    }

    /**
     * Every category's page, also those not shown, for the tests.
     *
     * @return the pages in the sidebar's order
     */
    List<Page> pages() {
        return pages;
    }

    /** "Close" when nothing is unsaved, "Cancel" when closing would throw edits away. */
    private void updateCancelText() {
        var changed = state != State.READING && state != State.UNREADABLE && form.isChanged();
        cancel.setText(messages.text(changed ? TextKey.SETTINGS_CANCEL : TextKey.SETTINGS_CLOSE));
    }

    private void showActions() {
        confirm.setVisible(false);
        actions.setVisible(true);
    }

    private void showPage(Category category) {
        if (category == null) {
            return;
        }
        pages.stream().filter(page -> page.category() == category).findFirst()
                .ifPresent(page -> body.setContent(page.view()));
        body.setVvalue(0);
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

    /** A category in the sidebar, marked while one of its values was refused. */
    private final class CategoryCell extends ListCell<Category> {

        @Override
        protected void updateItem(Category category, boolean empty) {
            super.updateItem(category, empty);
            getStyleClass().remove("has-problem");
            if (empty || category == null) {
                setText(null);
                setGraphic(null);
                setAccessibleText(null);
                return;
            }
            var title = messages.text(category.title());
            setText(title);
            if (refused.contains(category)) {
                getStyleClass().add("has-problem");
                var mark = new Region();
                mark.getStyleClass().add("problem-dot");
                setGraphic(mark);
                setAccessibleText(messages.text(TextKey.CATEGORY_HAS_PROBLEMS, title));
            } else {
                setGraphic(null);
                setAccessibleText(title);
            }
        }
    }
}

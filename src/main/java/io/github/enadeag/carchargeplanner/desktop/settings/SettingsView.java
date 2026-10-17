package io.github.enadeag.carchargeplanner.desktop.settings;

import io.github.enadeag.carchargeplanner.desktop.AppSettings;
import io.github.enadeag.carchargeplanner.desktop.Background;
import io.github.enadeag.carchargeplanner.desktop.Messages;
import io.github.enadeag.carchargeplanner.desktop.ScreenReader;
import io.github.enadeag.carchargeplanner.desktop.TextKey;
import io.github.enadeag.carchargeplanner.engine.config.ConfigProblem;
import io.github.enadeag.carchargeplanner.engine.forecast.ConnectionResult;
import java.net.URI;
import java.util.ArrayList;
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
    private final Label status = FieldRow.paragraph("", "status-text");
    private final Button cancel = new Button();
    private final Button save;
    private final Button saveAndClose;
    private final HBox actions;
    private final HBox confirm;
    private final Set<Category> refused = EnumSet.noneOf(Category.class);
    private Optional<SaveOutcome> outcome = Optional.empty();
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
        pages.forEach(page -> page.onChange(this::edited));

        categories.getItems().setAll(pages.stream().map(Page::category).toList());
        categories.setCellFactory(list -> new CategoryCell());
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
        var heading = new Label(messages.text(TextKey.SETTINGS_HEADING));
        heading.getStyleClass().add("sidebar-title");
        var sidebar = new VBox(heading, categories);
        VBox.setVgrow(categories, Priority.ALWAYS);
        sidebar.getStyleClass().add("settings-sidebar");

        body.setFitToWidth(true);
        body.getStyleClass().add("settings-body");

        cancel.setOnAction(event -> cancel());
        save = new Button(messages.text(TextKey.SETTINGS_SAVE));
        save.setDefaultButton(true);
        save.setOnAction(event -> save(() -> { }));
        saveAndClose = new Button(messages.text(TextKey.SETTINGS_SAVE_CLOSE));
        saveAndClose.setOnAction(event -> save(close));
        actions = bar(status, cancel, save, saveAndClose);

        var keepEditing = new Button(messages.text(TextKey.SETTINGS_KEEP_EDITING));
        keepEditing.setOnAction(event -> showActions());
        var discard = new Button(messages.text(TextKey.SETTINGS_DISCARD));
        discard.getStyleClass().add("danger");
        discard.setOnAction(event -> {
            showActions();
            close.run();
            afterClose.run();
        });
        confirm = bar(FieldRow.paragraph(messages.text(TextKey.SETTINGS_DISCARD_QUESTION), "field-label"),
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
        enter(State.READING);
    }

    /**
     * Makes the view with every category's page.
     *
     * @param stores where the settings are kept; nothing is read until {@link #reload()}
     * @param facts the version and the places of the files, for the About page
     * @param openLink opens a web page in the owner's browser; called on the JavaFX thread, so it should not block
     * @param close closes Settings; called after Cancel, or after Save and close
     * @param saved gets the app settings as stored after something was saved, so the app can follow them and the plan
     *     is made again
     * @param runSetUp opens set-up, after Settings closed; empty while the app has no set-up, and then App does not
     *     offer it
     * @param background does the slow work, such as reading the stores, away from the JavaFX thread
     * @param messages the texts, in the owner's language
     * @return the view, showing the first category
     */
    static SettingsView of(SettingsStores stores, AboutFacts facts, Consumer<URI> openLink, Runnable close,
            Consumer<AppSettings> saved, Optional<Runnable> runSetUp, Background background, Messages messages) {
        var solar = new SolarPage(messages);
        var location = new LocationPage(
                (provider, text) -> stores.engine().searchPlaces(provider, text, messages.language().code()),
                background, messages);
        var carHouse = new CarHousePage(messages);
        var charger = new ChargerPage(messages);
        var engine = stores.engine();
        ConnectionTest tester = (provider, settings) -> {
            try {
                return engine.checkConnection(provider, engine.config().system(), settings);
            } catch (ConfigProblem e) {
                return new ConnectionResult(false, 0, e.getMessage());
            }
        };
        var forecast = new ForecastPage(engine.providers(), tester, openLink, facts.keysFile(), background, messages);
        var away = new AwayPage(messages);
        var planning = new PlanningPage(messages);
        var app = new AppPage(messages);
        app.onUnitsChanged(carHouse::showIn);
        var about = new AboutPage(facts, openLink, messages);
        List<ConfigPage> configPages = List.of(solar, location, carHouse, charger, forecast, away, planning);
        var form = new SettingsForm(stores, configPages, forecast, app, about);
        var pages = new ArrayList<Page>(configPages);
        pages.add(app);
        pages.add(about);
        var view = new SettingsView(form, pages, close, saved, background, messages);
        runSetUp.ifPresent(setUp -> app.offerSetUp(() -> view.closeThen(setUp)));
        return view;
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
        outcome = Optional.empty();
        categories.refresh();
        showActions();
        enter(State.READING);
        working = true;
        background.run("reading the settings", form::read, stored -> {
            working = false;
            form.show(stored);
            enter(State.READY);
            then.run();
        }, failure -> {
            working = false;
            enter(State.UNREADABLE);
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
            before.outcome.ifPresent(this::showOutcome);
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
        switch (form.enter()) {
            case SettingsForm.Entered.Refused refusing -> saved(new SaveOutcome.Refused(refusing.categories()));
            case SettingsForm.Entered.Accepted accepted -> {
                enter(State.SAVING);
                working = true;
                Runnable prompting = () -> background.onFx(this::waitingForUnlock);
                background.run("saving the settings", () -> form.write(accepted.changes(), prompting), written -> {
                    working = false;
                    written.stored().ifPresent(form::show);
                    enter(State.READY);
                    saved(written.outcome());
                    if (written.outcome() instanceof SaveOutcome.Saved) {
                        saved.accept(written.stored().orElseThrow().app());
                        then.run();
                    }
                }, failure -> {
                    working = false;
                    enter(State.READY);
                    saved(new SaveOutcome.Failed(Background.describe(failure, messages)));
                });
            }
        }
    }

    /**
     * The outcome of the last Save, for a test that saved.
     *
     * @return the outcome; empty before the first Save, while one runs, and after the stores were read again
     */
    Optional<SaveOutcome> outcome() {
        return state == State.SAVING ? Optional.empty() : outcome;
    }

    /**
     * The system asks the owner to unlock the keyring for the Save that runs: the status says where, and a screen
     * reader reads it.
     */
    private void waitingForUnlock() {
        if (state == State.SAVING) {
            showBusy(messages.text(TextKey.SETTINGS_SAVING_UNLOCK));
            ScreenReader.focus(status);
        }
    }

    /** Shows what a Save came to, and moves the focus to what says so. */
    private void saved(SaveOutcome result) {
        if (result instanceof SaveOutcome.StartAtLoginRefused refusal) {
            form.showStartAtLoginFailure(refusal.detail());
        }
        showOutcome(result);
        categories.refresh(); // before the focus moves there: the marked category says it has a problem
        switch (result) {
            case SaveOutcome.Saved ignored -> ScreenReader.focus(status);
            case SaveOutcome.Refused problems -> show(problems.categories().getFirst());
            case SaveOutcome.StartAtLoginRefused ignored -> show(Category.APP);
            case SaveOutcome.KeyringLocked ignored -> ScreenReader.focus(status);
            case SaveOutcome.Failed ignored -> ScreenReader.focus(status);
        }
        edited();
    }

    /** The status line, and the categories marked, after a Save. */
    private void showOutcome(SaveOutcome result) {
        outcome = Optional.of(result);
        refused.clear();
        status.getStyleClass().remove("problem");
        switch (result) {
            case SaveOutcome.Saved ignored -> status.setText(messages.text(TextKey.SETTINGS_SAVED));
            case SaveOutcome.Refused problems -> {
                refused.addAll(problems.categories());
                showProblem(messages.text(TextKey.SETTINGS_REFUSED));
            }
            case SaveOutcome.StartAtLoginRefused ignored -> {
                refused.add(Category.APP);
                showProblem(messages.text(TextKey.SETTINGS_REFUSED));
            }
            case SaveOutcome.KeyringLocked ignored -> showProblem(messages.text(TextKey.SETTINGS_KEYRING_LOCKED));
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
    private void enter(State next) {
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
        edited();
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
     * editing instead. While a Save runs nothing closes, but a Save that waits for the owner to unlock the keyring
     * stops waiting.
     *
     * @param next what to run once Settings closed, such as set-up
     */
    void closeThen(Runnable next) {
        if (state == State.SAVING) {
            // a save that waits for the keyring to be unlocked stops waiting; in a moment the edits are either stored
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
    private void edited() {
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

package io.github.enadeag.carchargeplanner.desktop.settings;

import io.github.enadeag.carchargeplanner.desktop.Messages;
import io.github.enadeag.carchargeplanner.desktop.TextKey;
import java.net.URI;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Settings itself: the categories in a sidebar, the chosen one's page beside them, and Save and Cancel below. Closing
 * with unsaved changes asks first, in the bar where the buttons are.
 */
final class SettingsView extends BorderPane {

    private final SettingsForm form;
    private final List<Page> pages;
    private final Runnable close;
    private final Runnable saved;
    private final Messages messages;
    private final ListView<Category> categories = new ListView<>();
    private final ScrollPane body = new ScrollPane();
    private final Label status = FieldRow.paragraph("", "status-text");
    private final Button cancel = new Button();
    private final HBox actions;
    private final HBox confirm;
    private final Set<Category> refused = EnumSet.noneOf(Category.class);

    /**
     * Makes the view, showing the first category. The pages are not loaded here: see {@link #reload()}.
     *
     * @param form what the pages show against what is stored
     * @param pages every category's page, in the sidebar's order
     * @param close hides Settings; called after Cancel, or after Save and close
     * @param saved called after something was saved, so the plan can be made again with it
     * @param messages the texts, in the owner's language
     */
    SettingsView(SettingsForm form, List<Page> pages, Runnable close, Runnable saved, Messages messages) {
        this.form = form;
        this.pages = List.copyOf(pages);
        this.close = close;
        this.saved = saved;
        this.messages = messages;
        pages.forEach(page -> page.onChange(this::edited));

        categories.getItems().setAll(pages.stream().map(Page::category).toList());
        categories.setCellFactory(list -> new CategoryCell());
        categories.setAccessibleText(messages.text(TextKey.SETTINGS_CATEGORIES));
        categories.getStyleClass().add("settings-categories");
        categories.getSelectionModel().selectedItemProperty().subscribe(this::showPage);
        var heading = new Label(messages.text(TextKey.SETTINGS_HEADING));
        heading.getStyleClass().add("sidebar-title");
        var sidebar = new VBox(heading, categories);
        VBox.setVgrow(categories, Priority.ALWAYS);
        sidebar.getStyleClass().add("settings-sidebar");

        body.setFitToWidth(true);
        body.getStyleClass().add("settings-body");

        cancel.setCancelButton(true);
        cancel.setOnAction(event -> cancel());
        var save = new Button(messages.text(TextKey.SETTINGS_SAVE));
        save.setDefaultButton(true);
        save.setOnAction(event -> save());
        var saveAndClose = new Button(messages.text(TextKey.SETTINGS_SAVE_CLOSE));
        saveAndClose.setOnAction(event -> {
            if (save()) {
                close.run();
            }
        });
        actions = bar(status, cancel, save, saveAndClose);

        var keepEditing = new Button(messages.text(TextKey.SETTINGS_KEEP_EDITING));
        keepEditing.setOnAction(event -> showActions());
        var discard = new Button(messages.text(TextKey.SETTINGS_DISCARD));
        discard.getStyleClass().add("danger");
        discard.setOnAction(event -> {
            showActions();
            close.run();
        });
        confirm = bar(FieldRow.paragraph(messages.text(TextKey.SETTINGS_DISCARD_QUESTION), "field-label"),
                keepEditing, discard);

        setLeft(sidebar);
        setCenter(body);
        setBottom(new StackPane(actions, confirm));
        showActions();
        getStyleClass().add("settings");
        categories.getSelectionModel().selectFirst();
    }

    /**
     * Makes the view with every category's page.
     *
     * @param stores where the settings are kept; nothing is read until {@link #reload()}
     * @param facts the version and the places of the files, for the About page
     * @param openLink opens a web page in the owner's browser; called on the JavaFX thread, so it should not block
     * @param close hides Settings; called after Cancel, or after Save and close
     * @param saved called after something was saved, so the plan can be made again with it
     * @param messages the texts, in the owner's language
     * @return the view, showing the first category
     */
    static SettingsView of(SettingsStores stores, AboutFacts facts, Consumer<URI> openLink, Runnable close,
            Runnable saved, Messages messages) {
        var planning = new PlanningPage(messages);
        var app = new AppPage(messages);
        var about = new AboutPage(facts, openLink, messages);
        var form = new SettingsForm(stores, List.of(planning), app, about);
        return new SettingsView(form, List.of(planning, app, about), close, saved, messages);
    }

    /**
     * Reads the stores again and shows what they hold, as when Settings opens.
     */
    void reload() {
        form.load();
        refused.clear();
        categories.refresh();
        status.setText("");
        showActions();
        edited();
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
     * Checks and stores what the pages show. On a refused value its category is marked and shown.
     *
     * @return true when everything was stored
     */
    boolean save() {
        showActions();
        var outcome = form.save();
        refused.clear();
        status.getStyleClass().remove("problem");
        switch (outcome) {
            case SaveOutcome.Saved ignored -> {
                status.setText(messages.text(TextKey.SETTINGS_SAVED));
                saved.run();
            }
            case SaveOutcome.Refused problems -> {
                refused.addAll(problems.categories());
                status.setText(messages.text(TextKey.SETTINGS_REFUSED));
                status.getStyleClass().add("problem");
                show(problems.categories().getFirst());
            }
            case SaveOutcome.Failed failure -> {
                status.setText(messages.text(TextKey.SETTINGS_SAVE_FAILED, failure.detail()));
                status.getStyleClass().add("problem");
            }
        }
        categories.refresh();
        edited();
        return outcome instanceof SaveOutcome.Saved;
    }

    /** Closes Settings, after asking when that would throw edits away. */
    void cancel() {
        if (form.isChanged()) {
            actions.setVisible(false);
            confirm.setVisible(true);
            confirm.getChildren().get(1).requestFocus();
        } else {
            close.run();
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
        cancel.setText(messages.text(form.isChanged() ? TextKey.SETTINGS_CANCEL : TextKey.SETTINGS_CLOSE));
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

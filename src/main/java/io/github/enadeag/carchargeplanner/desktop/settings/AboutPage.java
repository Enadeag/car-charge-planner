package io.github.enadeag.carchargeplanner.desktop.settings;

import io.github.enadeag.carchargeplanner.desktop.Messages;
import io.github.enadeag.carchargeplanner.desktop.TextKey;
import io.github.enadeag.carchargeplanner.engine.secrets.KeyStorage;
import java.net.URI;
import java.nio.file.Path;
import java.util.function.Consumer;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** The version, the author, the licence, the source code and where the app keeps its files. */
final class AboutPage implements Page {

    /** The attribution ADDITIONAL-TERMS.md requires, in these words in every language. */
    static final String ATTRIBUTION = "Car charge planner, originally by Kelvin Vroegop";
    private static final String APP_NAME = "Car charge planner";
    private static final String COPYRIGHT = "Copyright © 2026 Kelvin Vroegop";
    private static final URI LICENCE = URI.create("https://www.gnu.org/licenses/gpl-3.0.html");
    private static final URI SOURCE = URI.create("https://github.com/Enadeag/car-charge-planner");

    private final Messages messages;
    private final AboutFacts facts;
    private final Consumer<URI> openLink;
    private final PageView view;

    /**
     * Makes the page, without the files' card until {@link #load(KeyStorage)}.
     *
     * @param facts the version and the places of the files
     * @param openLink opens a web page in the owner's browser; called on the JavaFX thread, so it should not block
     * @param messages the texts, in the owner's language
     */
    AboutPage(AboutFacts facts, Consumer<URI> openLink, Messages messages) {
        this.messages = messages;
        this.facts = facts;
        this.openLink = openLink;
        this.view = new PageView(messages.text(Category.ABOUT.title()));
    }

    /**
     * Shows the page, with where the API keys are kept now.
     *
     * @param keys whether the keys are in the system keychain, in the locked keychain or in the file
     */
    void load(KeyStorage keys) {
        view.show(
                new Card(APP_NAME, "", FieldRow.paragraph(messages.text(TextKey.ABOUT_VERSION, facts.version()), "fact"),
                        FieldRow.paragraph(ATTRIBUTION, "fact"), FieldRow.paragraph(COPYRIGHT, "note")),
                new Card(messages.text(TextKey.ABOUT_LICENCE_TITLE), messages.text(TextKey.ABOUT_LICENCE),
                        link(TextKey.ABOUT_LICENCE_LINK, LICENCE)),
                new Card(messages.text(TextKey.ABOUT_SOURCE_TITLE), messages.text(TextKey.ABOUT_SOURCE),
                        link(TextKey.ABOUT_SOURCE_LINK, SOURCE)),
                new Card(messages.text(TextKey.ABOUT_FILES_TITLE), messages.text(TextKey.ABOUT_FILES),
                        place(TextKey.ABOUT_CONFIG_FILE, facts.configFile().toString()),
                        place(TextKey.ABOUT_APP_FILE, facts.appSettingsFile().toString()),
                        place(TextKey.ABOUT_DATA_FOLDER, facts.dataFolder().toString()),
                        place(TextKey.ABOUT_LOG_FILE, facts.logFile().toString()),
                        place(TextKey.ABOUT_KEYS, keys(keys, facts.keysFile()))));
    }

    @Override
    public Category category() {
        return Category.ABOUT;
    }

    @Override
    public Region view() {
        return view;
    }

    @Override
    public void onChange(Runnable action) {
        // nothing on the page can be changed
    }

    @Override
    public boolean isChanged() {
        return false;
    }

    private String keys(KeyStorage storage, Path file) {
        return switch (storage) {
            case KEYCHAIN -> messages.text(TextKey.ABOUT_KEYS_KEYCHAIN);
            case LOCKED_KEYCHAIN -> messages.text(TextKey.ABOUT_KEYS_LOCKED);
            case FILE -> messages.text(TextKey.ABOUT_KEYS_FILE, file.toString());
        };
    }

    private Hyperlink link(TextKey text, URI target) {
        var link = new Hyperlink(messages.text(text));
        link.getStyleClass().add("settings-link");
        link.setOnAction(event -> openLink.accept(target));
        return link;
    }

    /** A place as text the owner can select and copy, under what is kept there. */
    private VBox place(TextKey what, String where) {
        var name = new Label(messages.text(what));
        name.getStyleClass().add("field-label");
        var path = new TextField(where);
        path.setEditable(false);
        path.getStyleClass().add("path-field");
        path.setAccessibleText(messages.text(what));
        name.setLabelFor(path);
        var place = new VBox(name, path);
        place.getStyleClass().add("field-row");
        return place;
    }
}

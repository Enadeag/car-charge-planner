package nl.enadeag.carchargeplanner.desktop.settings.field;

import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import javafx.beans.binding.Bindings;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;

/**
 * A file on this computer by its path, typed or chosen in the system's file dialogue. Spaces around it are not kept.
 */
public final class FileInput implements Input {

    private final TextField field = new TextField();
    private final Button choose;
    private final HBox view;
    private String loaded = "";

    /**
     * Makes the field, empty until {@link #load(String)}.
     *
     * @param chooseText the text of the button that opens the dialogue, in the owner's language; a screen reader
     *     reads it with the field's name
     * @param title the dialogue's title, in the owner's language
     * @param description what the files the dialogue offers are, such as "Calendar (*.ics)"
     * @param extension the files the dialogue offers, such as "*.ics"
     * @param messages the texts, in the owner's language
     */
    public FileInput(String chooseText, String title, String description, String extension, Messages messages) {
        field.getStyleClass().add("text-input");
        HBox.setHgrow(field, Priority.ALWAYS);
        choose = new Button(chooseText);
        choose.setMinWidth(Button.USE_PREF_SIZE);
        choose.accessibleTextProperty().bind(Bindings.createStringBinding(() -> field.getAccessibleText() == null
                ? null : messages.text(TextKey.IN_GROUP, chooseText, field.getAccessibleText()), field.accessibleTextProperty()));
        choose.setOnAction(_ -> {
            var chooser = new FileChooser();
            chooser.setTitle(title);
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(description, extension));
            var chosen = chooser.showOpenDialog(choose.getScene().getWindow());
            if (chosen != null) {
                field.setText(chosen.toPath().toAbsolutePath().toString());
            }
        });
        view = new HBox(field, choose);
        view.getStyleClass().add("field-line");
    }

    /**
     * Shows a path and remembers it as the one not edited.
     *
     * @param path the path as text; may be empty
     */
    public void load(String path) {
        loaded = path;
        field.setText(path);
    }

    /** Empties the field, as the owner would; the path it was loaded with stays the one not edited. */
    public void clear() {
        field.setText("");
    }

    /**
     * The path as typed.
     *
     * @return what the field holds, without spaces around it; not necessarily a path
     */
    public String value() {
        return field.getText().strip();
    }

    @Override
    public Node view() {
        return view;
    }

    @Override
    public Node focusable() {
        return field;
    }

    @Override
    public boolean isChanged() {
        return !value().equals(loaded);
    }

    @Override
    public void onChange(Runnable action) {
        field.textProperty().addListener((_, _, _) -> action.run());
    }
}

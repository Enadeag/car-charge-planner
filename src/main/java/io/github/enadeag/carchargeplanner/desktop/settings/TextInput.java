package io.github.enadeag.carchargeplanner.desktop.settings;

import javafx.scene.Node;
import javafx.scene.control.TextField;

/** A short text, such as a panel array's name. Spaces around it are not kept. */
final class TextInput implements Input {

    private final TextField field = new TextField();
    private String loaded = "";

    /** Makes the field, empty until {@link #load(String)}. */
    TextInput() {
        field.getStyleClass().add("text-input");
    }

    /**
     * Shows a text and remembers it as the one not edited.
     *
     * @param text the text to show; may be empty
     */
    void load(String text) {
        loaded = text;
        field.setText(text);
    }

    /**
     * The text now.
     *
     * @return what the field holds, without spaces around it
     */
    String value() {
        return field.getText().strip();
    }

    @Override
    public Node view() {
        return field;
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
        field.textProperty().addListener((property, before, now) -> action.run());
    }
}

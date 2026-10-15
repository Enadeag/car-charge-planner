package io.github.enadeag.carchargeplanner.desktop.settings;

import io.github.enadeag.carchargeplanner.desktop.Messages;
import io.github.enadeag.carchargeplanner.desktop.TextKey;
import javafx.scene.Node;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;

/**
 * A secret, such as an API key: dots, with a button to look at what was typed. A pasted key often carries a space or
 * a line break, which is not kept.
 */
final class SecretInput implements Input {

    private final PasswordField hidden = new PasswordField();
    private final TextField shown = new TextField();
    private final HBox view;
    private String loaded = "";

    /**
     * Makes the field, empty and hidden until {@link #load(String)}.
     *
     * @param messages the texts, in the owner's language
     */
    SecretInput(Messages messages) {
        shown.textProperty().bindBidirectional(hidden.textProperty());
        shown.setVisible(false);
        var toggle = new ToggleButton(messages.text(TextKey.SECRET_SHOW));
        toggle.selectedProperty().subscribe(visible -> {
            shown.setVisible(visible);
            hidden.setVisible(!visible);
            toggle.setText(messages.text(visible ? TextKey.SECRET_HIDE : TextKey.SECRET_SHOW));
        });
        shown.accessibleTextProperty().bind(hidden.accessibleTextProperty());
        shown.accessibleHelpProperty().bind(hidden.accessibleHelpProperty());
        view = new HBox(new StackPane(hidden, shown), toggle);
        view.getStyleClass().addAll("field-line", "secret-input");
    }

    /**
     * Shows a stored secret, hidden, and remembers it as the one not edited.
     *
     * @param secret the secret; may be empty
     */
    void load(String secret) {
        loaded = secret;
        hidden.setText(secret);
    }

    /**
     * The secret now.
     *
     * @return what the field holds, without spaces or line breaks around it
     */
    String value() {
        return hidden.getText().strip();
    }

    @Override
    public Node view() {
        return view;
    }

    @Override
    public Node focusable() {
        return hidden;
    }

    @Override
    public boolean isChanged() {
        return !value().equals(loaded);
    }

    @Override
    public void onChange(Runnable action) {
        hidden.textProperty().addListener((property, before, now) -> action.run());
    }
}

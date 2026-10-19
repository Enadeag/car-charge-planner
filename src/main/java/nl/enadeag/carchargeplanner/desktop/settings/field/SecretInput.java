package nl.enadeag.carchargeplanner.desktop.settings.field;

import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import javafx.beans.binding.Bindings;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;

/**
 * A secret, such as an API key: dots, with a button to look at what was typed. A pasted key often carries a space or
 * a line break, which is not kept. A stored secret that could not be read, as the keychain is locked, shows as locked:
 * the field cannot be edited, so it never empties the stored one, until the owner chooses to enter a new one.
 */
public final class SecretInput implements Input {

    private final PasswordField hidden = new PasswordField();
    private final TextField shown = new TextField();
    private final ToggleButton toggle;
    private final Button replace;
    private final String lockedText;
    private final HBox view;
    private String loaded = "";
    private boolean locked;

    /**
     * Makes the field, empty and hidden until {@link #load(String)}.
     *
     * @param messages the texts, in the owner's language
     */
    public SecretInput(Messages messages) {
        shown.textProperty().bindBidirectional(hidden.textProperty());
        shown.setVisible(false);
        toggle = new ToggleButton(messages.text(TextKey.SECRET_SHOW));
        toggle.selectedProperty().subscribe(visible -> {
            shown.setVisible(visible);
            hidden.setVisible(!visible);
            toggle.setText(messages.text(visible ? TextKey.SECRET_HIDE : TextKey.SECRET_SHOW));
        });
        shown.accessibleTextProperty().bind(hidden.accessibleTextProperty());
        // a text field is named only by its label on Windows; the row's label names the hidden field
        var name = new Label();
        name.accessibleTextProperty().bind(hidden.accessibleTextProperty());
        name.setLabelFor(shown);
        toggle.accessibleTextProperty().bind(Bindings.createStringBinding(() -> hidden.getAccessibleText() == null
                        ? null : messages.text(TextKey.IN_GROUP, toggle.getText(), hidden.getAccessibleText()),
                toggle.textProperty(), hidden.accessibleTextProperty()));
        shown.accessibleHelpProperty().bind(hidden.accessibleHelpProperty());
        lockedText = messages.text(TextKey.SECRET_LOCKED);
        // a disabled field takes no focus: the button is what the keyboard and a screen reader reach
        replace = new Button(messages.text(TextKey.SECRET_REPLACE));
        replace.accessibleTextProperty().bind(Bindings.createStringBinding(() -> hidden.getAccessibleText() == null
                        ? null : messages.text(TextKey.SECRET_LOCKED_SPOKEN, hidden.getAccessibleText()),
                hidden.accessibleTextProperty()));
        replace.setOnAction(_ -> {
            editable();
            hidden.requestFocus();
        });
        view = new HBox(new StackPane(hidden, shown), toggle, replace);
        editable();
        view.getStyleClass().addAll("field-line", "secret-input");
    }

    /**
     * Shows a stored secret, hidden, and remembers it as the one not edited.
     *
     * @param secret the secret; may be empty
     */
    public void load(String secret) {
        editable();
        loaded = secret;
        hidden.setText(secret);
    }

    /**
     * Shows that the stored secret could not be read, as the keychain is locked: empty, not editable and marked locked,
     * with a button to enter a new one instead. Until then the field holds nothing to save.
     */
    public void lock() {
        loaded = "";
        hidden.setText("");
        locked = true;
        hidden.setDisable(true);
        shown.setDisable(true);
        hidden.setPromptText(lockedText);
        shown.setPromptText(lockedText);
        toggle.setSelected(false);
        show(toggle, false);
        show(replace, true);
    }

    /**
     * Whether the field shows a secret that could not be read, and the owner has not chosen to enter a new one.
     *
     * @return true while it is locked
     */
    public boolean isLocked() {
        return locked;
    }

    private void editable() {
        locked = false;
        hidden.setDisable(false);
        shown.setDisable(false);
        hidden.setPromptText("");
        shown.setPromptText("");
        show(toggle, true);
        show(replace, false);
    }

    private static void show(Node node, boolean shown) {
        node.setVisible(shown);
        node.setManaged(shown);
    }

    /**
     * The secret now.
     *
     * @return what the field holds, without spaces or line breaks around it; nothing while it is locked
     */
    public String value() {
        return locked ? "" : hidden.getText().strip();
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
        hidden.textProperty().addListener((_, _, _) -> action.run());
    }
}

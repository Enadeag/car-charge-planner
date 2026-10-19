package nl.enadeag.carchargeplanner.desktop.settings.field;

import javafx.scene.Node;

/** A control in a settings field that remembers the value it was loaded with, so it knows whether it was edited. */
public interface Input {

    /**
     * The node to put in the field's row.
     *
     * @return the control, with its unit when it has one
     */
    Node view();

    /**
     * The node that takes the keyboard focus, which the field's label and the screen reader's name belong to. For a
     * spinner or a combo box that is the control itself, not the text field inside it.
     *
     * @return the control the owner types in or chooses with
     */
    Node focusable();

    /**
     * Whether the owner changed the value since it was loaded.
     *
     * @return true when the value differs from the one last loaded
     */
    boolean isChanged();

    /**
     * Confirms what the owner typed but did not confirm yet, as Enter does, so the control shows the value as it is
     * read. Does nothing for a control whose text is its value as it stands.
     */
    default void commitTyped() {
    }

    /**
     * Calls an action whenever the value changes, by the owner or by loading.
     *
     * @param action what to run, on the JavaFX thread
     */
    void onChange(Runnable action);
}

package io.github.enadeag.carchargeplanner.desktop.theme;

import javafx.scene.AccessibleAttribute;
import javafx.scene.Node;
import javafx.scene.control.Label;

/**
 * What a screen reader needs beyond a control's own text: a name for a field that has no label beside it, and a way
 * to have it read the focused control again after its text changed.
 *
 * <p>Both work around how JavaFX talks to UI Automation on Windows ({@code WinAccessible}): it names a text field and
 * a combo box only after the label whose {@code labelFor} it is, never after the control's own accessible text; and it
 * tells the screen reader about a changed name only when the focus moves.
 */
public final class ScreenReader {

    private ScreenReader() {
    }

    /**
     * Gives a control a name for a screen reader, also a text field or a combo box without a label of its own on
     * screen.
     *
     * @param control the control the owner types in or chooses with; it keeps the label, which is not shown
     * @param name what the control is for, such as "Away from", in the owner's language; not blank
     * @throws IllegalArgumentException when the name is blank
     */
    public static void name(Node control, String name) {
        if (name.isBlank()) {
            throw new IllegalArgumentException("a control's name must not be blank");
        }
        control.setAccessibleText(name);
        var label = new Label();
        label.setAccessibleText(name);
        label.setLabelFor(control);
    }

    /**
     * Has the screen reader read the focused control again, such as after a chart's readout moved to the next hour.
     * Nothing happens when the control does not have the focus, or when no screen reader is listening.
     *
     * @param control the control whose name or help changed
     */
    public static void readAgain(Node control) {
        if (control.isFocused()) {
            // as when the focus arrives: Windows asks for the focused control's name and help again
            control.notifyAccessibleAttributeChanged(AccessibleAttribute.FOCUS_NODE);
        }
    }

    /**
     * Puts the focus on a control and has the screen reader read it, also when it had the focus already, such as a
     * field whose help now says what is wrong with its value.
     *
     * @param control the control to focus; it should be shown and focusable
     */
    public static void focus(Node control) {
        if (control.isFocused()) {
            readAgain(control);
        } else {
            control.requestFocus();
        }
    }
}

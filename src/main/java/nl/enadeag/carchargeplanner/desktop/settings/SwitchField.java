package nl.enadeag.carchargeplanner.desktop.settings;

import javafx.scene.Node;
import javafx.scene.control.CheckBox;

/** A choice that is on or off, such as starting at login. */
final class SwitchField implements Input {

    private final CheckBox box = new CheckBox();
    private boolean loaded;

    /**
     * Makes the field, off until {@link #load(boolean)}.
     *
     * @param text what the box says beside it, in the owner's language, such as "Start the app when I sign in"
     */
    SwitchField(String text) {
        box.setText(text);
        box.setAccessibleText(text);
        box.setWrapText(true);
        box.getStyleClass().add("switch-field");
    }

    /**
     * Shows a state and remembers it as the one not edited.
     *
     * @param on whether the box is ticked
     */
    void load(boolean on) {
        loaded = on;
        box.setSelected(on);
    }

    /**
     * Whether the box is ticked now.
     *
     * @return true when ticked
     */
    boolean value() {
        return box.isSelected();
    }

    /**
     * Lets the owner change the box or not.
     *
     * @param enabled false when the system cannot do what the box asks for
     */
    void setEnabled(boolean enabled) {
        box.setDisable(!enabled);
    }

    @Override
    public Node view() {
        return box;
    }

    @Override
    public Node focusable() {
        return box;
    }

    @Override
    public boolean isChanged() {
        return box.isSelected() != loaded;
    }

    @Override
    public void onChange(Runnable action) {
        box.selectedProperty().addListener((_, _, _) -> action.run());
    }
}

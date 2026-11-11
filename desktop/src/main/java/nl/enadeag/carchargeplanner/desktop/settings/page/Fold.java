package nl.enadeag.carchargeplanner.desktop.settings.page;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;

/**
 * Settings that are seldom changed, folded under a row with an arrow, such as "▸ More settings"; the row shows or
 * hides them. It starts folded. The row is a button, so the keyboard reaches it and Space folds it; a screen reader
 * reads its name without the arrow.
 */
public final class Fold extends VBox {

    private static final String CLOSED = "▸ ";
    private static final String OPEN = "▾ ";

    private final Button row = new Button();
    private final VBox content;
    private final String name;
    private boolean held;

    /**
     * Makes the fold, folded.
     *
     * @param name what the row says after its arrow, in the owner's language, such as "More settings"
     * @param content the settings under it, in order
     */
    public Fold(String name, Node... content) {
        this.name = name;
        this.content = new VBox(content);
        this.content.getStyleClass().add("fold-content");
        row.getStyleClass().addAll("fold-row", "flat");
        row.setOnAction(_ -> setOpen(held || !isOpen()));
        row.setMaxWidth(Double.MAX_VALUE);
        row.setWrapText(true);
        row.setAccessibleText(name);
        getChildren().addAll(row, this.content);
        getStyleClass().add("fold");
        setOpen(false);
    }

    /**
     * Gives the row a longer name for a screen reader, where the same fold shows more than once, such as on a second
     * panel array; the row on screen keeps its own name.
     *
     * @param spoken the name to read, such as "More settings (Panel array 2)"
     */
    public void setSpokenName(String spoken) {
        row.setAccessibleText(spoken);
    }

    /**
     * The row's own name, as it shows after the arrow.
     *
     * @return such as "More settings"
     */
    public String name() {
        return name;
    }

    /**
     * Whether the settings under the row show.
     *
     * @return true when it is open
     */
    public boolean isOpen() {
        return content.isVisible();
    }

    /**
     * Shows or hides the settings under the row, as a press on it does.
     *
     * @param open true to show them
     */
    public void setOpen(boolean open) {
        content.setVisible(open);
        content.setManaged(open);
        row.setText((open ? OPEN : CLOSED) + name);
    }

    /**
     * Opens the fold and keeps it open while held, such as while a setting under it shows a problem: a press on the
     * row then leaves it open. Letting go leaves it open until the row is pressed.
     *
     * @param hold true to open it and keep it open; false to let the row fold it again
     */
    public void holdOpen(boolean hold) {
        held = hold;
        if (hold) {
            setOpen(true);
        }
    }

    /**
     * Shows the settings without the row, for a page where they are all there is, or brings the row back.
     *
     * @param withoutRow true to hide the row and show the settings; false to show the row, the fold staying as it is
     */
    public void showWithoutRow(boolean withoutRow) {
        row.setVisible(!withoutRow);
        row.setManaged(!withoutRow);
        if (withoutRow) {
            setOpen(true);
        }
    }
}

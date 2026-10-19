package nl.enadeag.carchargeplanner.desktop.settings.page;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/** A page of Settings or a step of set-up: its title, and its cards below it. */
public final class PageView extends VBox {

    private final Label title = new Label();

    /**
     * Makes the page, without cards until {@link #show(Node...)}.
     *
     * @param title the category's or the step's name, in the owner's language
     */
    public PageView(String title) {
        this.title.setText(title);
        this.title.getStyleClass().add("page-title");
        getChildren().add(this.title);
        getStyleClass().add("settings-page");
    }

    /**
     * Shows these cards below the title, in place of those shown before.
     *
     * @param cards the cards, in order
     */
    public void show(Node... cards) {
        getChildren().setAll(title);
        getChildren().addAll(cards);
    }
}

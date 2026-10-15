package io.github.enadeag.carchargeplanner.desktop;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** The dashboard's main card: the next charge window. */
final class MainCard extends VBox {

    private final Label headline = new Label();
    private final Label detail = new Label();
    private final HBox pills = new HBox(8);

    MainCard() {
        var overline = new Label("NEXT CHARGE WINDOW");
        overline.getStyleClass().add("overline");
        headline.getStyleClass().add("headline");
        detail.getStyleClass().add("muted");
        detail.setWrapText(true);
        detail.setMinHeight(Region.USE_PREF_SIZE);
        VBox.setMargin(pills, new Insets(4, 0, 0, 0));
        getChildren().setAll(overline, headline, detail, pills);
        getStyleClass().add("card");
    }

    void show(MainCardText text) {
        headline.setText(text.headline());
        detail.setText(text.detail());
        detail.setManaged(!text.detail().isEmpty());
        detail.setVisible(!text.detail().isEmpty());
        pills.getChildren().setAll(text.pills().stream().map(MainCard::pill).toList());
    }

    private static Node pill(MainCardText.Pill pill) {
        var label = new Label(pill.text());
        label.setMinWidth(Region.USE_PREF_SIZE);
        var glyph = switch (pill.source()) {
            case SUN -> Glyphs.sun();
            case CAR -> Glyphs.battery();
            case GRID -> Glyphs.bolt();
        };
        var box = new HBox(glyph, label);
        box.setMinWidth(Region.USE_PREF_SIZE);
        box.getStyleClass().addAll("pill", pill.source().name().toLowerCase());
        return box;
    }
}

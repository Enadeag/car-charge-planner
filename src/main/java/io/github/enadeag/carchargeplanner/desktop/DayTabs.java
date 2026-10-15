package io.github.enadeag.carchargeplanner.desktop;

import java.util.List;
import java.util.function.IntConsumer;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

/**
 * The strip of day tabs, today first, each with a glyph of where its charge comes from. Left and Right move between
 * them. The tabs hold no content: the dashboard shows the selected day below them, where its height can follow the
 * width (a TabPane sizes its content without it, which cuts off wrapped text).
 */
final class DayTabs extends TabPane {

    DayTabs(IntConsumer selected) {
        setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
        getStyleClass().add("day-tabs");
        setAccessibleText("Days");
        getSelectionModel().selectedIndexProperty().subscribe(index -> {
            if (index.intValue() >= 0) {
                selected.accept(index.intValue());
            }
        });
    }

    /** Shows these days, keeping the selected tab where it was. */
    void show(List<DayModel> days) {
        int selected = Math.max(0, getSelectionModel().getSelectedIndex());
        getTabs().setAll(days.stream().map(DayTabs::tab).toList());
        if (!days.isEmpty()) {
            getSelectionModel().select(Math.min(selected, days.size() - 1));
        }
    }

    private static Tab tab(DayModel day) {
        var tab = new Tab(day.tab());
        tab.setGraphic(Glyphs.of(day.kind()));
        return tab;
    }
}

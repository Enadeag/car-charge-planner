package io.github.enadeag.carchargeplanner.desktop.dashboard;

import io.github.enadeag.carchargeplanner.desktop.text.Messages;
import io.github.enadeag.carchargeplanner.desktop.text.TextKey;
import java.util.List;
import java.util.function.IntConsumer;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

/**
 * The strip of day tabs, today first, each with a glyph of where its charge comes from. Left and Right move between
 * them. The tabs hold no content: the dashboard shows the selected day below them, where its height can follow the
 * width (a TabPane sizes its content without it, which cuts off wrapped text). A screen reader reads a tab's day
 * with what the glyph shows, such as "Charge window".
 */
final class DayTabs extends TabPane {

    /**
     * Makes the strip, empty.
     *
     * @param selected called with the index of the day tab that gets selected, from 0 for today
     * @param messages the texts, in the owner's language
     */
    DayTabs(IntConsumer selected, Messages messages) {
        setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
        getStyleClass().add("day-tabs");
        setAccessibleText(messages.text(TextKey.DASHBOARD_DAYS));
        getSelectionModel().selectedIndexProperty().subscribe(index -> {
            if (index.intValue() >= 0) {
                selected.accept(index.intValue());
            }
        });
    }

    /**
     * Shows these days, keeping the selected tab where it was; without days the strip is hidden.
     *
     * @param days the days to show a tab for, today first; may be empty
     */
    void show(List<DayModel> days) {
        int selected = Math.max(0, getSelectionModel().getSelectedIndex());
        setVisible(!days.isEmpty());
        setManaged(!days.isEmpty());
        getTabs().setAll(days.stream().map(DayTabs::tab).toList());
        if (!days.isEmpty()) {
            getSelectionModel().select(Math.min(selected, days.size() - 1));
        }
    }

    /**
     * Lays out the strip, and gives each tab's header the help a screen reader reads after the day.
     */
    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        // JavaFX reads only a Tab's text, and its header (the skin's node, made with the skin) is the one that is read
        for (var header : lookupAll(".tab")) {
            if (header.getProperties().get(Tab.class) instanceof Tab tab && tab.getUserData() instanceof String help) {
                header.setAccessibleHelp(help);
            }
        }
    }

    private static Tab tab(DayModel day) {
        var tab = new Tab(day.tab());
        tab.setGraphic(Glyphs.of(day.kind()));
        tab.setUserData(day.heading());
        return tab;
    }
}

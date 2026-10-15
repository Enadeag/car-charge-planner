package io.github.enadeag.carchargeplanner.desktop;

import java.util.List;
import java.util.Optional;
import javafx.scene.AccessibleRole;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * The dashboard itself: the header, the day tabs with the selected day below them (or a message when there is no
 * plan), and the footer. The popup and the window both show this one component.
 */
final class DashboardView extends VBox {

    private final DayTabs tabs = new DayTabs(this::showDay);
    private final DayScroll body = new DayScroll();
    private final DashboardFooter footer = new DashboardFooter();
    private final Button detach = new Button();
    private final Runnable contentChanged;
    private List<DayModel> days = List.of();
    private Optional<GridAdviceModel> advice = Optional.empty();

    /**
     * Makes the dashboard, empty until {@link #show(DashboardModel)}.
     *
     * @param detachAction what the header's button does: open the popup as a window
     * @param contentChanged called when another day or a new plan shows, so the popup or window can fit it
     */
    DashboardView(Runnable detachAction, Runnable contentChanged) {
        this.contentChanged = contentChanged;
        var title = new Label("Car charge planner");
        title.getStyleClass().add("app-title");
        title.setAccessibleRole(AccessibleRole.TEXT);
        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        detach.setGraphic(Glyphs.detach());
        detach.setTooltip(new Tooltip("Open in a window"));
        detach.setAccessibleText("Open in a window");
        detach.getStyleClass().addAll("flat", "detach");
        detach.setOnAction(event -> detachAction.run());

        var header = new HBox(title, spacer, detach);
        header.getStyleClass().add("dashboard-header");
        // header, tabs and footer keep their height; only the day below the tabs gives way, by scrolling
        for (Region fixed : List.of(header, tabs, footer)) {
            fixed.setMinHeight(USE_PREF_SIZE);
        }
        VBox.setVgrow(body, Priority.ALWAYS);
        getChildren().setAll(header, tabs, body, footer);
        getStyleClass().add("dashboard");
    }

    /**
     * Shows a plan, keeping the selected day, or why there is none.
     *
     * @param model what to show; never null
     */
    void show(DashboardModel model) {
        switch (model) {
            case DashboardModel.Plan plan -> {
                days = plan.days();
                advice = plan.advice();
                tabs.show(days);
                showDay(tabs.getSelectionModel().getSelectedIndex());
            }
            case DashboardModel.Message message -> {
                days = List.of();
                tabs.show(days);
                body.show(page(new MessageCard(message)));
                contentChanged.run();
            }
        }
        footer.show(model.footer());
    }

    /**
     * Shows or hides the header's detach button, which only makes sense in the popup.
     *
     * @param detachable true in the popup, false in the window
     */
    void setDetachable(boolean detachable) {
        detach.setVisible(detachable);
        detach.setManaged(detachable);
    }

    /** Grid advice goes on today's tab, below its main card. */
    private void showDay(int index) {
        if (days.isEmpty()) {
            body.show(null);
            contentChanged.run();
            return;
        }
        var day = days.get(Math.clamp(index, 0, days.size() - 1));
        var page = page(new MainCard(day));
        if (day == days.getFirst()) {
            advice.ifPresent(it -> page.getChildren().add(new AdviceCard(it)));
        }
        page.getChildren().add(new ChartSlot());
        body.show(page);
        contentChanged.run();
    }

    private static VBox page(Region card) {
        var page = new VBox(card);
        page.getStyleClass().add("dashboard-content");
        return page;
    }
}

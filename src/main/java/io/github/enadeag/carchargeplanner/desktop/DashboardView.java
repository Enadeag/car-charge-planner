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
    private final VBox body = new VBox();
    private final DashboardFooter footer = new DashboardFooter();
    private final Button detach = new Button();
    private List<DayModel> days = List.of();
    private Optional<GridAdviceModel> advice = Optional.empty();

    /** @param detachAction what the header's button does: open the popup as a window */
    DashboardView(Runnable detachAction) {
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
        VBox.setVgrow(body, Priority.ALWAYS);
        getChildren().setAll(header, body, footer);
        getStyleClass().add("dashboard");
    }

    void show(DashboardModel model) {
        switch (model) {
            case DashboardModel.Plan plan -> {
                days = plan.days();
                advice = plan.advice();
                tabs.show(days);
                showDay(tabs.getSelectionModel().getSelectedIndex());
            }
            case DashboardModel.Message message -> body.getChildren().setAll(page(new MessageCard(message)));
        }
        footer.show(model.footer());
    }

    /** The detach button only makes sense in the popup. */
    void setDetachable(boolean detachable) {
        detach.setVisible(detachable);
        detach.setManaged(detachable);
    }

    /** Grid advice goes on today's tab, below its main card. */
    private void showDay(int index) {
        if (days.isEmpty()) {
            body.getChildren().setAll(tabs);
            return;
        }
        var day = days.get(Math.clamp(index, 0, days.size() - 1));
        var page = page(new MainCard(day));
        if (day == days.getFirst()) {
            advice.ifPresent(it -> page.getChildren().add(new AdviceCard(it)));
        }
        page.getChildren().add(new ChartSlot());
        body.getChildren().setAll(tabs, page);
    }

    private static VBox page(Region card) {
        var page = new VBox(card);
        page.getStyleClass().add("dashboard-content");
        return page;
    }
}

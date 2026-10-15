package io.github.enadeag.carchargeplanner.desktop;

import java.util.List;
import java.util.Optional;
import java.util.function.IntConsumer;
import javafx.beans.value.ObservableValue;
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

    private final Messages messages;
    private final DayTabs tabs;
    private final DayScroll body = new DayScroll();
    private final DashboardFooter footer;
    private final Button detach = new Button();
    private final Runnable contentChanged;
    private final SolarCard solar;
    private final BatteryCard battery;
    private List<DayModel> days = List.of();
    private Optional<GridAdviceModel> advice = Optional.empty();
    private Optional<Runnable> setUp = Optional.empty();

    /**
     * Makes the dashboard, empty until {@link #show(DashboardModel)}.
     *
     * @param detachAction what the header's button does: open the popup as a window
     * @param contentChanged called when another day or a new plan shows, so the popup or window can fit it
     * @param reducedMotion true while the system asks for reduced motion
     *     ({@code Platform.getPreferences().reducedMotionProperty()}); the charts then change without moving
     * @param messages the texts, in the owner's language
     */
    DashboardView(Runnable detachAction, Runnable contentChanged, ObservableValue<Boolean> reducedMotion,
            Messages messages) {
        this.messages = messages;
        this.tabs = new DayTabs(this::showDay, messages);
        this.footer = new DashboardFooter(messages);
        this.contentChanged = contentChanged;
        this.solar = new SolarCard(reducedMotion, messages);
        this.battery = new BatteryCard(reducedMotion, messages);
        var title = new Label("Car charge planner"); // the app's name, the same in every language
        title.getStyleClass().add("app-title");
        title.setAccessibleRole(AccessibleRole.TEXT);
        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        detach.setGraphic(Glyphs.detach());
        detach.setTooltip(new Tooltip(messages.text(TextKey.DASHBOARD_DETACH)));
        detach.setAccessibleText(messages.text(TextKey.DASHBOARD_DETACH));
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
                body.show(page(new MessageCard(message, setUp, messages)));
                contentChanged.run();
            }
        }
        footer.show(model.footer());
    }

    /**
     * Which day tab is selected.
     *
     * @return the selected day's index, from 0 for today; 0 while no plan shows
     */
    int selectedDay() {
        return Math.max(0, tabs.getSelectionModel().getSelectedIndex());
    }

    /**
     * Selects a day tab and shows that day, as a click on its tab does.
     *
     * @param index the day's index, from 0 for today; one past the last day selects the last day, and without a plan
     *     nothing happens
     */
    void selectDay(int index) {
        if (!days.isEmpty()) {
            tabs.getSelectionModel().select(Math.clamp(index, 0, days.size() - 1));
        }
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

    /**
     * Lets the owner enter today's battery level on the battery card.
     *
     * @param handler gets the level the owner saved, in percent from 0 to 100, on the JavaFX thread
     */
    void onBatteryEntered(IntConsumer handler) {
        battery.onEntered(handler);
    }

    /**
     * Lets the footer's Away button open the away periods.
     *
     * @param action what the button does; called on the JavaFX thread
     */
    void onAway(Runnable action) {
        footer.onAway(action);
    }

    /**
     * Lets the footer's Settings button open Settings.
     *
     * @param action what the button does; called on the JavaFX thread
     */
    void onSettings(Runnable action) {
        footer.onSettings(action);
    }

    /**
     * Lets a message that asks for set-up offer a button that opens it; a message shown already keeps its buttons
     * until the next one.
     *
     * @param action opens set-up; called on the JavaFX thread
     */
    void onSetUp(Runnable action) {
        setUp = Optional.of(action);
    }

    /**
     * Takes the footer's Away and Settings buttons away, for a dashboard that only shows, such as set-up's preview.
     */
    void hideFooterButtons() {
        footer.hideButtons();
    }

    /** Grid advice goes on today's tab, below its main card; the charts follow. */
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
        solar.show(day.curve());
        battery.show(day.battery());
        page.getChildren().addAll(solar, battery);
        body.show(page);
        contentChanged.run();
    }

    private static VBox page(Region card) {
        var page = new VBox(card);
        page.getStyleClass().add("dashboard-content");
        return page;
    }
}

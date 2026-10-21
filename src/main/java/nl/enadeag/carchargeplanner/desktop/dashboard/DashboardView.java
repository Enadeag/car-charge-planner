package nl.enadeag.carchargeplanner.desktop.dashboard;

import nl.enadeag.carchargeplanner.desktop.dashboard.advice.GridAdviceCard;
import nl.enadeag.carchargeplanner.desktop.dashboard.advice.GridAdviceModel;
import nl.enadeag.carchargeplanner.desktop.dashboard.away.OneOffAwayModel;
import nl.enadeag.carchargeplanner.desktop.dashboard.away.OneOffAwayCard;
import nl.enadeag.carchargeplanner.desktop.dashboard.battery.BatteryCard;
import nl.enadeag.carchargeplanner.desktop.dashboard.day.DayModel;
import nl.enadeag.carchargeplanner.desktop.dashboard.day.DayScroll;
import nl.enadeag.carchargeplanner.desktop.dashboard.day.DayTabs;
import nl.enadeag.carchargeplanner.desktop.dashboard.day.DayCard;
import nl.enadeag.carchargeplanner.desktop.dashboard.outlook.DashboardModel;
import nl.enadeag.carchargeplanner.desktop.dashboard.parts.Glyphs;
import nl.enadeag.carchargeplanner.desktop.dashboard.solar.SolarCard;
import nl.enadeag.carchargeplanner.desktop.dashboard.trip.TripAdviceCard;
import nl.enadeag.carchargeplanner.desktop.dashboard.trip.TripAdviceModel;
import nl.enadeag.carchargeplanner.desktop.dashboard.trip.TripCard;
import nl.enadeag.carchargeplanner.desktop.dashboard.trip.TripsModel;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod;
import nl.enadeag.carchargeplanner.engine.trip.Trip;
import java.time.InstantSource;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
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
 * The dashboard itself: the header, the day tabs with the selected day below them (or why there is no plan), and the
 * footer. The popup and the window both show this one component.
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
    private final OneOffAwayCard away;
    private final TripCard trips;
    private List<DayModel> days = List.of();
    private List<TripAdviceModel> tripAdvice = List.of();
    /** Today's grid advice; null when the plan shown has none, or before one shows. */
    private GridAdviceModel advice;
    /** The away periods for specific days; null until a plan shows. */
    private OneOffAwayModel awayPeriods;
    /** The trips; null until a plan shows. */
    private TripsModel enteredTrips;
    private Runnable setUp = () -> { };
    /** True once {@link #onSetUp(Runnable)} was called: only then does a card without a plan offer set-up. */
    private boolean canOpenSetUp;

    /**
     * Makes the dashboard, empty until {@link #show(DashboardModel)}.
     *
     * @param detachAction what the header's button does: open the popup as a window
     * @param contentChanged called when another day or a new plan shows, so the popup or window can fit it
     * @param reducedMotion true while the system asks for reduced motion
     *     ({@code Platform.getPreferences().reducedMotionProperty()}); the charts then change without moving
     * @param appName the app's name, for the header; the same in every language
     * @param messages the texts, in the owner's language
     */
    DashboardView(Runnable detachAction, Runnable contentChanged, ObservableValue<Boolean> reducedMotion,
            String appName, Messages messages) {
        this.messages = messages;
        this.tabs = new DayTabs(this::showDay, messages);
        this.footer = new DashboardFooter(messages);
        this.contentChanged = contentChanged;
        this.solar = new SolarCard(reducedMotion, messages);
        this.battery = new BatteryCard(reducedMotion, messages);
        this.away = new OneOffAwayCard(contentChanged, messages);
        this.trips = new TripCard(contentChanged, messages);
        var title = new Label(appName);
        title.getStyleClass().add("app-title");
        title.setAccessibleRole(AccessibleRole.TEXT);
        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        detach.setGraphic(Glyphs.detach());
        detach.setTooltip(new Tooltip(messages.text(TextKey.DASHBOARD_DETACH)));
        detach.setAccessibleText(messages.text(TextKey.DASHBOARD_DETACH));
        detach.getStyleClass().addAll("flat", "detach");
        detach.setOnAction(_ -> detachAction.run());

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
                tripAdvice = plan.tripAdvice();
                advice = plan.advice().orElse(null);
                awayPeriods = plan.away();
                enteredTrips = plan.trips();
                tabs.show(days);
                showDay(tabs.getSelectionModel().getSelectedIndex());
            }
            case DashboardModel.NoPlan noPlan -> {
                days = List.of();
                tabs.show(days);
                body.show(page(new NoPlanCard(noPlan, canOpenSetUp, setUp, messages)));
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
     * @param index the day's index, from 0 for today; an index past the last day selects the last day and one below
     *     0 selects today, and without a plan nothing happens
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
     * Lets the owner add and remove away periods for specific days, on the away card below each day; without this
     * the card is not shown.
     *
     * @param added gets a period the owner entered, on the JavaFX thread
     * @param removed gets a period the owner removed, as the plan listed it, on the JavaFX thread
     * @param clock tells the time when the owner saves a period, which must not have ended by then
     */
    void onOneOffAwayChange(Consumer<OneOffAwayPeriod> added, Consumer<OneOffAwayPeriod> removed, InstantSource clock) {
        away.onChange(added, removed, clock);
        if (!days.isEmpty()) {
            showDay(selectedDay());
        }
    }

    /**
     * Lets the owner add, change and remove trips, on the trip card of each day; without this the card is not shown.
     *
     * @param added gets a trip the owner entered, on the JavaFX thread
     * @param changed gets a trip as the plan listed it and the owner's changed version of it, on the JavaFX thread
     * @param removed gets a trip the owner removed, as the plan listed it, on the JavaFX thread
     * @param clock tells the time when the owner saves a trip, which must not have returned by then
     */
    void onTripChange(Consumer<Trip> added, BiConsumer<Trip, Trip> changed, Consumer<Trip> removed,
            InstantSource clock) {
        trips.onChange(added, changed, removed, clock);
        if (!days.isEmpty()) {
            showDay(selectedDay());
        }
    }

    /**
     * Lets the footer's Away button open the weekly away periods.
     *
     * @param action what the button does; called on the JavaFX thread
     */
    void onWeeklyAway(Runnable action) {
        footer.onWeeklyAway(action);
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
     * Lets the card shown without a plan offer a button that opens set-up when it asks for set-up; a card shown
     * already keeps its buttons until the next one.
     *
     * @param action opens set-up; called on the JavaFX thread
     */
    void onSetUp(Runnable action) {
        setUp = action;
        canOpenSetUp = true;
    }

    /**
     * Takes the footer's Away and Settings buttons away, for a dashboard that only shows, such as set-up's preview.
     */
    void hideFooterButtons() {
        footer.hideButtons();
    }

    /**
     * Below the day card comes the advice for the trips that depart that day, and on today's tab also for those that
     * ask the owner to do something now; grid advice goes on today's tab, below them. The charts follow, then the
     * day's trips, and the away periods last.
     */
    private void showDay(int index) {
        if (days.isEmpty()) {
            body.show(null);
            contentChanged.run();
            return;
        }
        var day = days.get(Math.clamp(index, 0, days.size() - 1));
        var page = page(new DayCard(day));
        var today = days.getFirst().date();
        for (var trip : tripAdvice) {
            if (trip.shownOn(day.date(), today)) {
                page.getChildren().add(tripAdviceCard(trip, trip.on(day.date(), today)));
            }
        }
        if (day == days.getFirst()) {
            if (advice != null) {
                page.getChildren().add(new GridAdviceCard(advice));
            }
        }
        solar.show(day.curve());
        battery.show(day.battery());
        page.getChildren().addAll(solar, battery);
        if (trips.acceptsChanges() && enteredTrips != null) {
            trips.show(day, enteredTrips);
            page.getChildren().add(trips);
        }
        if (away.acceptsChanges() && awayPeriods != null) {
            away.show(day, awayPeriods);
            page.getChildren().add(away);
        }
        body.show(page);
        contentChanged.run();
    }

    /**
     * A trip's advice in the words of the tab it shows on, whose Adjust opens the battery level's entry on today's tab,
     * where it can be entered.
     */
    private TripAdviceCard tripAdviceCard(TripAdviceModel trip, TripAdviceModel.Wording wording) {
        var card = new TripAdviceCard(trip, wording, messages);
        if (battery.acceptsLevel()) {
            card.onAdjust(() -> {
                selectDay(0);
                battery.enterLevel();
            });
        }
        return card;
    }

    private static VBox page(Region card) {
        var page = new VBox(card);
        page.getStyleClass().add("dashboard-content");
        return page;
    }
}

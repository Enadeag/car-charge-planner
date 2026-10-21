package nl.enadeag.carchargeplanner.desktop.dashboard.trip;

import static nl.enadeag.carchargeplanner.desktop.theme.Labels.line;
import static nl.enadeag.carchargeplanner.desktop.theme.Labels.paragraph;

import nl.enadeag.carchargeplanner.desktop.dashboard.day.DayModel;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.trip.Trip;
import java.time.InstantSource;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * The card with the trips the car is away for on the selected day: each with its times, distance and origin, the
 * choices "Not with this car" and "I can charge at the destination", and Change and Remove; and an Add button that
 * enters a trip leaving on the selected day.
 */
public final class TripCard extends VBox {

    /** Which of a trip's choices the owner changed, so the focus can go back to it when the trip shows again. */
    private enum Choice { NOT_WITH_THIS_CAR, CAN_CHARGE }

    /** A choice changed on a trip, as the trip will show once saved. */
    private record Changed(Trip trip, Choice choice) {
    }

    private final Messages messages;
    private final Runnable resized;
    private final Button add;
    private final VBox list = new VBox();
    private final TripEditor editor;
    private Consumer<Trip> added = _ -> { };
    private BiConsumer<Trip, Trip> changed = (_, _) -> { };
    private Consumer<Trip> removed = _ -> { };
    /** Tells the time when the owner saves a trip; null until {@link #onChange} gives it. */
    private InstantSource clock;
    /** True once {@link #onChange} gave somewhere to send the trips. */
    private boolean acceptsChanges;
    /** The selected day; null until one shows. */
    private DayModel day;
    private TripsModel model;
    /** The trip the entry changes; null while it enters a new one or is closed. */
    private Trip changing;
    /** The choice the owner changed last, until its trip shows again; null when there is none. */
    private Changed refocus;

    /**
     * Makes the card, empty until {@link #show(DayModel, TripsModel)}.
     *
     * @param resized called when the entry opens, closes or changes its height, so the popup or window can fit the
     *     card
     * @param messages the texts, in the owner's language
     */
    public TripCard(Runnable resized, Messages messages) {
        this.messages = messages;
        this.resized = resized;
        add = new Button(messages.text(TextKey.TRIP_ADD));
        add.getStyleClass().addAll("flat", "small");
        add.setMinWidth(USE_PREF_SIZE);
        add.setOnAction(_ -> {
            openEditor(null);
            resized.run();
        });
        editor = new TripEditor(this::save, this::closeEntry, resized, messages);
        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        var header = new HBox(line(messages.text(TextKey.TRIP_HEADING), "card-label"), spacer, add);
        header.getStyleClass().add("chart-header");
        list.getStyleClass().add("trip-list");
        getChildren().setAll(header, list, editor);
        getStyleClass().addAll("card", "trip-card");
        closeEditor();
    }

    /**
     * Shows the trips of a day, with Add for that day. An open entry stays open while the same day shows, and closes
     * on another day.
     *
     * @param selected the selected day, whose trips show and on which Add enters a trip
     * @param trips the stored trips and the time of the plan
     */
    public void show(DayModel selected, TripsModel trips) {
        boolean sameDay = day != null && day.date().equals(selected.date());
        day = selected;
        model = trips;
        var hint = messages.text(TextKey.TRIP_ADD_HINT, selected.title());
        add.setTooltip(new Tooltip(hint));
        add.setAccessibleHelp(hint);
        list.getChildren().clear();
        var onTheDay = trips.on(selected.date());
        if (onTheDay.isEmpty()) {
            list.getChildren().add(paragraph(messages.text(TextKey.TRIP_NONE), "note"));
        }
        onTheDay.forEach(entry -> list.getChildren().add(row(entry)));
        if (!sameDay) {
            closeEditor();
        }
    }

    /**
     * Lets the owner add, change and remove trips from now on; until then the card cannot be used.
     *
     * @param addedHandler gets a trip the owner entered; called on the JavaFX thread
     * @param changedHandler gets a trip as the model gave it and the owner's changed version of it; called on the
     *     JavaFX thread
     * @param removedHandler gets a trip the owner removed, as the model gave it; called on the JavaFX thread
     * @param clock tells the time when the owner saves a trip, which must not have returned by then; never null
     */
    public void onChange(Consumer<Trip> addedHandler, BiConsumer<Trip, Trip> changedHandler,
            Consumer<Trip> removedHandler, InstantSource clock) {
        added = addedHandler;
        changed = changedHandler;
        removed = removedHandler;
        this.clock = clock;
        acceptsChanges = true;
    }

    /**
     * Whether the owner can add, change and remove trips here, so the card is worth showing.
     *
     * @return true once {@link #onChange} gave somewhere to send them
     */
    public boolean acceptsChanges() {
        return acceptsChanges;
    }

    /**
     * The entry, for the tests.
     *
     * @return the card's entry; shown only while the owner enters or changes a trip
     */
    TripEditor editor() {
        return editor;
    }

    private VBox row(TripsModel.Listed entry) {
        var trip = entry.trip();
        var notWithThisCar = choice(TextKey.TRIP_NOT_WITH_THIS_CAR, entry, trip.notWithThisCar(),
                Choice.NOT_WITH_THIS_CAR);
        notWithThisCar.setOnAction(_ -> changeChoice(trip, new Trip(trip.departure(), trip.returnTime(),
                trip.distanceKm(), trip.canChargeAtDestination(), notWithThisCar.isSelected()),
                Choice.NOT_WITH_THIS_CAR));
        var canCharge = choice(TextKey.TRIP_CAN_CHARGE, entry, trip.canChargeAtDestination(), Choice.CAN_CHARGE);
        canCharge.setOnAction(_ -> changeChoice(trip, new Trip(trip.departure(), trip.returnTime(),
                trip.distanceKm(), canCharge.isSelected(), trip.notWithThisCar()), Choice.CAN_CHARGE));
        var choices = new FlowPane(notWithThisCar, canCharge);
        choices.getStyleClass().add("trip-choices");

        var change = new Button(messages.text(TextKey.TRIP_CHANGE));
        change.setAccessibleText(entry.changeSpoken());
        change.setOnAction(_ -> {
            openEditor(trip);
            resized.run();
        });
        var remove = new Button(messages.text(TextKey.TRIP_REMOVE));
        remove.setAccessibleText(entry.removeSpoken());
        remove.setOnAction(_ -> {
            // the row goes once the trip is removed: the keyboard goes on from Add
            add.requestFocus();
            removed.accept(trip);
        });
        for (var button : List.of(change, remove)) {
            button.getStyleClass().addAll("flat", "small");
            button.setMinWidth(USE_PREF_SIZE);
        }
        var buttons = new HBox(change, remove);
        buttons.getStyleClass().add("trip-buttons");

        var row = new VBox(paragraph(entry.text(), "trip-item"), paragraph(entry.originText(), "note"), choices,
                buttons);
        row.getStyleClass().add("trip-row");
        return row;
    }

    /**
     * A choice of a trip. The box that the owner changed last takes the focus again once its changed trip shows, as
     * the row is made anew then.
     */
    private CheckBox choice(TextKey text, TripsModel.Listed entry, boolean selected, Choice which) {
        var box = new CheckBox(messages.text(text));
        box.setSelected(selected);
        box.setWrapText(true);
        box.setMinHeight(USE_PREF_SIZE);
        box.setAccessibleText(messages.text(TextKey.IN_GROUP, box.getText(), entry.text()));
        if (new Changed(entry.trip(), which).equals(refocus)) {
            refocus = null;
            // once the dashboard has put the day's cards in place
            Platform.runLater(box::requestFocus);
        }
        return box;
    }

    private void changeChoice(Trip trip, Trip changedTrip, Choice which) {
        refocus = new Changed(changedTrip, which);
        changed.accept(trip, changedTrip);
    }

    private void save(Trip trip) {
        if (changing == null) {
            added.accept(trip);
        } else {
            changed.accept(changing, trip);
        }
    }

    /** After a save or a cancel the focus goes back to Add, where the keyboard goes on from. */
    private void closeEntry() {
        closeEditor();
        add.requestFocus();
        resized.run();
    }

    /**
     * Opens the entry when the card accepts changes; otherwise it stays closed.
     *
     * @param trip the trip to change; null to enter a new one on the selected day
     */
    private void openEditor(Trip trip) {
        if (day == null || !acceptsChanges()) {
            closeEditor();
            return;
        }
        changing = trip;
        showEditor(true);
        if (trip == null) {
            editor.prepare(day.date(), model.zone(), clock, model.units());
        } else {
            editor.prepare(trip, model.zone(), clock, model.units());
        }
    }

    private void closeEditor() {
        changing = null;
        showEditor(false);
    }

    /** The entry and Add take turns: Add shows while the entry is closed. */
    private void showEditor(boolean shown) {
        editor.setVisible(shown);
        editor.setManaged(shown);
        add.setVisible(!shown);
        add.setManaged(!shown);
    }
}

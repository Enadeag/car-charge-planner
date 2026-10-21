package nl.enadeag.carchargeplanner.desktop.dashboard.trip;

import nl.enadeag.carchargeplanner.desktop.app.Units;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.desktop.theme.Labels;
import nl.enadeag.carchargeplanner.desktop.theme.ScreenReader;
import nl.enadeag.carchargeplanner.engine.trip.Trip;
import java.time.Instant;
import java.time.InstantSource;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/**
 * Where the owner enters a trip or changes one: the day and time of departure and of return, the distance there and
 * back in the owner's units, and whether the car can charge at the destination, with Save and Cancel; Enter saves and
 * Escape cancels. A trip that is not valid is not saved: the entry says why, stays open and puts the focus where the
 * owner can fix it, where a screen reader reads the reason.
 */
final class TripEditor extends VBox {

    /** How many days from today the days of departure and return offer, beyond those of a trip being changed. */
    static final int DAYS_OFFERED = 21;

    private static final DateTimeFormatter SHOWN = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
    private static final LocalTime FIRST_DEPARTURE = LocalTime.of(8, 0);
    private static final LocalTime FIRST_RETURN = LocalTime.of(18, 0);

    private final Messages messages;
    private final Consumer<Trip> saved;
    private final Runnable closed;
    private final Runnable resized;
    private final ComboBox<LocalDate> departureDay = new ComboBox<>();
    private final TextField departureTime = new TextField();
    private final ComboBox<LocalDate> returnDay = new ComboBox<>();
    private final TextField returnTime = new TextField();
    private final TextField distance = new TextField();
    private final Label unit = Labels.line("", "fact");
    private final CheckBox canCharge;
    /** The units the distance is typed in; set again by {@link #prepare}. */
    private Units units = Units.METRIC;
    private final Label problem = Labels.paragraph("", "field-problem");
    private TripCheck check;
    /** The day "today" and "tomorrow" are told from; set by {@link #prepare}. */
    private LocalDate today = LocalDate.EPOCH;
    /** Whether the trip being entered is made without this car, which the entry keeps as it was. */
    private boolean notWithThisCar;
    /** The distance of the trip being changed as the field first showed it; empty for a new trip. */
    private String distanceShown = "";
    /** The distance of the trip being changed in km, kept exactly while the field shows it unchanged. */
    private double distanceKm;

    /**
     * Makes the entry, empty until {@link #prepare}.
     *
     * @param saved gets the trip the owner saved; called on the JavaFX thread
     * @param closed called when the entry should close, after a save or a cancel
     * @param resized called when the entry grows or shrinks while open: a problem shows or goes
     * @param messages the texts, in the owner's language
     */
    TripEditor(Consumer<Trip> saved, Runnable closed, Runnable resized, Messages messages) {
        this.messages = messages;
        this.saved = saved;
        this.closed = closed;
        this.resized = resized;
        this.check = new TripCheck(ZoneId.of("UTC"), InstantSource.fixed(Instant.EPOCH), Units.METRIC, messages);

        ScreenReader.name(departureDay, messages.text(TextKey.TRIP_DEPARTURE_DAY));
        ScreenReader.name(departureTime, messages.text(TextKey.TRIP_DEPARTURE_TIME));
        ScreenReader.name(returnDay, messages.text(TextKey.TRIP_RETURN_DAY));
        ScreenReader.name(returnTime, messages.text(TextKey.TRIP_RETURN_TIME));
        var days = new StringConverter<LocalDate>() {
            @Override
            public String toString(LocalDate day) {
                return day == null ? "" : messages.date(day, today);
            }

            @Override
            public LocalDate fromString(String text) {
                throw new UnsupportedOperationException("the days are chosen, not typed");
            }
        };
        for (var day : List.of(departureDay, returnDay)) {
            day.setConverter(days);
            day.getStyleClass().add("trip-day");
            day.setVisibleRowCount(7);
        }
        for (var time : List.of(departureTime, returnTime)) {
            time.getStyleClass().add("trip-time");
            time.setPrefColumnCount(4);
        }
        var when = new GridPane();
        when.addRow(0, label(TextKey.TRIP_DEPARTURE), departureDay, departureTime);
        when.addRow(1, label(TextKey.TRIP_RETURN), returnDay, returnTime);
        when.getStyleClass().add("trip-when");

        showIn(units);
        distance.getStyleClass().add("trip-distance");
        distance.setPrefColumnCount(5);
        unit.setMinWidth(USE_PREF_SIZE);
        var far = new HBox(label(TextKey.TRIP_DISTANCE_FIELD), distance, unit);
        far.getStyleClass().add("trip-far");

        canCharge = new CheckBox(messages.text(TextKey.TRIP_CAN_CHARGE));
        canCharge.setWrapText(true);
        canCharge.setMinHeight(USE_PREF_SIZE);

        var save = new Button(messages.text(TextKey.TRIP_SAVE));
        save.getStyleClass().add("accent");
        save.setOnAction(_ -> save());
        var cancel = new Button(messages.text(TextKey.TRIP_CANCEL));
        cancel.getStyleClass().add("flat");
        cancel.setOnAction(_ -> closed.run());
        // the battery's and the away period's entries can be open as well, with their own Save and Cancel
        var heading = messages.text(TextKey.TRIP_HEADING);
        save.setAccessibleText(messages.text(TextKey.IN_GROUP, save.getText(), heading));
        cancel.setAccessibleText(messages.text(TextKey.IN_GROUP, cancel.getText(), heading));
        var buttons = new HBox(save, cancel);
        buttons.getStyleClass().add("trip-entry-buttons");

        setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                closed.run();
                event.consume();
            }
        });
        // Enter saves from the fields and the check box; Save and Cancel take Enter themselves, and a day's list,
        // while open, takes it to choose its day
        addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER && !(event.getTarget() instanceof Button)
                    && !departureDay.isShowing() && !returnDay.isShowing()) {
                save();
                event.consume();
            }
        });
        var sizes = new FlowPane(when, far);
        sizes.getStyleClass().add("trip-fields");
        getChildren().setAll(sizes, canCharge, buttons, problem);
        getStyleClass().add("trip-editor");
        problem.setVisible(false);
        problem.setManaged(false);
    }

    /**
     * Fills the entry for a new trip that leaves on a day and returns the same day, and puts the focus in it; the card
     * shows the entry.
     *
     * @param day the day the trip leaves
     * @param zone the time zone of the owner's home, in which the days and times are read
     * @param clock tells the time when the owner saves: a trip that has returned by then is refused, also when the
     *     plan shown was made earlier; and which day is today
     * @param distanceUnits the units the distance is typed in
     */
    void prepare(LocalDate day, ZoneId zone, InstantSource clock, Units distanceUnits) {
        start(zone, clock, distanceUnits, day, day);
        departureDay.setValue(day);
        departureTime.setText(SHOWN.format(FIRST_DEPARTURE));
        returnDay.setValue(day);
        returnTime.setText(SHOWN.format(FIRST_RETURN));
        distance.setText("");
        distanceShown = "";
        canCharge.setSelected(false);
        notWithThisCar = false;
        departureDay.requestFocus();
    }

    /**
     * Fills the entry with a stored trip to change it, and puts the focus in it; the card shows the entry.
     *
     * @param trip the trip as it is stored
     * @param zone the time zone of the owner's home, in which the days and times are read and shown
     * @param clock tells the time when the owner saves, as for a new trip
     * @param distanceUnits the units the distance is shown and typed in
     */
    void prepare(Trip trip, ZoneId zone, InstantSource clock, Units distanceUnits) {
        var leaves = trip.departure().atZone(zone);
        var back = trip.returnTime().atZone(zone);
        start(zone, clock, distanceUnits, leaves.toLocalDate(), back.toLocalDate());
        departureDay.setValue(leaves.toLocalDate());
        departureTime.setText(SHOWN.format(leaves));
        returnDay.setValue(back.toLocalDate());
        returnTime.setText(SHOWN.format(back));
        distanceShown = messages.number(units.distance(trip.distanceKm()), 0);
        distanceKm = trip.distanceKm();
        distance.setText(distanceShown);
        canCharge.setSelected(trip.canChargeAtDestination());
        notWithThisCar = trip.notWithThisCar();
        departureDay.requestFocus();
    }

    /** Saves the trip as entered, or says why it cannot be saved. */
    void save() {
        var entered = new TripCheck.Entered(departureDay.getValue(), departureTime.getText(), returnDay.getValue(),
                returnTime.getText(), distance.getText(), canCharge.isSelected(), notWithThisCar);
        switch (check.check(entered)) {
            case TripCheck.Checked.Valid(var trip) -> {
                showProblem("");
                // a distance shown rounded, or in miles, stays as it was unless the owner typed another
                saved.accept(distanceShown.isEmpty() || !distanceShown.equals(distance.getText().strip()) ? trip
                        : new Trip(trip.departure(), trip.returnTime(), distanceKm, trip.canChargeAtDestination(),
                                trip.notWithThisCar()));
                closed.run();
            }
            case TripCheck.Checked.Refused(var why, var where) -> {
                showProblem(messages.text(why));
                ScreenReader.focus(field(where));
            }
        }
    }

    /**
     * Enters a trip as the owner does by hand.
     *
     * @param departure the day the trip leaves; one of the days offered
     * @param leaves the time it leaves as typed, such as "7:30"
     * @param returnsOn the day it is back; one of the days offered
     * @param back the time it is back as typed
     * @param far the distance there and back as typed, such as "120"
     * @param charging whether the car can charge at the destination
     */
    void enter(LocalDate departure, String leaves, LocalDate returnsOn, String back, String far, boolean charging) {
        departureDay.setValue(departure);
        departureTime.setText(leaves);
        returnDay.setValue(returnsOn);
        returnTime.setText(back);
        distance.setText(far);
        canCharge.setSelected(charging);
    }

    /**
     * Why the entered trip was not saved.
     *
     * @return the problem shown, or empty while there is none
     */
    String problem() {
        return problem.isVisible() ? problem.getText() : "";
    }

    /**
     * Offers the days from today, and those of the trip, and reads the times in the home's zone and the distance in
     * these units from now on.
     */
    private void start(ZoneId zone, InstantSource clock, Units distanceUnits, LocalDate first, LocalDate last) {
        if (distanceUnits != units) {
            showIn(distanceUnits);
        }
        check = new TripCheck(zone, clock, units, messages);
        today = clock.instant().atZone(zone).toLocalDate();
        var from = first.isBefore(today) ? first : today;
        var offeredUntil = today.plusDays(DAYS_OFFERED - 1L);
        var until = last.isAfter(offeredUntil) ? last : offeredUntil;
        var days = Stream.iterate(from, day -> !day.isAfter(until), day -> day.plusDays(1)).toList();
        departureDay.getItems().setAll(days);
        returnDay.getItems().setAll(days);
        showProblem("");
    }

    private void showIn(Units distanceUnits) {
        units = distanceUnits;
        unit.setText(units.distanceUnit());
        ScreenReader.name(distance, messages.text(TextKey.TRIP_DISTANCE_SPOKEN, units.distanceUnit()));
    }

    private Node field(TripCheck.Field where) {
        return switch (where) {
            case DEPARTURE_TIME -> departureTime;
            case RETURN_DAY -> returnDay;
            case RETURN_TIME -> returnTime;
            case DISTANCE -> distance;
        };
    }

    private Label label(TextKey text) {
        var label = Labels.line(messages.text(text), "fact");
        label.setMinWidth(USE_PREF_SIZE);
        return label;
    }

    private void showProblem(String text) {
        problem.setText(text);
        problem.setVisible(!text.isEmpty());
        problem.setManaged(!text.isEmpty());
        Stream.of(departureDay, departureTime, returnDay, returnTime, distance, canCharge)
                .forEach(node -> node.setAccessibleHelp(text));
        resized.run();
    }
}

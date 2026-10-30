package nl.enadeag.carchargeplanner.desktop.dashboard.trip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import nl.enadeag.carchargeplanner.desktop.AccessibleNames;
import nl.enadeag.carchargeplanner.desktop.Fx;
import nl.enadeag.carchargeplanner.desktop.FxTest;
import nl.enadeag.carchargeplanner.desktop.Keyboard;
import nl.enadeag.carchargeplanner.desktop.Scenes;
import nl.enadeag.carchargeplanner.desktop.app.Units;
import nl.enadeag.carchargeplanner.desktop.dashboard.Models;
import nl.enadeag.carchargeplanner.desktop.dashboard.day.DayModel;
import nl.enadeag.carchargeplanner.desktop.text.Language;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.trip.Trip;
import java.time.Instant;
import java.time.InstantSource;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javafx.application.ColorScheme;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@FxTest
class TripCardTest {

    private static final Messages ENGLISH = new Messages(Language.ENGLISH);
    private static final LocalDate SATURDAY = Models.TODAY.plusDays(1);

    private final List<Trip> added = new ArrayList<>();
    private final List<List<Trip>> changed = new ArrayList<>();
    private final List<Trip> removed = new ArrayList<>();
    private Stage stage;
    private TripCard card;

    @AfterEach
    void close() throws Exception {
        Fx.run(() -> stage.close());
    }

    private static Instant at(LocalDate day, int hour, int minute) {
        return day.atTime(hour, minute).atZone(Models.BRUSSELS).toInstant();
    }

    private void show(DayModel day, TripsModel trips) throws Exception {
        Fx.run(() -> {
            card = new TripCard(() -> { }, ENGLISH);
            card.onChange(added::add, (trip, changedTrip) -> changed.add(List.of(trip, changedTrip)), removed::add,
                    InstantSource.fixed(trips.madeAt()));
            card.show(day, trips);
            stage = Scenes.show(card, ColorScheme.LIGHT);
        });
    }

    private <T> T control(Class<T> type, String text) throws Exception {
        return Fx.call(() -> Scenes.all(card).stream().filter(type::isInstance)
                .filter(node -> node instanceof javafx.scene.control.Labeled labeled
                        && (text.equals(labeled.getText()) || text.equals(labeled.getAccessibleText())))
                .map(type::cast).findFirst().orElseThrow(() -> new AssertionError("no " + text)));
    }

    private void openEntry() throws Exception {
        var add = control(Button.class, ENGLISH.text(TextKey.TRIP_ADD));
        Fx.run(add::fire);
    }

    private boolean entering() throws Exception {
        return Fx.call(() -> Scenes.reallyVisible(card.editor()));
    }

    @Test
    void theDayShowsItsTripsWithTheirOriginAndChoices() throws Exception {
        show(Models.CLOUDY_TOMORROW, Models.TRIPS);

        assertThat(Fx.call(() -> Scenes.visibleTexts(card))).contains("Away 07:30 – 18:00 · 300 km there and back",
                "Entered by hand").doesNotContain("Away Sun 5 Jul 17:00 – Mon 6 Jul 09:30 · 120 km there and back");
        var notWithThisCar = control(CheckBox.class,
                "Not with this car (Away 07:30 – 18:00 · 300 km there and back)");
        var canCharge = control(CheckBox.class,
                "I can charge at the destination (Away 07:30 – 18:00 · 300 km there and back)");
        assertThat(Fx.call(notWithThisCar::isSelected)).isFalse();
        assertThat(Fx.call(canCharge::isSelected)).isFalse();
    }

    @Test
    void withoutTripsOnTheDayTheCardSaysThereAreNone() throws Exception {
        show(Models.SUNNY_TODAY, Models.TRIPS);

        assertThat(Fx.call(() -> Scenes.visibleTexts(card))).contains(ENGLISH.text(TextKey.TRIP_NONE));
    }

    @Test
    void eachChoiceChangesTheTripThroughTheEngineAndTakesTheFocusAgainWhenTheTripShowsChanged() throws Exception {
        show(Models.CLOUDY_TOMORROW, Models.TRIPS);
        var trip = Models.TRIP_SATURDAY;

        var notWithThisCar = control(CheckBox.class,
                "Not with this car (Away 07:30 – 18:00 · 300 km there and back)");
        Fx.run(notWithThisCar::fire);
        var withoutTheCar = new Trip(trip.departure(), trip.returnTime(), 300, false, true);
        assertThat(changed).containsExactly(List.of(trip, withoutTheCar));

        var listed = Models.TRIPS.trips().getFirst();
        var shownAgain = new TripsModel(Models.BRUSSELS, Models.TRIPS.madeAt(), Units.METRIC,
                List.of(new TripsModel.Listed(withoutTheCar, SATURDAY, SATURDAY, TripOrigin.BY_HAND, listed.text(),
                        listed.originText(), listed.changeSpoken(), listed.removeSpoken())));
        Fx.run(() -> card.show(Models.CLOUDY_TOMORROW, shownAgain));
        Fx.settle();
        var focused = Fx.call(() -> stage.getScene().getFocusOwner());
        assertThat(focused).isInstanceOf(CheckBox.class);
        assertThat(Fx.call(() -> ((CheckBox) focused).getText())).isEqualTo(ENGLISH.text(TextKey.TRIP_NOT_WITH_THIS_CAR));
        assertThat(Fx.call(() -> ((CheckBox) focused).isSelected())).isTrue();

        var canCharge = control(CheckBox.class,
                "I can charge at the destination (Away 07:30 – 18:00 · 300 km there and back)");
        Fx.run(canCharge::fire);
        assertThat(changed.getLast()).containsExactly(withoutTheCar,
                new Trip(trip.departure(), trip.returnTime(), 300, true, true));
    }

    @Test
    void removeRemovesTheTripAsTheModelGaveIt() throws Exception {
        show(Models.CLOUDY_TOMORROW, Models.TRIPS);

        Fx.run(control(Button.class, "Remove the trip Away 07:30 – 18:00 · 300 km there and back")::fire);

        assertThat(removed).containsExactly(Models.TRIP_SATURDAY);
    }

    @Test
    void aNewTripLeavesOnTheSelectedDay() throws Exception {
        show(Models.CLOUDY_TOMORROW, Models.TRIPS);

        openEntry();
        Fx.run(() -> {
            card.editor().enter(SATURDAY, "6:45", SATURDAY.plusDays(1), "22:10", "412", true);
            card.editor().save();
        });

        assertThat(added).containsExactly(new Trip(at(SATURDAY, 6, 45), at(SATURDAY.plusDays(1), 22, 10), 412, true));
        assertThat(entering()).isFalse();
    }

    @Test
    void theEntryStartsOnTheSelectedDayFromEightToSixAndOffersThreeWeeksFromToday() throws Exception {
        show(Models.GRID_SUNDAY, Models.TRIPS);

        openEntry();
        Fx.run(() -> {
            ((javafx.scene.control.TextField) card.editor().lookup(".trip-distance")).setText("10");
            card.editor().save();
        });
        var days = Fx.call(() -> Scenes.all(card).stream()
                .filter(node -> node instanceof javafx.scene.control.ComboBox<?>)
                .map(node -> ((javafx.scene.control.ComboBox<?>) node).getItems().size()).toList());

        var sunday = Models.TODAY.plusDays(2);
        assertThat(added).containsExactly(new Trip(at(sunday, 8, 0), at(sunday, 18, 0), 10, false));
        assertThat(days).containsExactly(TripEditor.DAYS_OFFERED, TripEditor.DAYS_OFFERED);
    }

    @Test
    void changeOpensTheEntryWithTheTripAndSavesTheChangedOneInItsPlace() throws Exception {
        var trip = new Trip(at(SATURDAY, 7, 30), at(SATURDAY, 18, 0), 300.4, true, true);
        var listed = Models.TRIPS.trips().getFirst();
        show(Models.CLOUDY_TOMORROW, new TripsModel(Models.BRUSSELS, Models.TRIPS.madeAt(), Units.METRIC,
                List.of(new TripsModel.Listed(trip, SATURDAY, SATURDAY, TripOrigin.BY_HAND, listed.text(),
                        listed.originText(), listed.changeSpoken(), listed.removeSpoken()))));

        Fx.run(control(Button.class, listed.changeSpoken())::fire);
        // only the return changes: the distance shown rounded stays as it was, and so do the choices
        Fx.run(() -> {
            Scenes.all(card.editor()).stream().filter(node -> node.getStyleClass().contains("trip-time"))
                    .map(javafx.scene.control.TextField.class::cast).toList().getLast().setText("19:00");
            card.editor().save();
        });

        assertThat(changed).containsExactly(List.of(trip, new Trip(at(SATURDAY, 7, 30), at(SATURDAY, 19, 0), 300.4,
                true, true)));
    }

    @Test
    void aTripInMilesIsShownAndSavedInMiles() throws Exception {
        var miles = new TripsModel(Models.BRUSSELS, Models.TRIPS.madeAt(), Units.IMPERIAL, List.of());
        show(Models.CLOUDY_TOMORROW, miles);

        openEntry();
        assertThat(Fx.call(() -> Scenes.visibleTexts(card))).contains("mi");
        Fx.run(() -> {
            card.editor().enter(SATURDAY, "8:00", SATURDAY, "18:00", "100", false);
            card.editor().save();
        });

        assertThat(added.getFirst().distanceKm()).isCloseTo(160.9344, within(1e-9));
    }

    @ParameterizedTest
    @CsvSource({
        "8:00, 18:00, far, TRIP_DISTANCE_NOT_A_NUMBER, 'Distance there and back, in km'",
        "8:00, 18:00, 0, TRIP_DISTANCE_NOT_POSITIVE, 'Distance there and back, in km'",
        "eight, 18:00, 10, PROBLEM_NOT_A_TIME, Time of departure",
        "8:00, six, 10, PROBLEM_NOT_A_TIME, Time of return",
        "18:00, 8:00, 10, TRIP_RETURN_BEFORE_DEPARTURE, Day of return",
    })
    void aTripThatCannotBeIsNotSavedAndTheFocusGoesWhereAScreenReaderReadsWhy(String leaves, String back,
            String distance, TextKey why, String field) throws Exception {
        show(Models.CLOUDY_TOMORROW, Models.TRIPS);
        openEntry();
        Fx.run(() -> card.editor().enter(SATURDAY, leaves, SATURDAY, back, distance, false));

        Keyboard.press(KeyCode.ENTER);

        assertThat(added).isEmpty();
        assertThat(entering()).isTrue();
        assertThat(Fx.call(() -> card.editor().problem())).isEqualTo(ENGLISH.text(why));
        var focused = Fx.call(() -> stage.getScene().getFocusOwner());
        assertThat(Fx.call(() -> AccessibleNames.of(focused))).isEqualTo(field);
        assertThat(Fx.call(() -> AccessibleNames.help(focused))).isEqualTo(ENGLISH.text(why));
    }

    @Test
    void aTripThatHasReturnedByTheTimeOfSavingIsRefused() throws Exception {
        show(Models.SUNNY_TODAY, Models.TRIPS); // made at 08:12

        openEntry();
        Fx.run(() -> {
            card.editor().enter(Models.TODAY, "6:00", Models.TODAY, "8:00", "40", false);
            card.editor().save();
        });

        assertThat(added).isEmpty();
        assertThat(Fx.call(() -> card.editor().problem())).isEqualTo(ENGLISH.text(TextKey.TRIP_PASSED));
    }

    @Test
    void escapeClosesTheEntryWithoutSavingAndAnotherDayClosesItToo() throws Exception {
        show(Models.CLOUDY_TOMORROW, Models.TRIPS);
        openEntry();
        Fx.run(() -> card.show(Models.CLOUDY_TOMORROW, Models.TRIPS));
        assertThat(entering()).isTrue();

        Fx.run(() -> card.editor().fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false,
                false, false)));
        assertThat(entering()).isFalse();

        openEntry();
        Fx.run(() -> card.show(Models.GRID_SUNDAY, Models.TRIPS));
        assertThat(entering()).isFalse();
        assertThat(added).isEmpty();
    }
}

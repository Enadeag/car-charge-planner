package io.github.enadeag.carchargeplanner.desktop;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.enadeag.carchargeplanner.engine.away.AwayPeriod;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import javafx.application.ColorScheme;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AwayCardTest {

    private static final Messages ENGLISH = new Messages(Language.ENGLISH);

    private final List<AwayPeriod> added = new ArrayList<>();
    private final List<AwayPeriod> removed = new ArrayList<>();
    private Stage stage;
    private AwayCard card;

    @AfterEach
    void close() throws Exception {
        Fx.run(() -> stage.close());
    }

    private void show(DayModel day, AwayModel away) throws Exception {
        Fx.run(() -> {
            card = new AwayCard(() -> { }, ENGLISH);
            card.onChange(added::add, removed::add);
            card.show(day, away);
            stage = Scenes.show(card, ColorScheme.LIGHT);
        });
    }

    private Button button(String text) throws Exception {
        return Fx.call(() -> card.lookupAll(".button").stream().map(Button.class::cast)
                .filter(button -> text.equals(button.getText()) || text.equals(button.getAccessibleText()))
                .findFirst().orElseThrow());
    }

    private void openEntry() throws Exception {
        var add = button(ENGLISH.text(TextKey.ONE_OFF_ADD));
        Fx.run(add::fire);
    }

    private boolean entering() throws Exception {
        return Fx.call(() -> Scenes.reallyVisible(card.editor()));
    }

    @Test
    void theWholeSelectedDayIsAddedByDefault() throws Exception {
        show(Models.CLOUDY_TOMORROW, Models.AWAY);

        openEntry();
        Fx.run(() -> card.editor().save());

        assertThat(added).containsExactly(AwayPeriod.allDay(Models.TODAY.plusDays(1), Models.BRUSSELS));
        assertThat(entering()).isFalse();
    }

    @Test
    void partOfTheDayIsAddedBetweenTheTypedTimes() throws Exception {
        show(Models.GRID_SUNDAY, Models.AWAY);

        openEntry();
        Fx.run(() -> {
            card.editor().enterTimes("9:15", "17:45");
            card.editor().save();
        });

        assertThat(added).containsExactly(AwayPeriod.on(Models.TODAY.plusDays(2), LocalTime.of(9, 15),
                LocalTime.of(17, 45), Models.BRUSSELS));
    }

    @ParameterizedTest
    @CsvSource({
        "16:00, 10:00, ONE_OFF_END_BEFORE_START",
        "10:00, 10:00, ONE_OFF_END_BEFORE_START",
        "ten, 12:00, PROBLEM_NOT_A_TIME",
        "10:00, 24:00, PROBLEM_NOT_A_TIME",
        "06:00, 07:00, ONE_OFF_PASSED",
    })
    void aPeriodThatCannotBeIsNotSavedAndTheEntrySaysWhy(String start, String end, TextKey why) throws Exception {
        show(Models.SUNNY_TODAY, Models.AWAY); // made at 08:12

        openEntry();
        Fx.run(() -> {
            card.editor().enterTimes(start, end);
            card.editor().save();
        });

        assertThat(added).isEmpty();
        assertThat(entering()).isTrue();
        assertThat(Fx.call(() -> card.editor().problem())).isEqualTo(ENGLISH.text(why));
    }

    @Test
    void escapeClosesTheEntryWithoutSaving() throws Exception {
        show(Models.SUNNY_TODAY, Models.AWAY);
        openEntry();

        Fx.run(() -> card.editor().fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false,
                false, false)));

        assertThat(entering()).isFalse();
        assertThat(added).isEmpty();
    }

    @Test
    void theEntryStaysOpenThroughANewPlanForTheSameDayAndClosesOnAnotherDay() throws Exception {
        show(Models.SUNNY_TODAY, Models.AWAY);
        openEntry();

        Fx.run(() -> card.show(Models.SUNNY_TODAY, Models.AWAY));
        assertThat(entering()).isTrue();

        Fx.run(() -> card.show(Models.CLOUDY_TOMORROW, Models.AWAY));
        assertThat(entering()).isFalse();
    }

    @Test
    void eachComingPeriodIsListedAndItsRemoveButtonRemovesIt() throws Exception {
        show(Models.SUNNY_TODAY, Models.AWAY);

        assertThat(Fx.call(() -> Scenes.visibleTexts(card))).contains("Tomorrow, all day", "Sun 5 Jul, 10:00 – 16:30");
        var remove = button("Remove Sun 5 Jul, 10:00 – 16:30");
        Fx.run(remove::fire);

        assertThat(removed).containsExactly(Models.AWAY_SUNDAY);
    }

    @Test
    void withoutComingPeriodsTheCardSaysThereAreNone() throws Exception {
        show(Models.SUNNY_TODAY, new AwayModel(Models.BRUSSELS, Models.AWAY.now(), List.of()));

        assertThat(Fx.call(() -> Scenes.visibleTexts(card))).contains(ENGLISH.text(TextKey.ONE_OFF_NONE));
    }
}

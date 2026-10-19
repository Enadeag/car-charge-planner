package nl.enadeag.carchargeplanner.desktop.dashboard.battery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import nl.enadeag.carchargeplanner.desktop.AccessibleNames;
import nl.enadeag.carchargeplanner.desktop.Fx;
import nl.enadeag.carchargeplanner.desktop.FxTest;
import nl.enadeag.carchargeplanner.desktop.Keyboard;
import nl.enadeag.carchargeplanner.desktop.Scenes;
import nl.enadeag.carchargeplanner.desktop.dashboard.Models;
import nl.enadeag.carchargeplanner.desktop.text.Language;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import java.util.ArrayList;
import java.util.List;
import javafx.application.ColorScheme;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.AccessibleAttribute;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@FxTest
class BatteryCardTest {

    private static final BatteryModel TODAY = Models.SUNNY_TODAY.battery();
    private static final BatteryModel TOMORROW = Models.CLOUDY_TOMORROW.battery();
    private static final Messages ENGLISH = new Messages(Language.ENGLISH);

    private final List<Integer> entered = new ArrayList<>();
    private Stage stage;
    private BatteryCard card;

    @AfterEach
    void close() throws Exception {
        Fx.run(() -> stage.close());
    }

    private void show(BatteryModel day, boolean canEnter) throws Exception {
        Fx.run(() -> {
            card = new BatteryCard(Fx.REDUCED_MOTION, new Messages(Language.ENGLISH));
            if (canEnter) {
                card.onEntered(entered::add);
            }
            card.show(day);
            stage = Scenes.show(card, ColorScheme.LIGHT);
            stage.getScene().getRoot().resize(400, 200);
            stage.getScene().getRoot().layout();
        });
    }

    private <T extends Node> List<T> parts(String styleClass, Class<T> type) throws Exception {
        return Fx.call(() -> card.bar().lookupAll("." + styleClass).stream().map(type::cast).toList());
    }

    private double barWidth() throws Exception {
        return Fx.call(() -> card.bar().getWidth());
    }

    private Button update() throws Exception {
        return Fx.call(() -> (Button) card.lookupAll(".button").stream()
                .filter(node -> ((Button) node).getText().equals("Update")).findFirst().orElseThrow());
    }

    private boolean editing() throws Exception {
        return Fx.call(() -> card.editor().isVisible());
    }

    @Test
    void theLevelsFillTheBarToTheirShareAndTheMarksSitAtMinimumAndTarget() throws Exception {
        show(TODAY, true);
        double width = barWidth();

        var level = parts("battery-level", Rectangle.class).getFirst();
        var after = parts("battery-after", Rectangle.class).getFirst();
        var marks = parts("battery-mark", Line.class);

        assertThat(width).isGreaterThan(300);
        assertThat(level.getWidth()).isCloseTo(width * 0.50, within(1e-9));
        assertThat(after.getWidth()).isCloseTo(width * 0.74, within(1e-9));
        assertThat(marks).extracting(Line::getStartX).containsExactly(width * 0.30, width * 0.80);
        assertThat(Scenes.visibleTexts(card)).contains("50 → 74 %", "min 30", "target 80");
    }

    @Test
    void aScreenReaderReadsTheBatteryAsText() throws Exception {
        show(TODAY, true);

        assertThat(Fx.call(() -> card.bar().queryAccessibleAttribute(AccessibleAttribute.TEXT))).isEqualTo(TODAY.spoken());
    }

    @Test
    void onTodayTheBarTellsAScreenReaderThatEnterOpensTheEntryAndOtherDaysDoNot() throws Exception {
        show(TODAY, true);
        var hint = new Messages(Language.ENGLISH).text(TextKey.BATTERY_UPDATE_HINT);
        assertThat(Fx.call(() -> AccessibleNames.help(card.bar()))).isEqualTo(hint);

        Fx.run(() -> card.show(TOMORROW));

        assertThat(Fx.call(() -> AccessibleNames.help(card.bar()))).isEmpty();
    }

    @Test
    void afterTheEntryClosesTheFocusIsBackOnUpdate() throws Exception {
        show(TODAY, true);
        Fx.run(update()::fire);

        Keyboard.press(stage.getScene(), KeyCode.ESCAPE);

        assertThat(Fx.call(() -> stage.getScene().getFocusOwner())).isSameAs(update());
    }

    @Test
    void todaysLevelIsEnteredWithUpdateAndSave() throws Exception {
        show(TODAY, true);
        assertThat(editing()).isFalse();

        var update = update();
        Fx.run(update::fire);
        assertThat(editing()).isTrue();
        assertThat(Fx.call(() -> card.editor().field().getValue())).isEqualTo(50);
        Fx.run(() -> {
            card.editor().field().getEditor().setText("63");
            card.editor().save();
        });

        assertThat(entered).containsExactly(63);
        assertThat(editing()).isFalse();
    }

    @Test
    void tappingTheBarOpensTheEntryAndEscapeClosesItWithoutSaving() throws Exception {
        show(TODAY, true);

        Fx.run(() -> card.bar().fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 10, 5, 10, 5, MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, false, false, false, null)));
        assertThat(editing()).isTrue();
        Fx.run(() -> card.editor().fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false,
                false, false)));

        assertThat(editing()).isFalse();
        assertThat(entered).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"lots", "6O", "50.5", " "})
    void aTypoKeepsTheEntryOpenSaysWhyAndSendsNothing(String typed) throws Exception {
        show(TODAY, true);
        Fx.run(update()::fire);

        Fx.run(() -> {
            card.editor().field().getEditor().setText(typed);
            card.editor().save();
        });

        var why = ENGLISH.text(TextKey.BATTERY_NOT_A_NUMBER);
        assertThat(entered).isEmpty();
        assertThat(editing()).isTrue();
        assertThat(Fx.call(() -> card.editor().problem())).isEqualTo(why);
        assertThat(Fx.call(() -> AccessibleNames.help(card.editor().field()))).isEqualTo(why);
    }

    @Test
    void aCorrectedTypoIsSavedAndTheReasonGoes() throws Exception {
        show(TODAY, true);
        Fx.run(update()::fire);
        Fx.run(() -> {
            card.editor().field().getEditor().setText("lots");
            card.editor().save();
        });

        Fx.run(() -> {
            card.editor().field().getEditor().setText("64");
            card.editor().save();
        });

        assertThat(entered).containsExactly(64);
        assertThat(Fx.call(() -> card.editor().problem())).isEmpty();
    }

    @Test
    void anotherDaysLevelCannotBeEntered() throws Exception {
        show(TOMORROW, true);

        var update = update();
        assertThat(Fx.call(update::isVisible)).isFalse();
        Fx.run(() -> card.bar().fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false,
                false, false)));
        assertThat(editing()).isFalse();
    }

    @Test
    void withoutSomewhereToSendItTheLevelCannotBeEntered() throws Exception {
        show(TODAY, false);

        var update = update();
        assertThat(Fx.call(update::isVisible)).isFalse();
    }

    @Test
    void movingToAnotherDayClosesTheEntry() throws Exception {
        show(TODAY, true);
        var update = update();
        Fx.run(update::fire);

        Fx.run(() -> card.show(TOMORROW));

        assertThat(editing()).isFalse();
    }

    @Test
    void underReducedMotionTheLevelsChangeAtOnceAndOtherwiseTheySlide() throws Exception {
        show(TODAY, true);
        Fx.run(() -> card.show(TOMORROW));
        assertThat(Fx.call(() -> card.bar().shownLevels())).containsExactly(62, 62);

        var motion = new SimpleBooleanProperty(false);
        var sliding = Fx.call(() -> {
            var bar = new BatteryBar(motion);
            bar.show(TODAY);
            bar.tween().finish();
            return bar;
        });
        var starting = Fx.call(() -> {
            sliding.show(TOMORROW);
            return sliding.shownLevels();
        });
        assertThat(starting).containsExactly(50, 74);
        assertThat(Fx.call(() -> sliding.tween().isRunning())).isTrue();
        Fx.run(() -> sliding.tween().finish());
        assertThat(Fx.call(sliding::shownLevels)).containsExactly(62, 62);
    }
}

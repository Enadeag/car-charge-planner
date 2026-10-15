package io.github.enadeag.carchargeplanner.desktop.settings;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.enadeag.carchargeplanner.desktop.Fx;
import io.github.enadeag.carchargeplanner.desktop.Language;
import io.github.enadeag.carchargeplanner.desktop.Scenes;
import io.github.enadeag.carchargeplanner.desktop.TextKey;
import io.github.enadeag.carchargeplanner.desktop.Units;
import io.github.enadeag.carchargeplanner.engine.away.RecurringAwayPeriod;
import io.github.enadeag.carchargeplanner.engine.config.Car;
import io.github.enadeag.carchargeplanner.engine.config.ConfigFile;
import io.github.enadeag.carchargeplanner.engine.config.EngineConfig;
import io.github.enadeag.carchargeplanner.engine.config.PanelArray;
import io.github.enadeag.carchargeplanner.engine.config.ProviderId;
import io.github.enadeag.carchargeplanner.engine.config.TypicalValues;
import io.github.enadeag.carchargeplanner.engine.setting.Setting;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The first-run set-up and running it again: step by step over Settings' cards, each step checked by the engine's
 * records, a forecast checked and the dashboard previewed before Finish saves it all at once.
 */
class SetUpViewTest {

    @TempDir
    Path folder;

    private SetUpFixture setUp;

    @BeforeEach
    void make() {
        setUp = new SetUpFixture(folder, Language.ENGLISH);
    }

    @AfterEach
    void close() throws Exception {
        setUp.close();
    }

    private EngineConfig stored() throws Exception {
        return new ConfigFile(setUp.configPath).read();
    }

    private void next(SetUpView view, int times) throws Exception {
        for (int i = 0; i < times; i++) {
            Fx.run(view::next);
        }
    }

    /** From the welcome to the location step, with the place found and chosen. */
    private SetUpView atSolar() throws Exception {
        var view = setUp.open();
        next(view, 2);
        setUp.choosePlace(view);
        next(view, 1);
        return view;
    }

    private void waitForText(SetUpView view, String text) throws Exception {
        SettingsFixture.waitFor(() -> SetUpFixture.texts(view).stream().anyMatch(shown -> shown.contains(text)));
    }

    @Test
    void theFirstRunGoesStepByStepAndFinishSavesWhatWasEnteredWithTheTypicalValuesTagged() throws Exception {
        var view = setUp.open();
        next(view, 1);
        Fx.run(() -> setUp.choose(view, TextKey.FIELD_UNITS, Units.IMPERIAL));
        next(view, 1);
        setUp.choosePlace(view);
        next(view, 1);
        Fx.run(() -> setUp.type(view, TextKey.FIELD_PANEL_SIZE, "6.2"));
        next(view, 1);
        Fx.run(() -> setUp.type(view, TextKey.FIELD_CAR_BATTERY, "75"));
        next(view, 3);
        Fx.run(() -> setUp.button(view, TextKey.AWAY_ADD).fire());
        next(view, 1);
        assertThat(Fx.call(view::step)).isEqualTo(SetUpStep.CHECK);
        waitForText(view, setUp.messages.text(TextKey.SETUP_CHECK_WORKS, setUp.provider.info().name(), 2));
        next(view, 1);
        waitForText(view, setUp.provider.info().attribution());

        next(view, 1);

        assertThat(setUp.finished).isEqualTo(1);
        assertThat(setUp.closed).isEqualTo(1);
        var config = stored();
        assertThat(config.system().location()).isEqualTo(SetUpFixture.PLACE.location());
        assertThat(config.system().timezone()).isEqualTo(SetUpFixture.PLACE.timezone());
        assertThat(config.system().panels()).extracting(PanelArray::kwp).containsExactly(6.2);
        assertThat(config.system().car()).isEqualTo(new Car(75, Car.TYPICAL.consumptionKwhPer100km(),
                Car.TYPICAL.kmPerDay()));
        assertThat(config.typical().system()).contains(Setting.CAR_CONSUMPTION, Setting.HOUSE_BASELOAD,
                Setting.CHARGER_MAX_CURRENT).doesNotContain(Setting.CAR_BATTERY);
        assertThat(config.typical().ofPanel(0)).contains(Setting.PANEL_TILT).doesNotContain(Setting.PANEL_SIZE);
        assertThat(config.away().recurring()).containsExactly(
                new RecurringAwayPeriod(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(17, 0)));
        assertThat(setUp.appSettings.read().units()).isEqualTo(Units.IMPERIAL);
    }

    @Test
    void theLocationStepWaitsForAPlace() throws Exception {
        var view = setUp.open();
        next(view, 2);

        next(view, 1);

        assertThat(Fx.call(view::step)).isEqualTo(SetUpStep.LOCATION);
        assertThat(Fx.call(() -> setUp.control(view, TextKey.FIELD_LATITUDE).getAccessibleHelp()))
                .startsWith(setUp.messages.text(TextKey.PROBLEM_NO_PLACE));
        assertThat(setUp.configPath).doesNotExist();
    }

    @Test
    void typedCoordinatesAreAPlaceToo() throws Exception {
        var view = setUp.open();
        next(view, 2);
        Fx.run(() -> {
            setUp.type(view, TextKey.FIELD_LATITUDE, "51.5");
            setUp.type(view, TextKey.FIELD_LONGITUDE, "4.25");
        });

        next(view, 1);

        assertThat(Fx.call(view::step)).isEqualTo(SetUpStep.SOLAR);
    }

    @Test
    void aValueTheRecordsRefuseKeepsTheOwnerOnItsStepWithTheProblemAtItsField() throws Exception {
        var view = atSolar();
        next(view, 2);
        Fx.run(() -> {
            setUp.type(view, TextKey.FIELD_MIN_CURRENT, "10");
            setUp.type(view, TextKey.FIELD_MAX_CURRENT, "8");
        });

        next(view, 1);

        assertThat(Fx.call(view::step)).isEqualTo(SetUpStep.CHARGER);
        assertThat(Fx.call(() -> setUp.control(view, TextKey.FIELD_MAX_CURRENT).getAccessibleHelp()))
                .startsWith(setUp.messages.text(TextKey.PROBLEM_BELOW_MINIMUM_CURRENT));
        assertThat(Fx.call(() -> SetUpFixture.texts(view))).contains(setUp.messages.text(TextKey.SETUP_REFUSED));
    }

    @Test
    void iDontKnowFillsInTypicalValuesAgainAndTagsThem() throws Exception {
        var view = atSolar();
        next(view, 1);
        Fx.run(() -> setUp.type(view, TextKey.FIELD_CAR_BATTERY, "75"));
        assertThat(Fx.call(() -> setUp.control(view, TextKey.FIELD_CAR_BATTERY).getAccessibleHelp()))
                .doesNotContain(setUp.messages.text(TextKey.TYPICAL_TAG));

        Fx.run(() -> setUp.button(view, TextKey.USE_TYPICAL).fire());

        assertThat(Fx.call(() -> ((TextField) setUp.field(view, TextKey.FIELD_CAR_BATTERY)).getText()))
                .isEqualTo(setUp.messages.number(Car.TYPICAL.usableBatteryKwh(), 1));
        assertThat(Fx.call(() -> setUp.control(view, TextKey.FIELD_CAR_BATTERY).getAccessibleHelp()))
                .contains(setUp.messages.text(TextKey.TYPICAL_TAG));
    }

    @Test
    void backKeepsWhatWasEntered() throws Exception {
        var view = atSolar();
        Fx.run(() -> setUp.type(view, TextKey.FIELD_PANEL_TILT, "20"));
        next(view, 1);

        Fx.run(() -> setUp.button(view, TextKey.SETUP_BACK).fire());

        assertThat(Fx.call(view::step)).isEqualTo(SetUpStep.SOLAR);
        assertThat(Fx.call(() -> ((TextField) setUp.field(view, TextKey.FIELD_PANEL_TILT)).getText())).isEqualTo("20");
    }

    @Test
    void leavingTheFirstRunAsksAndSavesNothing() throws Exception {
        var view = atSolar();

        Fx.run(view::cancel);
        assertThat(Fx.call(view::isAsking)).isTrue();
        assertThat(setUp.closed).isZero();
        Fx.run(() -> setUp.button(view, TextKey.SETUP_LEAVE).fire());

        assertThat(setUp.closed).isEqualTo(1);
        assertThat(setUp.finished).isZero();
        assertThat(setUp.configPath).doesNotExist();
        assertThat(setUp.appSettings.path()).doesNotExist();
    }

    @Test
    void cancelOnAnUntouchedWelcomeClosesAtOnce() throws Exception {
        var view = setUp.open();

        Fx.run(view::cancel);

        assertThat(setUp.closed).isEqualTo(1);
        assertThat(Fx.call(view::isAsking)).isFalse();
    }

    @Test
    void runAgainItStartsFromWhatIsStoredAndAnUnchangedFinishRewritesNothing() throws Exception {
        setUp.withExample();
        var before = Files.readString(setUp.configPath);
        var view = setUp.open();
        next(view, 2);
        assertThat(Fx.call(() -> ((TextField) setUp.field(view, TextKey.FIELD_LATITUDE)).getText()))
                .isEqualTo(setUp.messages.number(51.0, 4));

        setUp.preview(view);
        next(view, 1);

        assertThat(setUp.finished).isEqualTo(1);
        assertThat(Files.readString(setUp.configPath)).isEqualTo(before);
        assertThat(setUp.appSettings.path()).doesNotExist();
        assertThat(setUp.keysFile()).doesNotExist();
    }

    @Test
    void aFailedCheckOnlySaysSoAndFinishStillSaves() throws Exception {
        setUp.provider.failing = true;
        var view = atSolar();
        Fx.run(() -> view.show(SetUpStep.CHECK));
        waitForText(view, "no network");

        next(view, 1);
        waitForText(view, setUp.messages.text(TextKey.NO_FORECAST));
        next(view, 1);

        assertThat(stored().system().location()).isEqualTo(SetUpFixture.PLACE.location());
    }

    @Test
    void anApiKeyEnteredInSetUpIsUsedForTheCheckAndKeptAtFinish() throws Exception {
        var view = atSolar();
        Fx.run(() -> view.show(SetUpStep.FORECAST));
        Fx.run(() -> {
            var key = Scenes.all(view).stream().filter(node -> node instanceof TextField field
                    && field.isVisible() && SettingsFixture.named(field, setUp.messages.text(TextKey.PROVIDER_API_KEY)))
                    .map(TextField.class::cast).findFirst().orElseThrow();
            key.setText("TYPED-KEY");
        });
        next(view, 2);
        waitForText(view, setUp.messages.text(TextKey.SETUP_CHECK_WORKS, setUp.provider.info().name(), 2));
        next(view, 1);
        setUp.awaitPreview(view);
        next(view, 1);

        assertThat(setUp.provider.asked).contains(Map.of("api_key", "TYPED-KEY"));
        assertThat(setUp.engine.apiKeys().of(new ProviderId("open-meteo"))).containsEntry("api_key", "TYPED-KEY");
    }

    @Test
    void theProgressSaysWhichStepOfHowManyAndTheKeysWork() throws Exception {
        var view = atSolar();

        var progress = Fx.call(() -> Scenes.all(view).stream().filter(ProgressBar.class::isInstance)
                .map(ProgressBar.class::cast).findFirst().orElseThrow());
        assertThat(Fx.call(progress::getAccessibleText)).isEqualTo(setUp.messages.text(TextKey.SETUP_PROGRESS,
                SetUpStep.SOLAR.number(), SetUpStep.count(), setUp.messages.text(TextKey.CATEGORY_SOLAR)));
        assertThat(Fx.call(progress::getProgress)).isEqualTo((double) SetUpStep.SOLAR.number() / SetUpStep.count());
        assertThat(Fx.call(() -> setUp.button(view, TextKey.SETUP_NEXT).isDefaultButton())).isTrue();
        assertThat(Fx.call(() -> setUp.button(view, TextKey.SETUP_CANCEL).isCancelButton())).isTrue();
        // the focus is on the step's first field, ready to type
        assertThat(Fx.call(() -> view.getScene().getFocusOwner().getAccessibleText()))
                .isEqualTo(setUp.messages.text(TextKey.FIELD_PANEL_NAME));
    }

    @Test
    void finishWithAValueRefusedAfterGoingBackShowsThatStep() throws Exception {
        var view = atSolar();
        Fx.run(() -> view.show(SetUpStep.CHARGER));
        Fx.run(() -> {
            setUp.type(view, TextKey.FIELD_MIN_CURRENT, "10");
            setUp.type(view, TextKey.FIELD_MAX_CURRENT, "8");
        });
        setUp.preview(view);

        next(view, 1);

        assertThat(Fx.call(view::step)).isEqualTo(SetUpStep.CHARGER);
        assertThat(setUp.configPath).doesNotExist();
    }

    @Test
    void theHubHoldsTheHouseAndTheAwayTimesBothOptional() throws Exception {
        var view = atSolar();
        Fx.run(() -> view.show(SetUpStep.MORE));
        Fx.run(() -> setUp.type(view, TextKey.FIELD_BASELOAD, "0.35"));
        next(view, 1);
        setUp.preview(view);
        next(view, 1);

        var config = stored();
        assertThat(config.system().house().baseloadKw()).isEqualTo(0.35);
        assertThat(config.typical().system()).doesNotContain(Setting.HOUSE_BASELOAD);
        assertThat(config.away().recurring()).isEmpty();
        assertThat(config.typical().panels()).containsExactly(TypicalValues.PANEL_SETTINGS);
    }

    @Test
    void thePreviewShowsTheOwnersOwnPlanWithoutButtonsThatLeadElsewhere() throws Exception {
        var view = atSolar();
        setUp.preview(view);
        waitForText(view, setUp.provider.info().attribution());

        var buttons = Fx.call(() -> Scenes.all(view).stream().filter(Button.class::isInstance)
                .map(Button.class::cast).filter(Scenes::reallyVisible).map(Button::getText).toList());
        assertThat(buttons).doesNotContain(setUp.messages.text(TextKey.FOOTER_SETTINGS),
                setUp.messages.text(TextKey.FOOTER_AWAY));
        assertThat(setUp.configPath).doesNotExist();
        assertThat(List.of(setUp.provider.asked.size())).containsExactly(1);
    }
}

package nl.enadeag.carchargeplanner.desktop.settings.setup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nl.enadeag.carchargeplanner.desktop.Fx;
import nl.enadeag.carchargeplanner.desktop.FxTest;
import nl.enadeag.carchargeplanner.desktop.Scenes;
import nl.enadeag.carchargeplanner.desktop.app.Background;
import nl.enadeag.carchargeplanner.desktop.app.Units;
import nl.enadeag.carchargeplanner.desktop.settings.SettingsFixture;
import nl.enadeag.carchargeplanner.desktop.system.PosixFilePrivacy;
import nl.enadeag.carchargeplanner.desktop.text.Language;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.away.WeeklyAwayPeriod;
import nl.enadeag.carchargeplanner.engine.config.Car;
import nl.enadeag.carchargeplanner.engine.config.Charger;
import nl.enadeag.carchargeplanner.engine.config.ChargingHours;
import nl.enadeag.carchargeplanner.engine.config.ConfigFile;
import nl.enadeag.carchargeplanner.engine.config.EngineConfig;
import nl.enadeag.carchargeplanner.engine.config.HomeChargePower;
import nl.enadeag.carchargeplanner.engine.config.House;
import nl.enadeag.carchargeplanner.engine.config.PanelArray;
import nl.enadeag.carchargeplanner.engine.config.PlaceName;
import nl.enadeag.carchargeplanner.engine.config.PanelSize;
import nl.enadeag.carchargeplanner.engine.config.Phases;
import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import nl.enadeag.carchargeplanner.engine.config.TypicalValues;
import nl.enadeag.carchargeplanner.engine.secrets.KeychainException;
import nl.enadeag.carchargeplanner.engine.secrets.LockableKeychain;
import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting;
import nl.enadeag.carchargeplanner.engine.store.PrivateFiles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
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
@FxTest
class SetUpViewTest {

    private static final PrivateFiles FILES = new PrivateFiles(new PosixFilePrivacy());

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
        return new ConfigFile(setUp.configPath, FILES).read();
    }

    private void next(SetUpView view, int times) throws Exception {
        for (int i = 0; i < times; i++) {
            Fx.run(view::next);
        }
    }

    /** What a drop-down shows; its skin writes the prompt into its cell when it is laid out. */
    private List<String> shown(SetUpView view, TextKey choice) {
        var box = (ComboBox<?>) setUp.field(view, choice);
        box.layout();
        return Scenes.visibleTexts(box);
    }

    /**
     * From the welcome to the panels step, with the place found and chosen, the panels filled in, and 11 kW chosen on
     * the charger step, as an owner who went ahead to it and came back.
     */
    private SetUpView atSolar() throws Exception {
        var view = setUp.open();
        next(view, 2);
        setUp.choosePlace(view);
        next(view, 1);
        Fx.run(() -> {
            setUp.fillPanels(view);
            view.show(SetUpStep.CHARGER);
            setUp.choose(view, TextKey.FIELD_CHARGE_POWER, Optional.of(HomeChargePower.KW_11));
            view.show(SetUpStep.SOLAR);
        });
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
        next(view, 1);
        assertThat(Fx.call(view::step)).as("the panels step starts empty").isEqualTo(SetUpStep.SOLAR);
        assertThat(Fx.call(() -> List.of(shown(view, TextKey.FIELD_PANEL_DIRECTION),
                shown(view, TextKey.FIELD_PANEL_TILT)))).containsOnly(List.of(setUp.messages.text(TextKey.CHOOSE)));
        assertThat(Fx.call(() -> setUp.control(view, TextKey.FIELD_PANEL_COUNT).getAccessibleHelp()))
                .startsWith(setUp.messages.text(TextKey.PROBLEM_PANEL_COUNT));
        Fx.run(() -> {
            setUp.fillPanels(view);
            setUp.type(view, TextKey.FIELD_PANEL_POWER, "310");
        });
        next(view, 1);
        Fx.run(() -> setUp.type(view, TextKey.FIELD_CAR_BATTERY, "75"));
        assertThat(Fx.call(() -> SetUpFixture.field(view, setUp.messages.text(TextKey.FIELD_CAR_OTHER_DRIVING))))
                .as("set-up asks for the other driving").isNotNull();
        assertThatThrownBy(() -> Fx.call(() -> setUp.field(view, TextKey.FIELD_CAR_TRIP_SURCHARGE)))
                .as("set-up keeps the standard trip surcharge").hasMessageContaining("no field");
        next(view, 1);
        Fx.run(() -> setUp.choose(view, TextKey.FIELD_CHARGE_POWER, Optional.of(HomeChargePower.KW_11)));
        next(view, 2);
        Fx.run(() -> setUp.button(view, TextKey.AWAY_ADD).fire());
        next(view, 1);
        assertThat(Fx.call(view::step)).isEqualTo(SetUpStep.CHECK);
        waitForText(view, setUp.messages.text(TextKey.SET_UP_CHECK_WORKS, setUp.provider.info().name(), 2));
        next(view, 1);
        waitForText(view, setUp.provider.info().attribution());

        next(view, 1);

        assertThat(setUp.finished).isEqualTo(1);
        assertThat(setUp.closed).isEqualTo(1);
        var config = stored();
        assertThat(config.system().location()).isEqualTo(SetUpFixture.PLACE.location());
        assertThat(config.system().place()).contains(new PlaceName(SetUpFixture.PLACE.label()));
        assertThat(config.system().timezone()).isEqualTo(SetUpFixture.PLACE.timezone());
        assertThat(config.system().panels()).containsExactly(new PanelArray("",
                new PanelSize.Panels(20, OptionalInt.of(310), Optional.of(Year.of(2021))), 35, 0, 0.14));
        assertThat(config.system().panels()).extracting(PanelArray::kwp).containsExactly(6.2);
        assertThat(config.system().car()).isEqualTo(new Car(75, Car.TYPICAL.consumptionKwhPer100km(),
                Car.TYPICAL.kmPerDay()));
        assertThat(config.system().charger()).isEqualTo(new Charger(Phases.THREE, 6, 16, 230, 0.9));
        assertThat(config.typical().system()).contains(ConfigSetting.CAR_CONSUMPTION, ConfigSetting.HOUSE_BASELOAD,
                ConfigSetting.CHARGER_MIN_CURRENT, ConfigSetting.CHARGER_VOLTAGE, ConfigSetting.CHARGER_EFFICIENCY)
                .doesNotContain(ConfigSetting.CAR_BATTERY, ConfigSetting.CHARGER_PHASES,
                        ConfigSetting.CHARGER_MAX_CURRENT);
        assertThat(config.typical().ofPanel(0)).containsExactly(ConfigSetting.PANEL_LOSSES);
        assertThat(config.away().weekly()).containsExactly(
                new WeeklyAwayPeriod(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(17, 0)));
        assertThat(setUp.appSettings.read().units()).isEqualTo(Units.IMPERIAL);
        assertThat(config.planning().chargingHours()).isEqualTo(ChargingHours.STANDARD);
        assertThat(setUp.configPath).content().doesNotContain("chargingHours").doesNotContain("tripSurcharge");
        assertThat(config.system().car().tripSurcharge()).isEqualTo(Car.STANDARD_TRIP_SURCHARGE);
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
    void nextWithoutAPlaceOpensTheFoldedCoordinatesWithTheProblemAndTheRowLeavesThemOpen() throws Exception {
        var view = setUp.open();
        next(view, 2);
        Fx.run(() -> setUp.foldRow(view).fire());
        assertThat(Fx.call(() -> setUp.latitudeShown(view))).isFalse();

        next(view, 1);

        assertThat(Fx.call(view::step)).isEqualTo(SetUpStep.LOCATION);
        assertThat(Fx.call(() -> setUp.latitudeShown(view))).isTrue();
        assertThat(Fx.call(() -> setUp.latitudeHelp(view))).startsWith(noPlace());
        Fx.run(() -> setUp.foldRow(view).fire());
        assertThat(Fx.call(() -> setUp.latitudeShown(view))).isTrue();
    }

    private String noPlace() {
        return setUp.messages.text(TextKey.PROBLEM_NO_PLACE);
    }

    @Test
    void choosingAPlaceTakesTheProblemAwayClosesTheCoordinatesAndLetsTheRowFoldThemAgain() throws Exception {
        var view = setUp.open();
        next(view, 2);
        assertThat(Fx.call(() -> Scenes.visibleTexts(view))).contains("No place chosen yet");
        next(view, 1);

        setUp.choosePlace(view);

        assertThat(Fx.call(() -> Scenes.visibleTexts(view))).contains("Chosen place: " + SetUpFixture.PLACE.label());
        assertThat(Fx.call(() -> setUp.latitudeHelp(view))).doesNotContain(noPlace());
        assertThat(Fx.call(() -> setUp.latitudeShown(view))).isFalse();
        Fx.run(() -> setUp.foldRow(view).fire());
        assertThat(Fx.call(() -> setUp.latitudeShown(view))).isTrue();
        Fx.run(() -> setUp.foldRow(view).fire());
        assertThat(Fx.call(() -> setUp.latitudeShown(view))).isFalse();
    }

    @Test
    void typedCoordinatesTakeTheProblemAwayAndLetTheRowFoldThemAgain() throws Exception {
        var view = setUp.open();
        next(view, 2);
        next(view, 1);
        Fx.run(() -> {
            setUp.type(view, TextKey.FIELD_LATITUDE, "51.5");
            setUp.type(view, TextKey.FIELD_LONGITUDE, "4.25");
        });

        next(view, 1);
        Fx.run(() -> setUp.button(view, TextKey.SET_UP_BACK).fire());

        assertThat(Fx.call(view::step)).isEqualTo(SetUpStep.LOCATION);
        assertThat(Fx.call(() -> setUp.latitudeHelp(view))).doesNotContain(noPlace());
        assertThat(Fx.call(() -> setUp.latitudeShown(view))).isTrue();
        Fx.run(() -> setUp.foldRow(view).fire());
        assertThat(Fx.call(() -> setUp.latitudeShown(view))).isFalse();
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
        assertThat(Fx.call(() -> SetUpFixture.texts(view))).contains(setUp.messages.text(TextKey.SET_UP_REFUSED));
    }

    @Test
    void reachingAStepMarksNoFieldOfALaterOne() throws Exception {
        var view = atSolar();
        Fx.run(() -> view.show(SetUpStep.CHARGER));
        var maximum = Fx.call(() -> {
            setUp.type(view, TextKey.FIELD_MIN_CURRENT, "10");
            setUp.type(view, TextKey.FIELD_MAX_CURRENT, "8");
            return setUp.control(view, TextKey.FIELD_MAX_CURRENT);
        });

        Fx.run(() -> view.show(SetUpStep.WELCOME));

        assertThat(Fx.call(maximum::getAccessibleHelp))
                .doesNotContain(setUp.messages.text(TextKey.PROBLEM_BELOW_MINIMUM_CURRENT));
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

    /** The charger step's "More settings" row; on that step. */
    private Button moreRow(SetUpView view) {
        var name = setUp.messages.text(TextKey.MORE_SETTINGS);
        return Scenes.all(view).stream()
                .filter(node -> node instanceof Button button && button.getText().endsWith(" " + name)
                        && Scenes.reallyVisible(button))
                .map(Button.class::cast).findFirst().orElseThrow();
    }

    /** Whether the charger step shows its folded fields; on that step. */
    private boolean chargerFoldOpen(SetUpView view) {
        return Scenes.reallyVisible(setUp.control(view, TextKey.FIELD_MIN_CURRENT));
    }

    private String help(SetUpView view, TextKey field) {
        return setUp.control(view, field).getAccessibleHelp();
    }

    @Test
    void theChargerStepStartsWithChooseAndGoesOnOnlyOnceAPowerIsChosen() throws Exception {
        var view = setUp.open();
        Fx.run(() -> view.show(SetUpStep.CHARGER));
        assertThat(Fx.call(() -> shown(view, TextKey.FIELD_CHARGE_POWER)))
                .containsExactly(setUp.messages.text(TextKey.CHOOSE));
        assertThat(Fx.call(() -> chargerFoldOpen(view))).isFalse();

        next(view, 1);

        assertThat(Fx.call(view::step)).isEqualTo(SetUpStep.CHARGER);
        assertThat(Fx.call(() -> help(view, TextKey.FIELD_CHARGE_POWER)))
                .startsWith(setUp.messages.text(TextKey.PROBLEM_NOT_CHOSEN));
        assertThat(Fx.call(() -> chargerFoldOpen(view))).as("the problem is not under the fold").isFalse();
        Fx.run(() -> setUp.choose(view, TextKey.FIELD_CHARGE_POWER, Optional.of(HomeChargePower.KW_7_4)));
        next(view, 1);
        assertThat(Fx.call(view::step)).isEqualTo(SetUpStep.FORECAST);
    }

    @Test
    void theFoldedChargerFieldsStartAtTheirTypicalValuesTaggedAndIDontKnowNoLongerChoosesAPower() throws Exception {
        var view = setUp.open();
        Fx.run(() -> view.show(SetUpStep.CHARGER));
        Fx.run(() -> moreRow(view).fire());

        var typicalTag = setUp.messages.text(TextKey.TYPICAL_TAG);
        assertThat(Fx.call(() -> List.of(TextKey.FIELD_MIN_CURRENT, TextKey.FIELD_VOLTAGE, TextKey.FIELD_EFFICIENCY)
                .stream().map(field -> ((TextField) setUp.field(view, field)).getText()).toList()))
                .containsExactly("6", "230", "90");
        for (var field : List.of(TextKey.FIELD_MIN_CURRENT, TextKey.FIELD_VOLTAGE, TextKey.FIELD_EFFICIENCY)) {
            assertThat(Fx.call(() -> help(view, field))).as("%s", field).contains(typicalTag);
        }
        Fx.run(() -> setUp.button(view, TextKey.USE_TYPICAL).fire());
        assertThat(Fx.call(() -> shown(view, TextKey.FIELD_CHARGE_POWER)))
                .containsExactly(setUp.messages.text(TextKey.CHOOSE));
        Fx.run(() -> setUp.choose(view, TextKey.FIELD_CHARGE_POWER, Optional.of(HomeChargePower.KW_3_7)));
        assertThat(Fx.call(() -> help(view, TextKey.FIELD_MAX_CURRENT))).doesNotContain(typicalTag);
        assertThat(Fx.call(() -> help(view, TextKey.FIELD_PHASES))).doesNotContain(typicalTag);
        assertThat(Fx.call(() -> help(view, TextKey.FIELD_VOLTAGE))).as("still 230 V").contains(typicalTag);
    }

    @Test
    void otherInSetUpOpensTheFoldChangesNoValueAndLetsTheOwnerGoOn() throws Exception {
        var view = setUp.open();
        Fx.run(() -> {
            view.show(SetUpStep.CHARGER);
            setUp.choose(view, TextKey.FIELD_CHARGE_POWER, Optional.<HomeChargePower>empty());
        });
        assertThat(Fx.call(() -> chargerFoldOpen(view))).isTrue();
        assertThat(Fx.call(() -> shown(view, TextKey.FIELD_CHARGE_POWER))).containsExactly("Other");
        assertThat(Fx.call(() -> ((TextField) setUp.field(view, TextKey.FIELD_MAX_CURRENT)).getText()))
                .isEqualTo("16");

        next(view, 1);

        assertThat(Fx.call(view::step)).isEqualTo(SetUpStep.FORECAST);
    }

    @Test
    void aPowerWhoseMaximumEndsBelowATypedMinimumIsRefusedAtTheMaximumWithTheFoldOpen() throws Exception {
        var view = atSolar();
        Fx.run(() -> {
            view.show(SetUpStep.CHARGER);
            moreRow(view).fire();
            setUp.type(view, TextKey.FIELD_MIN_CURRENT, "20");
            moreRow(view).fire();
            setUp.choose(view, TextKey.FIELD_CHARGE_POWER, Optional.of(HomeChargePower.KW_3_7));
        });
        assertThat(Fx.call(() -> chargerFoldOpen(view))).isFalse();

        next(view, 1);

        assertThat(Fx.call(view::step)).isEqualTo(SetUpStep.CHARGER);
        assertThat(Fx.call(() -> chargerFoldOpen(view))).isTrue();
        assertThat(Fx.call(() -> help(view, TextKey.FIELD_MAX_CURRENT)))
                .startsWith(setUp.messages.text(TextKey.PROBLEM_BELOW_MINIMUM_CURRENT));
        assertThat(Fx.call(() -> ((TextField) setUp.field(view, TextKey.FIELD_MIN_CURRENT)).getText()))
                .as("the minimum is not changed silently").isEqualTo("20");
        Fx.run(() -> moreRow(view).fire());
        assertThat(Fx.call(() -> chargerFoldOpen(view))).as("held open while the problem shows").isTrue();
    }

    @Test
    void backKeepsWhatWasEntered() throws Exception {
        var view = atSolar();
        Fx.run(() -> setUp.type(view, TextKey.FIELD_PANEL_COUNT, "17"));
        next(view, 1);

        Fx.run(() -> setUp.button(view, TextKey.SET_UP_BACK).fire());

        assertThat(Fx.call(view::step)).isEqualTo(SetUpStep.SOLAR);
        assertThat(Fx.call(() -> ((TextField) setUp.field(view, TextKey.FIELD_PANEL_COUNT)).getText())).isEqualTo("17");
    }

    @Test
    void leavingTheFirstRunAsksAndSavesNothing() throws Exception {
        var view = atSolar();

        Fx.run(view::cancel);
        assertThat(Fx.call(view::isAskingToLeave)).isTrue();
        assertThat(setUp.closed).isZero();
        Fx.run(() -> setUp.button(view, TextKey.SET_UP_LEAVE).fire());

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
        assertThat(Fx.call(view::isAskingToLeave)).isFalse();
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
        waitForText(view, setUp.messages.text(TextKey.SET_UP_CHECK_WORKS, setUp.provider.info().name(), 2));
        next(view, 1);
        setUp.awaitPreview(view);
        next(view, 1);

        assertThat(setUp.provider.asked).contains(Map.of("api_key", "TYPED-KEY"));
        assertThat(setUp.engine.storedApiKeys().of(new ProviderId("open-meteo"))).containsEntry("api_key", "TYPED-KEY");
    }

    @Test
    void finishWhileTheKeychainStaysLockedSavesNothingAndSaysToUnlockItAndFinishAgain() throws Exception {
        var keychain = new LockableKeychain();
        keychain.locked = true;
        keychain.unlockFails = KeychainException.Kind.REFUSED;
        setUp = new SetUpFixture(folder, Language.ENGLISH, keychain, new Background(Runnable::run, Runnable::run));
        var view = atSolar();
        Fx.run(() -> view.show(SetUpStep.FORECAST));
        Fx.run(() -> setUp.button(view, TextKey.SECRET_REPLACE).fire());
        Fx.run(() -> Scenes.all(view).stream().filter(node -> node instanceof TextField field && field.isVisible()
                        && SettingsFixture.named(field, setUp.messages.text(TextKey.PROVIDER_API_KEY)))
                .map(TextField.class::cast).findFirst().orElseThrow().setText("TYPED-KEY"));
        Fx.run(() -> view.show(SetUpStep.CHECK));
        next(view, 1);
        setUp.awaitPreview(view);

        next(view, 1);

        assertThat(Fx.call(() -> SetUpFixture.texts(view)))
                .contains(setUp.messages.text(TextKey.SETTINGS_KEYCHAIN_LOCKED));
        assertThat(setUp.finished).isZero();
        assertThat(setUp.configPath).as("nothing after the keys is saved").doesNotExist();
        assertThat(setUp.keysFile()).doesNotExist();
    }

    @Test
    void theProgressSaysWhichStepOfHowManyAndTheKeysWork() throws Exception {
        var view = atSolar();

        var progress = Fx.call(() -> Scenes.all(view).stream().filter(ProgressBar.class::isInstance)
                .map(ProgressBar.class::cast).findFirst().orElseThrow());
        assertThat(Fx.call(progress::getAccessibleText)).isEqualTo(setUp.messages.text(TextKey.SET_UP_PROGRESS,
                SetUpStep.SOLAR.number(), SetUpStep.count(), setUp.messages.text(TextKey.CATEGORY_SOLAR)));
        assertThat(Fx.call(progress::getProgress)).isEqualTo((double) SetUpStep.SOLAR.number() / SetUpStep.count());
        assertThat(Fx.call(() -> setUp.button(view, TextKey.SET_UP_NEXT).isDefaultButton())).isTrue();
        assertThat(Fx.call(() -> setUp.button(view, TextKey.SET_UP_CANCEL).isCancelButton())).isTrue();
        // the focus is on the step's first field, ready to type
        assertThat(Fx.call(() -> view.getScene().getFocusOwner().getAccessibleText()))
                .isEqualTo(setUp.messages.text(TextKey.FIELD_PANEL_COUNT));
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
    void inDutchTheStepAfterTheForecastIsAfwezigheid() throws Exception {
        setUp.close();
        setUp = new SetUpFixture(folder, Language.DUTCH);
        var view = atSolar();

        Fx.run(() -> view.show(SetUpStep.AWAY));

        assertThat(Fx.call(() -> SetUpFixture.texts(view))).contains("Afwezigheid");
    }

    @Test
    void noStepAsksForTheHousesUseAndFinishSavesTheTypicalOneTagged() throws Exception {
        var view = atSolar();
        // the check and the preview ask for a forecast as they show, and ask for no value
        for (var step : List.of(SetUpStep.values()).subList(0, SetUpStep.CHECK.ordinal())) {
            Fx.run(() -> view.show(step));
            assertThatThrownBy(() -> Fx.call(() -> setUp.field(view, TextKey.FIELD_HOUSEHOLD_USE)))
                    .as("the %s step", step).hasMessageContaining("no field");
        }
        Fx.run(() -> view.show(SetUpStep.AWAY));
        assertThat(Fx.call(() -> SetUpFixture.texts(view))).contains("Away periods",
                setUp.messages.text(TextKey.SET_UP_AWAY_HINT));
        next(view, 1);
        setUp.preview(view);
        next(view, 1);

        var config = stored();
        assertThat(config.system().house()).isEqualTo(House.TYPICAL);
        assertThat(config.typical().system()).contains(ConfigSetting.HOUSE_BASELOAD);
        assertThat(config.away().weekly()).isEmpty();
        assertThat(config.typical().panels()).containsExactly(Set.of(ConfigSetting.PANEL_LOSSES));
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

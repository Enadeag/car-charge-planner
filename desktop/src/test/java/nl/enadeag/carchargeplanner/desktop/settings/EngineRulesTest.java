package nl.enadeag.carchargeplanner.desktop.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import nl.enadeag.carchargeplanner.desktop.Fx;
import nl.enadeag.carchargeplanner.desktop.FxTest;
import nl.enadeag.carchargeplanner.desktop.Scenes;
import nl.enadeag.carchargeplanner.desktop.app.Units;
import nl.enadeag.carchargeplanner.desktop.settings.page.Category;
import nl.enadeag.carchargeplanner.desktop.system.PosixFilePrivacy;
import nl.enadeag.carchargeplanner.desktop.text.Language;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.config.Car;
import nl.enadeag.carchargeplanner.engine.config.Charger;
import nl.enadeag.carchargeplanner.engine.config.ConfigFile;
import nl.enadeag.carchargeplanner.engine.config.Coordinates;
import nl.enadeag.carchargeplanner.engine.config.EngineConfig;
import nl.enadeag.carchargeplanner.engine.config.House;
import nl.enadeag.carchargeplanner.engine.config.PanelArray;
import nl.enadeag.carchargeplanner.engine.config.PanelSize;
import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.config.Phases;
import nl.enadeag.carchargeplanner.engine.config.PlanningPreferences;
import nl.enadeag.carchargeplanner.engine.config.TypicalValues;
import nl.enadeag.carchargeplanner.engine.store.PrivateFiles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.function.UnaryOperator;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The number fields of Settings follow the engine's rules only: a stored value the engine allows shows and saves as
 * it is, a typed value the engine refuses is refused at its field with the limit, and nothing is cut to a range.
 */
@FxTest
class EngineRulesTest {

    private static final PrivateFiles FILES = new PrivateFiles(new PosixFilePrivacy());

    private static final Clock CLOCK = Clock.systemUTC();

    @TempDir
    Path folder;

    private SettingsFixture settings;

    @AfterEach
    void close() throws Exception {
        settings.close();
    }

    /** The example config, changed and written back with nothing typical, before Settings opens. */
    private SettingsView openWith(Language language, UnaryOperator<EngineConfig> change) throws Exception {
        settings = new SettingsFixture(folder, language);
        var file = new ConfigFile(settings.configPath, FILES, CLOCK);
        var changed = change.apply(file.read());
        file.write(changed.withSystem(changed.system(), TypicalValues.NONE));
        return settings.open();
    }

    private SettingsView open() throws Exception {
        return openWith(Language.ENGLISH, config -> config);
    }

    private EngineConfig stored() throws Exception {
        return new ConfigFile(settings.configPath, FILES, CLOCK).read();
    }

    private static UnaryOperator<EngineConfig> car(Car car, House house) {
        return config -> config.withSystem(config.system().withHouseAndCar(house, car), config.typical());
    }

    private String text(SettingsView view, TextKey field) throws Exception {
        return Fx.call(() -> ((TextField) settings.field(view, field)).getText());
    }

    private String help(SettingsView view, TextKey field) throws Exception {
        return Fx.call(() -> SettingsFixture.shownWith(settings.control(view, field)));
    }

    private boolean visible(SettingsView view, TextKey field) throws Exception {
        return Fx.call(() -> Scenes.reallyVisible(settings.control(view, field)));
    }

    @SuppressWarnings("unchecked")
    private Spinner<Double> spinner(SettingsView view, TextKey field) {
        return (Spinner<Double>) settings.control(view, field);
    }

    private boolean typeAndSave(SettingsView view, TextKey field, String typed) throws Exception {
        return Fx.call(() -> {
            settings.type(view, field, typed);
            return SettingsFixture.save(view);
        });
    }

    @Test
    void aCarAndAHouseBeyondTheDesktopsOldRangesShowAndAreSavedAsTheyAre() throws Exception {
        var view = openWith(Language.ENGLISH, car(new Car(250, 100, 999), new House(25)));

        assertThat(text(view, TextKey.FIELD_CAR_BATTERY)).isEqualTo("250.0");
        assertThat(text(view, TextKey.FIELD_CAR_CONSUMPTION)).isEqualTo("100.0");
        assertThat(text(view, TextKey.FIELD_HOUSEHOLD_USE)).isEqualTo("600.0");
        assertThat(Fx.call(view::isChanged)).isFalse();
        assertThat(typeAndSave(view, TextKey.FIELD_CAR_TRIP_SURCHARGE, "20")).isTrue();

        assertThat(stored().system().car()).isEqualTo(new Car(250, 100, 999, new Percent(20)));
        assertThat(stored().system().house()).isEqualTo(new House(25));
    }

    @Test
    void inMilesADistanceNearTheEnginesEndShowsRoundedAndIsKeptAsItWas() throws Exception {
        var view = openWith(Language.ENGLISH, car(new Car(60, 18, 999), new House(0.5)));
        Fx.run(() -> settings.choose(view, TextKey.FIELD_UNITS, Units.IMPERIAL));

        assertThat(text(view, TextKey.FIELD_CAR_DAILY_DISTANCE)).isEqualTo("621");
        assertThat(typeAndSave(view, TextKey.FIELD_CAR_BATTERY, "61")).isTrue();

        assertThat(stored().system().car()).isEqualTo(new Car(61, 18, 999));
    }

    @Test
    void planningPreferencesBeyondTheDesktopsOldRangesAreSavedAsTheyAre() throws Exception {
        var planning = new PlanningPreferences(new Percent(30), new Percent(80), 0.005, 150, Duration.ofMinutes(2000),
                Duration.ofMinutes(15));
        var view = openWith(Language.ENGLISH, config -> config.withPlanning(planning));

        assertThat(text(view, TextKey.FIELD_MIN_WINDOW)).isEqualTo("2000");
        assertThat(text(view, TextKey.FIELD_IMPORT_TOLERANCE)).isEqualTo("150.00");
        assertThat(typeAndSave(view, TextKey.FIELD_TARGET_CHARGE, "85")).isTrue();

        assertThat(stored().planning()).isEqualTo(new PlanningPreferences(new Percent(30), new Percent(85), 0.005, 150,
                Duration.ofMinutes(2000), Duration.ofMinutes(15)));
    }

    @Test
    void lossesOfAlmostAllThePowerAreKeptWhenAnotherPageIsSaved() throws Exception {
        var array = new PanelArray("", new PanelSize.Peak(5), 35, 0, 0.995);
        var view = openWith(Language.ENGLISH,
                config -> config.withSystem(config.system().withPanels(List.of(array)), config.typical()));

        assertThat(typeAndSave(view, TextKey.FIELD_CAR_BATTERY, "61")).isTrue();

        assertThat(stored().system().panels()).containsExactly(array);
    }

    @Test
    void aChargersVoltageAndEfficiencyWithMoreDecimalsThanShownAreKeptAsTheyAre() throws Exception {
        var charger = new Charger(Phases.THREE, 8, 32, 231.46, 0.9375);
        var view = openWith(Language.ENGLISH,
                config -> config.withSystem(config.system().withCharger(charger), config.typical()));
        Fx.run(() -> view.show(Category.CHARGER));

        assertThat(text(view, TextKey.FIELD_VOLTAGE)).isEqualTo("231");
        assertThat(text(view, TextKey.FIELD_EFFICIENCY)).isEqualTo("94");
        assertThat(typeAndSave(view, TextKey.FIELD_MIN_CURRENT, "10")).isTrue();

        assertThat(stored().system().charger()).isEqualTo(new Charger(Phases.THREE, 10, 32, 231.46, 0.9375));
    }

    @Test
    void aLocationAtTheEnginesEndsWithMoreDecimalsThanShownIsKeptAsItIs() throws Exception {
        var location = new Coordinates(89.999_999, -179.999_999);
        var view = openWith(Language.ENGLISH, config -> config.withSystem(
                config.system().withUnnamedLocation(location, ZoneId.of("UTC")), config.typical()));
        Fx.run(() -> view.show(Category.LOCATION));

        assertThat(text(view, TextKey.FIELD_LATITUDE)).isEqualTo("90.0000");
        assertThat(text(view, TextKey.FIELD_LONGITUDE)).isEqualTo("-180.0000");
        assertThat(typeAndSave(view, TextKey.FIELD_LATITUDE, "52")).isTrue();

        assertThat(stored().system().location()).isEqualTo(new Coordinates(52, -179.999_999));
    }

    @Test
    void aWholeNumberTooLongForTheFieldIsRefusedWithItsLimit() throws Exception {
        var view = open();
        Fx.run(() -> view.show(Category.CHARGER));

        assertThat(typeAndSave(view, TextKey.FIELD_MAX_CURRENT, "123456789012")).isFalse();

        assertThat(help(view, TextKey.FIELD_MAX_CURRENT)).startsWith("Type a number from 1 to 80.");
    }

    @Test
    void threeDecimalsAreRefusedAsAThousandsSeparatorRatherThanReadAsOne() throws Exception {
        var view = openWith(Language.DUTCH, config -> config);
        var before = Files.readString(settings.configPath);

        assertThat(typeAndSave(view, TextKey.FIELD_CAR_BATTERY, "1.000")).isFalse();

        assertThat(help(view, TextKey.FIELD_CAR_BATTERY))
                .startsWith("Typ het getal zonder scheidingsteken voor duizendtallen.");
        assertThat(settings.configPath).hasContent(before);
    }

    @Test
    void aValueTheEngineRefusesIsShownWithItsLimitAndNothingIsWritten() throws Exception {
        var view = open();
        var before = Files.readString(settings.configPath);

        assertThat(typeAndSave(view, TextKey.FIELD_CAR_BATTERY, "1001")).isFalse();

        assertThat(help(view, TextKey.FIELD_CAR_BATTERY)).startsWith("Type a number from 1 to 1000.");
        assertThat(text(view, TextKey.FIELD_CAR_BATTERY)).isEqualTo("1001.0");
        assertThat(settings.configPath).hasContent(before);
        assertThat(typeAndSave(view, TextKey.FIELD_CAR_BATTERY, "1000")).isTrue();
        assertThat(stored().system().car().usableBatteryKwh()).isEqualTo(1000);
    }

    @Test
    void theLimitIsWrittenInTheOwnersLanguage() throws Exception {
        var view = openWith(Language.DUTCH, config -> config);

        assertThat(typeAndSave(view, TextKey.FIELD_EFFICIENCY, "0,5")).isFalse();

        assertThat(help(view, TextKey.FIELD_EFFICIENCY)).startsWith("Typ een getal van 1 tot en met 100.");
    }

    @Test
    void inMilesTheLimitIsNamedInMilesRoundedSoTheNamedEndIsAccepted() throws Exception {
        var view = open();
        Fx.run(() -> settings.choose(view, TextKey.FIELD_UNITS, Units.IMPERIAL));

        assertThat(typeAndSave(view, TextKey.FIELD_CAR_DAILY_DISTANCE, "622")).isFalse();
        assertThat(help(view, TextKey.FIELD_CAR_DAILY_DISTANCE)).startsWith("Type a number from 0 to 621.");
        assertThat(typeAndSave(view, TextKey.FIELD_CAR_CONSUMPTION, "62.14")).isFalse();
        assertThat(help(view, TextKey.FIELD_CAR_CONSUMPTION)).startsWith("Type a number from 0.32 to 62.13.");

        assertThat(Fx.call(() -> {
            settings.type(view, TextKey.FIELD_CAR_DAILY_DISTANCE, "621");
            settings.type(view, TextKey.FIELD_CAR_CONSUMPTION, "62.13");
            return SettingsFixture.save(view);
        })).isTrue();
        assertThat(stored().system().car().kmPerDay()).isCloseTo(999.402_624, within(1e-9));
        assertThat(stored().system().car().consumptionKwhPer100km()).isCloseTo(1.000_1, within(1e-4));
    }

    @Test
    void aTextThatIsNotANumberIsRefusedRatherThanTakenForTheValueBefore() throws Exception {
        var view = open();

        assertThat(typeAndSave(view, TextKey.FIELD_CAR_BATTERY, "sixty")).isFalse();

        assertThat(help(view, TextKey.FIELD_CAR_BATTERY)).startsWith("Type a number.");
        assertThat(text(view, TextKey.FIELD_CAR_BATTERY)).as("left as typed").isEqualTo("sixty");
    }

    @Test
    void aDecimalInAWholeNumberFieldIsRefusedRatherThanRounded() throws Exception {
        var view = open();
        Fx.run(() -> view.show(Category.CHARGER));

        assertThat(typeAndSave(view, TextKey.FIELD_MIN_CURRENT, "6.5")).isFalse();

        assertThat(help(view, TextKey.FIELD_MIN_CURRENT)).startsWith("Type a whole number, without decimals.");
        assertThat(visible(view, TextKey.FIELD_MIN_CURRENT)).as("the fold opens").isTrue();
        assertThat(typeAndSave(view, TextKey.FIELD_MIN_WINDOW, "30.5")).isFalse();
        assertThat(help(view, TextKey.FIELD_MIN_WINDOW)).startsWith("Type a whole number, without decimals.");
    }

    @Test
    void aCommaIsADecimalMarkInEnglishToo() throws Exception {
        var view = open();

        assertThat(typeAndSave(view, TextKey.FIELD_CAR_BATTERY, "62,5")).isTrue();

        assertThat(stored().system().car().usableBatteryKwh()).isEqualTo(62.5);
    }

    @Test
    void aPointIsADecimalMarkInDutchToo() throws Exception {
        var dutch = openWith(Language.DUTCH, config -> config);

        assertThat(typeAndSave(dutch, TextKey.FIELD_CAR_BATTERY, "0.5")).isFalse();
        assertThat(help(dutch, TextKey.FIELD_CAR_BATTERY)).as("read as 0.5, not 5")
                .startsWith("Typ een getal van 1 tot en met 1000.");
        assertThat(typeAndSave(dutch, TextKey.FIELD_CAR_BATTERY, "64.5")).isTrue();
        assertThat(stored().system().car().usableBatteryKwh()).isEqualTo(64.5);
    }

    @Test
    void aProblemGoesAsSoonAsItsFieldIsEdited() throws Exception {
        var view = open();
        assertThat(typeAndSave(view, TextKey.FIELD_CAR_BATTERY, "0")).isFalse();
        assertThat(help(view, TextKey.FIELD_CAR_BATTERY)).startsWith("Type a number from 1 to 1000.");

        Fx.run(() -> settings.type(view, TextKey.FIELD_CAR_BATTERY, "1"));

        assertThat(help(view, TextKey.FIELD_CAR_BATTERY)).doesNotContain("Type a number");
    }

    @Test
    void aFoldedFieldThatIsRefusedOpensItsFoldWithItsLimit() throws Exception {
        var view = open();

        assertThat(typeAndSave(view, TextKey.FIELD_HOUSEHOLD_USE, "-1")).isFalse();
        assertThat(help(view, TextKey.FIELD_HOUSEHOLD_USE)).startsWith("Type a number of 0 or more.");
        assertThat(visible(view, TextKey.FIELD_HOUSEHOLD_USE)).isTrue();

        Fx.run(() -> settings.type(view, TextKey.FIELD_HOUSEHOLD_USE, "12"));
        assertThat(typeAndSave(view, TextKey.FIELD_CAR_TRIP_SURCHARGE, "150")).isFalse();
        assertThat(help(view, TextKey.FIELD_CAR_TRIP_SURCHARGE)).startsWith("Type a number from 0 to 100.");
        assertThat(visible(view, TextKey.FIELD_CAR_TRIP_SURCHARGE)).isTrue();
    }

    @Test
    void thePlanningsFoldedFieldsSayTheirLimitsAndOpenTheFold() throws Exception {
        var view = open();

        assertThat(typeAndSave(view, TextKey.FIELD_MARGIN, "0")).isFalse();
        assertThat(help(view, TextKey.FIELD_MARGIN)).startsWith("Type a number more than 0 and at most 100.");
        assertThat(visible(view, TextKey.FIELD_MARGIN)).isTrue();

        Fx.run(() -> settings.type(view, TextKey.FIELD_MARGIN, "85"));
        assertThat(typeAndSave(view, TextKey.FIELD_MIN_WINDOW, "0")).isFalse();
        assertThat(help(view, TextKey.FIELD_MIN_WINDOW)).startsWith("Type a number more than 0.");
    }

    @Test
    void theLossesAndTheLatitudeSayTheirLimits() throws Exception {
        var view = open();

        assertThat(typeAndSave(view, TextKey.FIELD_PANEL_LOSSES, "100")).isFalse();
        assertThat(help(view, TextKey.FIELD_PANEL_LOSSES)).startsWith("Type a number of 0 or more and below 100.");
        assertThat(visible(view, TextKey.FIELD_PANEL_LOSSES)).isTrue();

        Fx.run(() -> settings.type(view, TextKey.FIELD_PANEL_LOSSES, "99.5"));
        assertThat(typeAndSave(view, TextKey.FIELD_LATITUDE, "91")).isFalse();
        assertThat(help(view, TextKey.FIELD_LATITUDE)).startsWith("Type a number from -90 to 90.");
        assertThat(visible(view, TextKey.FIELD_LATITUDE)).isTrue();
    }

    @Test
    void theArrowsStopAtTheEnginesEndAndGoOnWhereItHasNone() throws Exception {
        var view = openWith(Language.ENGLISH, car(new Car(999.5, 18, 40), new House(25)));

        Fx.run(() -> {
            spinner(view, TextKey.FIELD_CAR_BATTERY).increment();
            spinner(view, TextKey.FIELD_CAR_BATTERY).increment();
            spinner(view, TextKey.FIELD_HOUSEHOLD_USE).increment();
        });

        assertThat(text(view, TextKey.FIELD_CAR_BATTERY)).isEqualTo("1000.0");
        assertThat(text(view, TextKey.FIELD_HOUSEHOLD_USE)).isEqualTo("601.0");
        Fx.run(() -> {
            settings.type(view, TextKey.FIELD_MARGIN, "5");
            spinner(view, TextKey.FIELD_MARGIN).decrement();
            spinner(view, TextKey.FIELD_MARGIN).decrement();
        });
        assertThat(text(view, TextKey.FIELD_MARGIN)).as("more than 0, so 1 at no decimals").isEqualTo("1");
    }
}

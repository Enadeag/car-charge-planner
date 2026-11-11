package nl.enadeag.carchargeplanner.desktop.settings;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.desktop.Fx;
import nl.enadeag.carchargeplanner.desktop.FxTest;
import nl.enadeag.carchargeplanner.desktop.Scenes;
import nl.enadeag.carchargeplanner.desktop.app.AppSettings;
import nl.enadeag.carchargeplanner.desktop.app.Appearance;
import nl.enadeag.carchargeplanner.desktop.app.DisplayMode;
import nl.enadeag.carchargeplanner.desktop.app.Units;
import nl.enadeag.carchargeplanner.desktop.settings.page.Category;
import nl.enadeag.carchargeplanner.desktop.settings.page.Fold;
import nl.enadeag.carchargeplanner.desktop.system.PosixFilePrivacy;
import nl.enadeag.carchargeplanner.desktop.text.Language;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.config.Car;
import nl.enadeag.carchargeplanner.engine.config.Charger;
import nl.enadeag.carchargeplanner.engine.config.CompassPoint;
import nl.enadeag.carchargeplanner.engine.config.ConfigFile;
import nl.enadeag.carchargeplanner.engine.config.Coordinates;
import nl.enadeag.carchargeplanner.engine.config.EngineConfig;
import nl.enadeag.carchargeplanner.engine.config.PanelArray;
import nl.enadeag.carchargeplanner.engine.config.PanelSize;
import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.config.Phases;
import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import nl.enadeag.carchargeplanner.engine.config.TiltChoice;
import nl.enadeag.carchargeplanner.engine.config.TypicalValues;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastProvider;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastStep;
import nl.enadeag.carchargeplanner.engine.forecast.Place;
import nl.enadeag.carchargeplanner.engine.forecast.PlaceResults;
import nl.enadeag.carchargeplanner.engine.forecast.PlaceSearch;
import nl.enadeag.carchargeplanner.engine.forecast.ProviderInfo;
import nl.enadeag.carchargeplanner.engine.forecast.SolarSite;
import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting;
import nl.enadeag.carchargeplanner.engine.store.PrivateFiles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.Year;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The system's categories: panel arrays, location, car and house, charger, and their typical values. */
@FxTest
class SystemPagesTest {

    private static final PrivateFiles FILES = new PrivateFiles(new PosixFilePrivacy());

    private static final PanelArray EXAMPLE_ARRAY = new PanelArray("South roof",
            new PanelSize.Panels(20, OptionalInt.of(250), Optional.of(Year.of(2013))), 35, 0, 0.14);

    @TempDir
    Path folder;

    private SettingsFixture settings;

    @AfterEach
    void close() throws Exception {
        settings.close();
    }

    private SettingsView open() throws Exception {
        settings = new SettingsFixture(folder, Language.ENGLISH);
        return settings.open();
    }

    private EngineConfig stored() throws Exception {
        return new ConfigFile(settings.configPath, FILES).read();
    }

    /** The example config, changed and written back before Settings opens. */
    private SettingsView openWith(java.util.function.UnaryOperator<EngineConfig> change) throws Exception {
        settings = new SettingsFixture(folder, Language.ENGLISH);
        var file = new ConfigFile(settings.configPath, FILES);
        file.write(change.apply(file.read()));
        return settings.open();
    }

    private Button button(SettingsView view, TextKey text, int which) {
        return view.pages().stream().flatMap(page -> Scenes.all(page.view()).stream())
                .filter(node -> node instanceof Button button && button.getText().equals(settings.messages.text(text)))
                .map(Button.class::cast).toList().get(which);
    }

    private List<TextField> fields(SettingsView view, TextKey name) {
        var label = settings.messages.text(name);
        return view.pages().stream().flatMap(page -> Scenes.all(page.view()).stream())
                .filter(node -> SettingsFixture.named(node, label) && node instanceof TextField)
                .map(TextField.class::cast).toList();
    }

    @SuppressWarnings("unchecked")
    private <T> List<ComboBox<T>> choices(SettingsView view, TextKey name) {
        var label = settings.messages.text(name);
        return view.pages().stream().flatMap(page -> Scenes.all(page.view()).stream())
                .filter(node -> SettingsFixture.named(node, label) && node instanceof ComboBox<?>)
                .map(node -> (ComboBox<T>) node).toList();
    }

    private List<String> texts(SettingsView view) throws Exception {
        return Fx.call(() -> view.pages().stream().flatMap(page -> Scenes.visibleTexts(page.view()).stream())
                .toList());
    }

    @Test
    void anAddedArrayStartsEmptyAndIsStoredWithWhatWasEnteredAndItsTypicalLosses() throws Exception {
        var view = open();

        var refused = Fx.call(() -> {
            button(view, TextKey.PANELS_ADD, 0).fire();
            return !SettingsFixture.save(view);
        });
        assertThat(refused).as("a new array needs its panels").isTrue();
        assertThat(Fx.call(() -> choices(view, TextKey.FIELD_PANEL_DIRECTION).get(1).getValue())).isNull();
        Fx.call(() -> {
            fields(view, TextKey.FIELD_PANEL_COUNT).get(1).setText("8");
            this.<CompassPoint>choices(view, TextKey.FIELD_PANEL_DIRECTION).get(1).setValue(CompassPoint.EAST);
            this.<TiltChoice>choices(view, TextKey.FIELD_PANEL_TILT).get(1).setValue(TiltChoice.FLAT);
            fields(view, TextKey.FIELD_PANEL_POWER).get(1).setText("400");
            fields(view, TextKey.FIELD_PANEL_NAME).get(1).setText("Garage");
            return SettingsFixture.save(view);
        });

        var config = stored();
        assertThat(config.system().panels()).containsExactly(EXAMPLE_ARRAY, new PanelArray("Garage",
                new PanelSize.Panels(8, OptionalInt.of(400), Optional.empty()), 13, -90, 0.14));
        assertThat(config.typical().ofPanel(0)).isEmpty();
        assertThat(config.typical().ofPanel(1)).containsExactly(ConfigSetting.PANEL_LOSSES);
    }

    @Test
    void aStoredPeakPowerAndDegreesBetweenTheChoicesStayUntilTheOwnerReplacesThem() throws Exception {
        var stored = new PanelArray("Shed", 4.2, 30, -100, 0.1);
        var view = openWith(config -> config.withSystem(config.system().withPanels(List.of(stored)),
                TypicalValues.NONE));

        assertThat(texts(view)).contains("Now set: 4.2 kWp. Enter the number of panels to replace this.");
        assertThat(Fx.call(() -> choices(view, TextKey.FIELD_PANEL_DIRECTION).get(0).getValue()))
                .isEqualTo(CompassPoint.EAST);
        assertThat(Fx.call(() -> choices(view, TextKey.FIELD_PANEL_TILT).get(0).getValue()))
                .isEqualTo(TiltChoice.PITCHED);
        assertThat(Fx.call(view::isChanged)).as("showing the nearest choice changes nothing").isFalse();

        Fx.call(() -> {
            fields(view, TextKey.FIELD_PANEL_NAME).get(0).setText("Shed roof");
            return SettingsFixture.save(view);
        });
        assertThat(stored().system().panels()).containsExactly(new PanelArray("Shed roof", 4.2, 30, -100, 0.1));

        Fx.call(() -> {
            fields(view, TextKey.FIELD_PANEL_COUNT).get(0).setText("10");
            fields(view, TextKey.FIELD_PANEL_YEAR).get(0).setText("2021");
            this.<TiltChoice>choices(view, TextKey.FIELD_PANEL_TILT).get(0).setValue(TiltChoice.STEEP);
            return SettingsFixture.save(view);
        });
        assertThat(stored().system().panels()).containsExactly(new PanelArray("Shed roof",
                new PanelSize.Panels(10, OptionalInt.empty(), Optional.of(Year.of(2021))), 50, -100, 0.1));
    }

    @Test
    void theSumAndTheEstimateFollowTheFieldsAtOnce() throws Exception {
        var view = open();

        Fx.run(() -> {
            fields(view, TextKey.FIELD_PANEL_COUNT).get(0).setText("10");
            fields(view, TextKey.FIELD_PANEL_POWER).get(0).setText("");
            fields(view, TextKey.FIELD_PANEL_YEAR).get(0).setText("2021");
        });

        assertThat(texts(view)).contains("Together about 3.6 kWp");
        assertThat(Fx.call(() -> fields(view, TextKey.FIELD_PANEL_POWER).get(0).getPromptText())).isEqualTo("360");
        assertThat(Fx.call(() -> settings.control(view, TextKey.FIELD_PANEL_POWER).getAccessibleHelp()))
                .contains("the app then counts on 360 Wp, usual for 2021");
        Fx.run(() -> fields(view, TextKey.FIELD_PANEL_YEAR).get(0).setText("2023"));
        assertThat(texts(view)).contains("Together about 4.0 kWp");
    }

    @Test
    void theLossesAndTheNameAreFoldedUnderMoreSettingsUntilItsRowIsPressed() throws Exception {
        var view = open();
        var fold = Fx.call(() -> view.pages().stream().flatMap(page -> Scenes.all(page.view()).stream())
                .filter(Fold.class::isInstance).map(Fold.class::cast).findFirst().orElseThrow());
        assertThat(texts(view)).doesNotContain("System losses").contains("▸ More settings");

        Fx.run(() -> Scenes.all(fold).stream().filter(Button.class::isInstance).map(Button.class::cast).findFirst()
                .orElseThrow().fire());

        assertThat(Fx.call(fold::isOpen)).isTrue();
        assertThat(texts(view)).contains("System losses", "▾ More settings");
    }

    @Test
    void removingAnArrayMovesTheLaterOnesUpWithTheirTypicalValues() throws Exception {
        var view = openWith(config -> config.withSystem(config.system().withPanels(List.of(
                new PanelArray("East", 3, 30, -90, 0.14), new PanelArray("West", 3, 30, 90, 0.14),
                new PanelArray("Garage", 1, 10, 0, 0.1))),
                new TypicalValues(Set.of(), List.of(Set.of(), Set.of(ConfigSetting.PANEL_TILT)))));

        Fx.call(() -> {
            button(view, TextKey.PANELS_REMOVE, 0).fire();
            return SettingsFixture.save(view);
        });

        var config = stored();
        assertThat(config.system().panels()).extracting(PanelArray::name).containsExactly("West", "Garage");
        assertThat(config.typical().panels()).containsExactly(Set.of(ConfigSetting.PANEL_TILT), Set.of());
    }

    @Test
    void enteringAValueOfOnesOwnTakesItsTypicalTagAway() throws Exception {
        var view = openWith(config -> config.withSystem(config.system(),
                new TypicalValues(Set.of(ConfigSetting.CAR_BATTERY, ConfigSetting.CAR_CONSUMPTION), List.of())));

        Fx.call(() -> {
            settings.type(view, TextKey.FIELD_CAR_BATTERY, "75");
            return SettingsFixture.save(view);
        });

        var config = stored();
        assertThat(config.system().car().usableBatteryKwh()).isEqualTo(75);
        assertThat(config.typical().system()).containsExactly(ConfigSetting.CAR_CONSUMPTION);
    }

    @Test
    void aChangedCarKeepsTheTripSurchargeTheCardDoesNotShow() throws Exception {
        var view = openWith(config -> {
            var system = config.system();
            var car = system.car();
            var surcharged = new Car(car.usableBatteryKwh(), car.consumptionKwhPer100km(), car.kmPerDay(), new Percent(20));
            return config.withSystem(system.withHouseAndCar(system.house(), surcharged), config.typical());
        });

        Fx.call(() -> {
            settings.type(view, TextKey.FIELD_CAR_BATTERY, "75");
            return SettingsFixture.save(view);
        });

        assertThat(stored().system().car()).isEqualTo(new Car(75, 18, 40, new Percent(20)));
    }

    @Test
    void theTripSurchargeShowsTheStoredOneAndSavesAnother() throws Exception {
        var view = openWith(config -> withSurcharge(config, new Percent(20)));
        assertThat(Fx.call(() -> ((TextField) settings.field(view, TextKey.FIELD_CAR_TRIP_SURCHARGE)).getText()))
                .isEqualTo("20");

        Fx.call(() -> {
            settings.type(view, TextKey.FIELD_CAR_TRIP_SURCHARGE, "25");
            return SettingsFixture.save(view);
        });

        assertThat(stored().system().car()).isEqualTo(new Car(60, 18, 40, new Percent(25)));
        assertThat(settings.configPath).content().containsPattern("\"tripSurchargePct\"\\s*:\\s*25");
    }

    @Test
    void aTripSurchargeOutsideZeroToHundredIsBroughtIntoThatRange() throws Exception {
        var view = open();

        Fx.call(() -> {
            settings.type(view, TextKey.FIELD_CAR_TRIP_SURCHARGE, "150");
            return SettingsFixture.save(view);
        });
        assertThat(stored().system().car().tripSurcharge()).isEqualTo(new Percent(100));

        Fx.call(() -> {
            settings.type(view, TextKey.FIELD_CAR_TRIP_SURCHARGE, "-5");
            return SettingsFixture.save(view);
        });
        assertThat(stored().system().car().tripSurcharge()).isEqualTo(new Percent(0));
    }

    @Test
    void theStandardTripSurchargeStaysOutOfTheFileWhenAnotherValueIsSaved() throws Exception {
        var view = open();
        assertThat(Fx.call(() -> ((TextField) settings.field(view, TextKey.FIELD_CAR_TRIP_SURCHARGE)).getText()))
                .isEqualTo("15");

        Fx.call(() -> {
            settings.type(view, TextKey.FIELD_CAR_BATTERY, "75");
            return SettingsFixture.save(view);
        });

        assertThat(stored().system().car()).isEqualTo(new Car(75, 18, 40));
        assertThat(settings.configPath).content().doesNotContain("tripSurcharge");
    }

    @Test
    void changingTheTripSurchargeBackToTheStandardTakesItOutOfTheFile() throws Exception {
        var view = openWith(config -> withSurcharge(config, new Percent(20)));

        Fx.call(() -> {
            settings.type(view, TextKey.FIELD_CAR_TRIP_SURCHARGE, "15");
            return SettingsFixture.save(view);
        });

        assertThat(settings.configPath).content().doesNotContain("tripSurcharge");
    }

    private static EngineConfig withSurcharge(EngineConfig config, Percent surcharge) {
        var system = config.system();
        var car = system.car();
        return config.withSystem(system.withHouseAndCar(system.house(),
                new Car(car.usableBatteryKwh(), car.consumptionKwhPer100km(), car.kmPerDay(), surcharge)),
                config.typical());
    }

    @Test
    void iDontKnowFillsTheCardWithTypicalValuesTaggedAsSuch() throws Exception {
        var view = openWith(config -> config.withSystem(config.system().withCharger(
                new Charger(Phases.THREE, 8, 32, 400, 0.95)),
                TypicalValues.NONE));

        Fx.call(() -> {
            button(view, TextKey.USE_TYPICAL, 2).fire(); // the car, the house, the charger
            return SettingsFixture.save(view);
        });

        var config = stored();
        assertThat(config.system().charger()).isEqualTo(Charger.TYPICAL);
        assertThat(config.typical().system()).containsExactlyInAnyOrder(ConfigSetting.CHARGER_PHASES,
                ConfigSetting.CHARGER_MIN_CURRENT, ConfigSetting.CHARGER_MAX_CURRENT, ConfigSetting.CHARGER_VOLTAGE,
                ConfigSetting.CHARGER_EFFICIENCY);
    }

    @Test
    void typicalValuesAskedForAreForgottenOnCancel() throws Exception {
        var view = open();
        var before = Files.readString(settings.configPath);

        Fx.run(() -> {
            button(view, TextKey.USE_TYPICAL, 1).fire();
            view.cancel();
            settings.button(view, TextKey.SETTINGS_DISCARD).fire();
        });

        assertThat(settings.configPath).hasContent(before);
    }

    @Test
    void aMaximumBelowTheMinimumCurrentIsShownAtTheMaximum() throws Exception {
        var view = open();

        boolean saved = Fx.call(() -> {
            settings.type(view, TextKey.FIELD_MIN_CURRENT, "16");
            settings.type(view, TextKey.FIELD_MAX_CURRENT, "10");
            return SettingsFixture.save(view);
        });

        assertThat(saved).isFalse();
        assertThat(Fx.call(view::shown)).isEqualTo(Category.CHARGER);
        assertThat(Fx.call(() -> settings.control(view, TextKey.FIELD_MAX_CURRENT).getAccessibleHelp()))
                .startsWith(settings.messages.text(TextKey.PROBLEM_BELOW_MINIMUM_CURRENT));
    }

    @Test
    void distancesShowInTheChosenUnitsAndAreKeptMetric() throws Exception {
        var view = open();

        Fx.run(() -> settings.choose(view, TextKey.FIELD_UNITS, Units.IMPERIAL));
        assertThat(Fx.call(() -> ((TextField) settings.field(view, TextKey.FIELD_CAR_OTHER_DRIVING)).getText()))
                .isEqualTo("25"); // 40 km
        Fx.call(() -> {
            settings.type(view, TextKey.FIELD_CAR_OTHER_DRIVING, "50");
            return SettingsFixture.save(view);
        });

        var car = stored().system().car();
        assertThat(car.kmPerDay()).isCloseTo(80.4672, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(car.consumptionKwhPer100km()).as("not touched, so exactly as it was").isEqualTo(18);
    }

    @Test
    void switchingUnitsBackAndForthDoesNotChangeAnEditedValue() throws Exception {
        var view = open();

        Fx.call(() -> {
            settings.type(view, TextKey.FIELD_CAR_CONSUMPTION, "17.3");
            settings.choose(view, TextKey.FIELD_UNITS, Units.IMPERIAL);
            settings.choose(view, TextKey.FIELD_UNITS, Units.METRIC);
            return SettingsFixture.save(view);
        });

        assertThat(stored().system().car().consumptionKwhPer100km()).isEqualTo(17.3);
        assertThat(stored().system().car().kmPerDay()).isEqualTo(40);
    }

    @Test
    void aFoundPlaceFillsInTheCoordinatesAndTheTimeZone() throws Exception {
        var utrecht = new Place("Utrecht", new Coordinates(52.09083, 5.12222), ZoneId.of("Europe/Amsterdam"),
                "Utrecht", "Netherlands", "");
        settings = new SettingsFixture(folder, Language.ENGLISH, List.of(new SearchOnly(utrecht)));
        var view = settings.open();

        Fx.run(() -> {
            var text = (TextField) settings.field(view, TextKey.PLACE_FIELD);
            text.setText("Utrecht");
            text.fireEvent(new javafx.event.ActionEvent());
        });
        SettingsFixture.waitFor(() -> view.pages().stream().flatMap(page -> Scenes.all(page.view()).stream())
                .anyMatch(node -> node instanceof Button button && button.getText().equals("Utrecht, Netherlands")));
        Fx.call(() -> {
            view.pages().stream().flatMap(page -> Scenes.all(page.view()).stream())
                    .filter(node -> node instanceof Button button && button.getText().equals("Utrecht, Netherlands"))
                    .map(Button.class::cast).findFirst().orElseThrow().fire();
            return SettingsFixture.save(view);
        });

        var system = stored().system();
        assertThat(system.location()).isEqualTo(new Coordinates(52.09083, 5.12222));
        assertThat(system.timezone()).isEqualTo(ZoneId.of("Europe/Amsterdam"));
    }

    @Test
    void withoutAPlaceSearchTheOwnerIsToldToTypeTheCoordinates() throws Exception {
        var view = open();

        Fx.run(() -> {
            view.show(Category.LOCATION);
            ((TextField) settings.field(view, TextKey.PLACE_FIELD)).setText("Utrecht");
            settings.button(view, TextKey.PLACE_SEARCH).fire();
        });

        SettingsFixture.waitFor(() -> Scenes.visibleTexts(view).contains(settings.messages.text(TextKey.PLACE_NO_SEARCH)));
    }

    @Test
    void typicalValuesAreTaggedNextToTheirFields() throws Exception {
        var view = openWith(config -> config.withSystem(config.system(),
                new TypicalValues(Set.of(ConfigSetting.HOUSE_BASELOAD), List.of())));
        Fx.run(() -> view.show(Category.CAR_HOUSE));

        assertThat(Fx.call(() -> settings.control(view, TextKey.FIELD_BASELOAD).getAccessibleHelp()))
                .contains(settings.messages.text(TextKey.TYPICAL_TAG));
        assertThat(Fx.call(() -> settings.control(view, TextKey.FIELD_CAR_BATTERY).getAccessibleHelp()))
                .doesNotContain(settings.messages.text(TextKey.TYPICAL_TAG));
    }

    @Test
    void typicalValuesStayTaggedInImperialUnits() throws Exception {
        settings = new SettingsFixture(folder, Language.ENGLISH);
        var file = new ConfigFile(settings.configPath, FILES);
        var example = file.read();
        file.write(example.withSystem(example.system(), new TypicalValues(Set.of(ConfigSetting.CAR_CONSUMPTION), List.of())));
        settings.appSettings.write(new AppSettings(Optional.empty(), Units.IMPERIAL, DisplayMode.AUTOMATIC, Appearance.SYSTEM));
        var view = settings.open();

        assertThat(Fx.call(() -> settings.control(view, TextKey.FIELD_CAR_CONSUMPTION).getAccessibleHelp()))
                .contains(settings.messages.text(TextKey.TYPICAL_TAG));
        assertThat(Fx.call(view::isChanged)).isFalse();
    }

    /** A provider with only a place search, which always finds the one place. */
    private record SearchOnly(Place place) implements ForecastProvider {

        @Override
        public ProviderInfo info() {
            return new ProviderInfo(new ProviderId("open-meteo"), "Search only", "", "https://example.org", "", "",
                    Duration.ofHours(1), Duration.ofDays(1), Duration.ofHours(1), List.of());
        }

        @Override
        public List<ForecastStep> fetch(SolarSite system, Map<String, String> values, Instant now) {
            return List.of();
        }

        @Override
        public Optional<PlaceSearch> placeSearch() {
            return Optional.of((text, language) -> new PlaceResults.Found(List.of(place)));
        }
    }
}

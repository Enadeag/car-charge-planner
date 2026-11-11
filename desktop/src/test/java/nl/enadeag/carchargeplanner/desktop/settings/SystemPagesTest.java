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
import nl.enadeag.carchargeplanner.engine.config.PlaceName;
import nl.enadeag.carchargeplanner.engine.config.EngineConfig;
import nl.enadeag.carchargeplanner.engine.config.House;
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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.Year;
import java.time.ZoneId;
import java.time.ZoneOffset;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
        return openWith(Clock.systemUTC(), change);
    }

    private SettingsView openWith(Clock clock, java.util.function.UnaryOperator<EngineConfig> change)
            throws Exception {
        settings = new SettingsFixture(folder, Language.ENGLISH, clock);
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

    /** What a drop-down shows; its skin writes the prompt into its cell when it is laid out. */
    private static List<String> shown(ComboBox<?> box) {
        box.applyCss();
        box.layout();
        return Scenes.visibleTexts(box);
    }

    private List<String> texts(SettingsView view) throws Exception {
        return Fx.call(() -> shownTexts(view));
    }

    /** On the JavaFX thread. */
    private static List<String> shownTexts(SettingsView view) {
        return view.pages().stream().flatMap(page -> Scenes.visibleTexts(page.view()).stream()).toList();
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
        assertThat(Fx.call(() -> List.of(shown(choices(view, TextKey.FIELD_PANEL_DIRECTION).get(1)),
                shown(choices(view, TextKey.FIELD_PANEL_TILT).get(1)))))
                .containsOnly(List.of(settings.messages.text(TextKey.CHOOSE)));
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
    void aStoredYearAfterThisYearSavesAsItWasUntilTheOwnerChangesTheSize() throws Exception {
        var future = new PanelArray("Shed", new PanelSize.Panels(10, OptionalInt.empty(), Optional.of(Year.of(2027))),
                35, 0, 0.14);
        var view = openWith(Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"), ZoneOffset.UTC),
                config -> config.withSystem(config.system().withPanels(List.of(future)), TypicalValues.NONE));

        var savedUntouched = Fx.call(() -> {
            fields(view, TextKey.FIELD_PANEL_NAME).get(0).setText("Shed roof");
            return SettingsFixture.save(view);
        });
        assertThat(savedUntouched).isTrue();
        assertThat(stored().system().panels()).containsExactly(new PanelArray("Shed roof", future.size(), 35, 0, 0.14));

        var savedTouched = Fx.call(() -> {
            fields(view, TextKey.FIELD_PANEL_COUNT).get(0).setText("12");
            return SettingsFixture.save(view);
        });
        assertThat(savedTouched).isFalse();
        assertThat(texts(view)).contains("Type the year placed: from 1990 up to and including this year.");
    }

    @Test
    void thisYearIsTheYearInTheDevicesTimeZoneNotInUtc() throws Exception {
        // 23:30 UTC on 31 December is already 1 January in Amsterdam, the fixture's device zone
        settings = new SettingsFixture(folder, Language.ENGLISH,
                Clock.fixed(Instant.parse("2026-12-31T23:30:00Z"), ZoneOffset.UTC));
        var view = settings.open();
        assertThat(settings.stores(false).thisYear()).isEqualTo(Year.of(2027));

        var saved = Fx.call(() -> {
            fields(view, TextKey.FIELD_PANEL_YEAR).get(0).setText("2027");
            return SettingsFixture.save(view);
        });

        assertThat(saved).isTrue();
        assertThat(((PanelSize.Panels) stored().system().panels().getFirst().size()).placed())
                .contains(Year.of(2027));
    }

    @Test
    void northChosenIsStoredAs180() throws Exception {
        var view = open();

        Fx.call(() -> {
            this.<CompassPoint>choices(view, TextKey.FIELD_PANEL_DIRECTION).get(0).setValue(CompassPoint.NORTH);
            return SettingsFixture.save(view);
        });

        assertThat(stored().system().panels().getFirst().azimuthDeg()).isEqualTo(180);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-180, 180})
    void aStoredNorthShowsAsNorthAndIsWrittenBackAsItWas(double azimuthDeg) throws Exception {
        var view = openWith(config -> config.withSystem(config.system().withPanels(List.of(
                new PanelArray("Shed", 4.2, 35, azimuthDeg, 0.14))), TypicalValues.NONE));
        assertThat(Fx.call(() -> choices(view, TextKey.FIELD_PANEL_DIRECTION).get(0).getValue()))
                .isEqualTo(CompassPoint.NORTH);

        Fx.call(() -> {
            fields(view, TextKey.FIELD_PANEL_NAME).get(0).setText("Shed roof");
            return SettingsFixture.save(view);
        });

        assertThat(stored().system().panels().getFirst().azimuthDeg()).isEqualTo(azimuthDeg);
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

    private static final Place UTRECHT = new Place("Utrecht", new Coordinates(52.09083, 5.12222),
            ZoneId.of("Europe/Amsterdam"), "Utrecht", "Netherlands", "");

    private static final String EXAMPLE_PLACE = "Chosen place: Zonnedorp, Belgium";

    /** Settings on the location page, with a primary provider whose place search finds this. */
    private SettingsView openSearching(PlaceSearch search) throws Exception {
        settings = new SettingsFixture(folder, Language.ENGLISH, List.of(new SearchOnly(search)));
        var view = settings.open();
        showLocation(view);
        return view;
    }

    /** Shows the location page, laid out as the next pulse would, so its parts are in the scene. */
    private static void showLocation(SettingsView view) throws Exception {
        Fx.run(() -> {
            view.show(Category.LOCATION);
            view.applyCss();
            view.layout();
        });
    }

    private SettingsView openFinding(Place place) throws Exception {
        return openSearching((text, language) -> new PlaceResults.Found(List.of(place)));
    }

    private void search(SettingsView view) {
        var text = (TextField) settings.field(view, TextKey.PLACE_FIELD);
        text.setText("Utrecht");
        text.fireEvent(new javafx.event.ActionEvent());
    }

    private void choose(SettingsView view, String label) throws Exception {
        Fx.run(() -> search(view));
        SettingsFixture.waitFor(() -> shownTexts(view).contains(label));
        Fx.run(() -> button(view, label).fire());
    }

    private Button button(SettingsView view, String text) {
        return view.pages().stream().flatMap(page -> Scenes.all(page.view()).stream())
                .filter(node -> node instanceof Button button && button.getText().equals(text))
                .map(Button.class::cast).findFirst().orElseThrow();
    }

    /** The fold's row, whose text is the arrow and the fold's name. */
    private Optional<Button> foldRow(SettingsView view) {
        var name = settings.messages.text(TextKey.CARD_COORDINATES);
        return view.pages().stream().flatMap(page -> Scenes.all(page.view()).stream())
                .filter(node -> node instanceof Button button && button.getText().endsWith(" " + name)
                        && Scenes.reallyVisible(button))
                .map(Button.class::cast).findFirst();
    }

    private boolean coordinatesShown(SettingsView view) {
        return Scenes.reallyVisible(settings.control(view, TextKey.FIELD_LATITUDE));
    }

    @Test
    void aFoundPlaceShowsAtTheTopAndIsSavedWithItsNameItsCoordinatesAndItsTimeZone() throws Exception {
        var view = openFinding(UTRECHT);

        choose(view, "Utrecht, Netherlands");

        assertThat(texts(view)).contains("Chosen place: Utrecht, Netherlands", "Search to choose another place")
                .doesNotContain(EXAMPLE_PLACE);
        assertThat(Fx.call(() -> ((TextField) settings.field(view, TextKey.PLACE_FIELD)).getText())).isEmpty();
        assertThat(Fx.call(() -> coordinatesShown(view))).isFalse();
        assertThat(Fx.call(() -> SettingsFixture.save(view))).isTrue();
        var system = stored().system();
        assertThat(system.place()).contains(new PlaceName("Utrecht, Netherlands"));
        assertThat(system.location()).isEqualTo(new Coordinates(52.09083, 5.12222));
        assertThat(system.timezone()).isEqualTo(ZoneId.of("Europe/Amsterdam"));
    }

    @Test
    void aStoredPlaceShowsAtTheTopWithTheCoordinatesFoldedAndOpensThemOnThePressOfTheRow() throws Exception {
        var view = openFinding(UTRECHT);

        assertThat(texts(view)).contains(EXAMPLE_PLACE, "Search to choose another place");
        assertThat(Fx.call(() -> coordinatesShown(view))).isFalse();
        Fx.run(() -> foldRow(view).orElseThrow().fire());
        assertThat(Fx.call(() -> coordinatesShown(view))).isTrue();
        assertThat(Fx.call(view::isChanged)).isFalse();
    }

    /** Settings whose home has no place chosen yet, on the location page, with a search that finds this. */
    private SettingsView openUnnamedFinding(Place place) throws Exception {
        settings = new SettingsFixture(folder, Language.ENGLISH, List.of(new SearchOnly(
                (text, language) -> new PlaceResults.Found(List.of(place)))));
        var file = new ConfigFile(settings.configPath, FILES);
        var example = file.read();
        file.write(example.withSystem(example.system().withUnnamedLocation(Coordinates.NOT_CHOSEN,
                example.system().timezone()), example.typical()));
        var view = settings.open();
        showLocation(view);
        return view;
    }

    private void pressFoldRow(SettingsView view) throws Exception {
        Fx.run(() -> foldRow(view).orElseThrow().fire());
    }

    @Test
    void withoutAPlaceTheTopSaysSoAndTheCoordinatesAreOpen() throws Exception {
        var view = openUnnamedFinding(UTRECHT);

        assertThat(texts(view)).contains("No place chosen yet").doesNotContain("Search to choose another place");
        assertThat(Fx.call(() -> coordinatesShown(view))).isTrue();
    }

    @Test
    void typingADigitAndPuttingItBackKeepsTheNameAndARealDifferenceDropsItAtOnce() throws Exception {
        var view = openFinding(UTRECHT);
        Fx.run(() -> foldRow(view).orElseThrow().fire());

        Fx.run(() -> settings.type(view, TextKey.FIELD_LATITUDE, "51.0001"));
        assertThat(texts(view)).contains("No place chosen yet").doesNotContain(EXAMPLE_PLACE,
                "Search to choose another place");
        Fx.run(() -> settings.type(view, TextKey.FIELD_LATITUDE, "51.00000"));
        assertThat(texts(view)).contains(EXAMPLE_PLACE);

        Fx.run(() -> settings.type(view, TextKey.FIELD_LONGITUDE, "5.0001"));
        assertThat(Fx.call(() -> SettingsFixture.save(view))).isTrue();
        assertThat(stored().system().place()).isEmpty();
        assertThat(stored().system().location()).isEqualTo(new Coordinates(51.0, 5.0001));
        assertThat(texts(view)).contains("No place chosen yet");
    }

    @Test
    void inDutchADecimalCommaWithMoreDecimalsKeepsTheNameAndADifferenceAtTheFourthDecimalDropsIt() throws Exception {
        settings = new SettingsFixture(folder, Language.DUTCH, List.of(new SearchOnly(
                (text, language) -> new PlaceResults.Found(List.of(UTRECHT)))));
        var view = settings.open();
        showLocation(view);
        pressFoldRow(view);

        Fx.run(() -> settings.type(view, TextKey.FIELD_LATITUDE, "51,00004"));
        assertThat(texts(view)).contains("Gekozen plaats: Zonnedorp, Belgium")
                .doesNotContain("Nog geen plaats gekozen");

        Fx.run(() -> settings.type(view, TextKey.FIELD_LATITUDE, "51,0001"));
        assertThat(texts(view)).contains("Nog geen plaats gekozen")
                .doesNotContain("Gekozen plaats: Zonnedorp, Belgium");
    }

    @Test
    void anotherPlaceAtTheSameCoordinatesIsAChangeThatSavesItsName() throws Exception {
        var view = openFinding(new Place("Zonnedorp", new Coordinates(51.0, 5.0), ZoneId.of("Europe/Brussels"),
                "Westland", "Belgium", ""));

        assertThat(Fx.call(() -> Scenes.visibleTexts(view))).contains("Close").doesNotContain("Cancel");

        choose(view, "Zonnedorp, Westland, Belgium");

        assertThat(Fx.call(() -> Scenes.visibleTexts(view))).contains("Cancel").doesNotContain("Close");
        assertThat(Fx.call(view::isChanged)).isTrue();
        assertThat(Fx.call(() -> SettingsFixture.save(view))).isTrue();
        assertThat(stored().system().place()).contains(new PlaceName("Zonnedorp, Westland, Belgium"));
    }

    @Test
    void savingAnotherPageKeepsThePlaceName() throws Exception {
        var view = openFinding(UTRECHT);

        Fx.call(() -> {
            view.show(Category.CAR_HOUSE);
            settings.type(view, TextKey.FIELD_CAR_BATTERY, "64");
            return SettingsFixture.save(view);
        });

        assertThat(stored().system().car().usableBatteryKwh()).isEqualTo(64);
        assertThat(stored().system().place()).contains(new PlaceName("Zonnedorp, Belgium"));
        assertThat(stored().system().location()).isEqualTo(new Coordinates(51.0, 5.0));
    }

    @Test
    void whileTheResultsOfANewSearchShowTheChosenPlaceStaysAtTheTop() throws Exception {
        var view = openFinding(UTRECHT);

        Fx.run(() -> search(view));

        SettingsFixture.waitFor(() -> shownTexts(view).contains("Utrecht, Netherlands"));
        assertThat(texts(view)).contains(EXAMPLE_PLACE, "Search to choose another place")
                .doesNotContain("No place chosen yet", "Chosen place: Utrecht, Netherlands");
        assertThat(Fx.call(view::isChanged)).isFalse();
    }

    @Test
    void aFailedSearchLeavesTheChosenPlaceAndTheFoldAlone() throws Exception {
        var view = openSearching((text, language) -> new PlaceResults.Failed(PlaceResults.Problem.UNAVAILABLE));
        Fx.run(() -> foldRow(view).orElseThrow().fire());

        Fx.run(() -> search(view));

        SettingsFixture.waitFor(() -> shownTexts(view).contains(settings.messages.text(TextKey.PLACE_UNAVAILABLE)));
        assertThat(texts(view)).contains(EXAMPLE_PLACE);
        assertThat(Fx.call(() -> coordinatesShown(view))).isTrue();
        assertThat(Fx.call(view::isChanged)).isFalse();
    }

    @Test
    void withoutAPlaceSearchOnlyTheCoordinatesAndTheTimeZoneShowUnfoldedBeforeAnythingIsSearched() throws Exception {
        var view = open();

        showLocation(view);

        var shown = texts(view);
        assertThat(shown).contains(settings.messages.text(TextKey.PLACE_NO_SEARCH),
                settings.messages.text(TextKey.CARD_COORDINATES));
        assertThat(shown).doesNotContain(EXAMPLE_PLACE, "No place chosen yet",
                settings.messages.text(TextKey.CARD_PLACE));
        assertThat(Fx.call(() -> coordinatesShown(view))).isTrue();
        assertThat(Fx.call(() -> foldRow(view))).isEmpty();
        assertThat(Fx.call(() -> Scenes.all(view).stream().filter(Scenes::reallyVisible)
                .noneMatch(node -> SettingsFixture.named(node, settings.messages.text(TextKey.PLACE_FIELD)))))
                .isTrue();
    }

    @Test
    void typicalValuesAreTaggedNextToTheirFields() throws Exception {
        var view = openWith(config -> config.withSystem(config.system(),
                new TypicalValues(Set.of(ConfigSetting.HOUSE_BASELOAD), List.of())));
        Fx.run(() -> view.show(Category.CAR_HOUSE));

        assertThat(Fx.call(() -> settings.control(view, TextKey.FIELD_HOUSEHOLD_USE).getAccessibleHelp()))
                .contains(settings.messages.text(TextKey.TYPICAL_TAG));
        assertThat(Fx.call(() -> settings.control(view, TextKey.FIELD_CAR_BATTERY).getAccessibleHelp()))
                .doesNotContain(settings.messages.text(TextKey.TYPICAL_TAG));
    }

    /** The example config with this house's use, nothing typical, written back before Settings opens. */
    private SettingsView openWithHouse(Language language, double kw) throws Exception {
        settings = new SettingsFixture(folder, language);
        var file = new ConfigFile(settings.configPath, FILES);
        var example = file.read();
        file.write(example.withSystem(example.system().withHouseAndCar(new House(kw), example.system().car()),
                TypicalValues.NONE));
        var view = settings.open();
        Fx.run(() -> view.show(Category.CAR_HOUSE));
        return view;
    }

    /** The row of the fold on the Car and house page. */
    private Button houseFoldRow(SettingsView view) {
        var name = settings.messages.text(TextKey.MORE_SETTINGS);
        return view.pages().stream().filter(page -> page.category() == Category.CAR_HOUSE)
                .flatMap(page -> Scenes.all(page.view()).stream())
                .filter(node -> node instanceof Button button && button.getText().endsWith(" " + name))
                .map(Button.class::cast).findFirst().orElseThrow();
    }

    private boolean houseUseShown(SettingsView view) {
        return Scenes.reallyVisible(settings.control(view, TextKey.FIELD_HOUSEHOLD_USE));
    }

    private String houseUseText(SettingsView view) {
        return ((TextField) settings.field(view, TextKey.FIELD_HOUSEHOLD_USE)).getText();
    }

    @Test
    void theHouseholdUseIsFoldedAwayWithoutAHeadingAlsoWhenItIsNotTheTypicalOne() throws Exception {
        var view = openWithHouse(Language.ENGLISH, 0.35);

        assertThat(Fx.call(() -> houseFoldRow(view).getText())).isEqualTo("\u25B8 More settings");
        assertThat(Fx.call(() -> houseUseShown(view))).isFalse();
        assertThat(texts(view)).doesNotContain("House");
        Fx.run(() -> houseFoldRow(view).fire());

        assertThat(Fx.call(() -> houseUseShown(view))).isTrue();
        assertThat(Fx.call(() -> houseUseText(view))).isEqualTo("8.4");
        assertThat(texts(view)).contains("Household use", "kWh per day");
        assertThat(Fx.call(() -> settings.control(view, TextKey.FIELD_HOUSEHOLD_USE).getAccessibleHelp()))
                .contains("kWh per day, without charging the car. Your energy supplier shows your use per day.");
        assertThat(Fx.call(view::isChanged)).isFalse();
    }

    @Test
    void anUntouchedHouseholdUseIsSavedBackExactlyThoughItShowsRounded() throws Exception {
        var view = openWithHouse(Language.ENGLISH, 0.123);
        Fx.run(() -> houseFoldRow(view).fire());
        assertThat(Fx.call(() -> houseUseText(view))).isEqualTo("3.0");

        Fx.call(() -> {
            settings.type(view, TextKey.FIELD_CAR_BATTERY, "75");
            return SettingsFixture.save(view);
        });

        assertThat(stored().system().house()).isEqualTo(new House(0.123));
        assertThat(stored().system().car().usableBatteryKwh()).isEqualTo(75);
    }

    @Test
    void aTypedHouseholdUseIsSavedAsTheAverageInKwAndBroughtIntoZeroToFourHundredEighty() throws Exception {
        var view = openWithHouse(Language.ENGLISH, 0.5);

        Fx.call(() -> {
            settings.type(view, TextKey.FIELD_HOUSEHOLD_USE, "6");
            return SettingsFixture.save(view);
        });
        assertThat(stored().system().house()).isEqualTo(new House(0.25));
        assertThat(stored().typical().system()).doesNotContain(ConfigSetting.HOUSE_BASELOAD);

        Fx.call(() -> {
            settings.type(view, TextKey.FIELD_HOUSEHOLD_USE, "480.1");
            return SettingsFixture.save(view);
        });
        assertThat(stored().system().house()).isEqualTo(new House(20));
        assertThat(Fx.call(() -> houseUseText(view))).isEqualTo("480.0");

        Fx.call(() -> {
            settings.type(view, TextKey.FIELD_HOUSEHOLD_USE, "-0.1");
            return SettingsFixture.save(view);
        });
        assertThat(stored().system().house()).isEqualTo(new House(0));
    }

    @Test
    void inDutchTheHouseholdUseIsWrittenAndReadWithADecimalComma() throws Exception {
        var view = openWithHouse(Language.DUTCH, 0.35);
        assertThat(Fx.call(() -> houseUseText(view))).isEqualTo("8,4");
        Fx.run(() -> houseFoldRow(view).fire());
        assertThat(texts(view)).contains("Verbruik van het huis", "kWh per dag");

        Fx.call(() -> {
            settings.type(view, TextKey.FIELD_HOUSEHOLD_USE, "7,2");
            return SettingsFixture.save(view);
        });

        assertThat(stored().system().house().baseloadKw()).isEqualTo(7.2 / 24);
    }

    @Test
    void theTypicalHouseholdUseInsideTheFoldPutsTwelveKwhPerDayBackTagged() throws Exception {
        var view = openWithHouse(Language.ENGLISH, 0.35);
        Fx.run(() -> houseFoldRow(view).fire());
        var typical = settings.messages.text(TextKey.IN_GROUP, settings.messages.text(TextKey.USE_TYPICAL),
                "Household use");

        Fx.call(() -> {
            Scenes.all(houseFoldRow(view).getParent()).stream()
                    .filter(node -> node instanceof Button button && typical.equals(button.getAccessibleText()))
                    .map(Button.class::cast).findFirst().orElseThrow().fire();
            return SettingsFixture.save(view);
        });

        assertThat(Fx.call(() -> houseUseText(view))).isEqualTo("12.0");
        assertThat(stored().system().house()).isEqualTo(House.TYPICAL);
        assertThat(stored().typical().system()).contains(ConfigSetting.HOUSE_BASELOAD);
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

    /** A provider with only a place search, which answers as the test says. */
    private record SearchOnly(PlaceSearch search) implements ForecastProvider {

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
            return Optional.of(search);
        }
    }
}

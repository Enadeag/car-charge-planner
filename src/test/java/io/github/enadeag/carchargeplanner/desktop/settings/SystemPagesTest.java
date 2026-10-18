package io.github.enadeag.carchargeplanner.desktop.settings;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.enadeag.carchargeplanner.desktop.Fx;
import io.github.enadeag.carchargeplanner.desktop.FxTest;
import io.github.enadeag.carchargeplanner.desktop.Scenes;
import io.github.enadeag.carchargeplanner.desktop.app.AppSettings;
import io.github.enadeag.carchargeplanner.desktop.app.Appearance;
import io.github.enadeag.carchargeplanner.desktop.app.DisplayMode;
import io.github.enadeag.carchargeplanner.desktop.app.Units;
import io.github.enadeag.carchargeplanner.desktop.text.Language;
import io.github.enadeag.carchargeplanner.desktop.text.TextKey;
import io.github.enadeag.carchargeplanner.engine.config.Charger;
import io.github.enadeag.carchargeplanner.engine.config.ConfigFile;
import io.github.enadeag.carchargeplanner.engine.config.Coordinates;
import io.github.enadeag.carchargeplanner.engine.config.EngineConfig;
import io.github.enadeag.carchargeplanner.engine.config.PanelArray;
import io.github.enadeag.carchargeplanner.engine.config.Phases;
import io.github.enadeag.carchargeplanner.engine.config.ProviderId;
import io.github.enadeag.carchargeplanner.engine.config.TypicalValues;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastProvider;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastStep;
import io.github.enadeag.carchargeplanner.engine.forecast.Place;
import io.github.enadeag.carchargeplanner.engine.forecast.PlaceResults;
import io.github.enadeag.carchargeplanner.engine.forecast.PlaceSearch;
import io.github.enadeag.carchargeplanner.engine.forecast.ProviderInfo;
import io.github.enadeag.carchargeplanner.engine.forecast.SystemDescription;
import io.github.enadeag.carchargeplanner.engine.setting.Setting;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The system's categories: panel arrays, location, car and house, charger, and their typical values. */
@FxTest
class SystemPagesTest {

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
        return new ConfigFile(settings.configPath).read();
    }

    /** The example config, changed and written back before Settings opens. */
    private SettingsView openWith(java.util.function.UnaryOperator<EngineConfig> change) throws Exception {
        settings = new SettingsFixture(folder, Language.ENGLISH);
        var file = new ConfigFile(settings.configPath);
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

    @Test
    void anAddedArrayIsStoredWithItsNameAndItsTypicalValuesTagged() throws Exception {
        var view = open();

        Fx.call(() -> {
            button(view, TextKey.PANELS_ADD, 0).fire();
            fields(view, TextKey.FIELD_PANEL_NAME).get(1).setText("Garage");
            return SettingsFixture.save(view);
        });

        var config = stored();
        assertThat(config.system().panels()).containsExactly(new PanelArray("South roof", 5.0, 35, 0, 0.14),
                new PanelArray("Garage", 5, 35, 0, 0.14));
        assertThat(config.typical().ofPanel(0)).isEmpty();
        assertThat(config.typical().ofPanel(1)).isEqualTo(TypicalValues.PANEL_SETTINGS);
    }

    @Test
    void removingAnArrayMovesTheLaterOnesUpWithTheirTypicalValues() throws Exception {
        var view = openWith(config -> config.withSystem(config.system().withPanels(List.of(
                new PanelArray("East", 3, 30, -90, 0.14), new PanelArray("West", 3, 30, 90, 0.14),
                new PanelArray("Garage", 1, 10, 0, 0.1))),
                new TypicalValues(Set.of(), List.of(Set.of(), Set.of(Setting.PANEL_TILT)))));

        Fx.call(() -> {
            button(view, TextKey.PANELS_REMOVE, 0).fire();
            return SettingsFixture.save(view);
        });

        var config = stored();
        assertThat(config.system().panels()).extracting(PanelArray::name).containsExactly("West", "Garage");
        assertThat(config.typical().panels()).containsExactly(Set.of(Setting.PANEL_TILT), Set.of());
    }

    @Test
    void enteringAValueOfOnesOwnTakesItsTypicalTagAway() throws Exception {
        var view = openWith(config -> config.withSystem(config.system(),
                new TypicalValues(Set.of(Setting.CAR_BATTERY, Setting.CAR_CONSUMPTION), List.of())));

        Fx.call(() -> {
            settings.type(view, TextKey.FIELD_CAR_BATTERY, "75");
            return SettingsFixture.save(view);
        });

        var config = stored();
        assertThat(config.system().car().usableBatteryKwh()).isEqualTo(75);
        assertThat(config.typical().system()).containsExactly(Setting.CAR_CONSUMPTION);
    }

    @Test
    void iDontKnowFillsTheCardWithTypicalValuesTaggedAsSuch() throws Exception {
        var view = openWith(config -> config.withSystem(config.system().withCharger(
                new Charger(Phases.THREE, 8, 32, 400, 0.95)),
                TypicalValues.NONE));

        Fx.call(() -> {
            button(view, TextKey.USE_TYPICAL, 3).fire(); // the panel array, the car, the house, the charger
            return SettingsFixture.save(view);
        });

        var config = stored();
        assertThat(config.system().charger()).isEqualTo(Charger.TYPICAL);
        assertThat(config.typical().system()).containsExactlyInAnyOrder(Setting.CHARGER_PHASES,
                Setting.CHARGER_MIN_CURRENT, Setting.CHARGER_MAX_CURRENT, Setting.CHARGER_VOLTAGE,
                Setting.CHARGER_EFFICIENCY);
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
        assertThat(Fx.call(() -> ((TextField) settings.field(view, TextKey.FIELD_CAR_DISTANCE)).getText()))
                .isEqualTo("25"); // 40 km
        Fx.call(() -> {
            settings.type(view, TextKey.FIELD_CAR_DISTANCE, "50");
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
                new TypicalValues(Set.of(Setting.HOUSE_BASELOAD), List.of())));
        Fx.run(() -> view.show(Category.CAR_HOUSE));

        assertThat(Fx.call(() -> settings.control(view, TextKey.FIELD_BASELOAD).getAccessibleHelp()))
                .contains(settings.messages.text(TextKey.TYPICAL_TAG));
        assertThat(Fx.call(() -> settings.control(view, TextKey.FIELD_CAR_BATTERY).getAccessibleHelp()))
                .doesNotContain(settings.messages.text(TextKey.TYPICAL_TAG));
    }

    @Test
    void typicalValuesStayTaggedInImperialUnits() throws Exception {
        settings = new SettingsFixture(folder, Language.ENGLISH);
        var file = new ConfigFile(settings.configPath);
        var example = file.read();
        file.write(example.withSystem(example.system(), new TypicalValues(Set.of(Setting.CAR_CONSUMPTION), List.of())));
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
        public List<ForecastStep> fetch(SystemDescription system, Map<String, String> values, Instant now) {
            return List.of();
        }

        @Override
        public Optional<PlaceSearch> placeSearch() {
            return Optional.of((text, language) -> new PlaceResults.Found(List.of(place)));
        }
    }
}

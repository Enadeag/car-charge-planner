package io.github.enadeag.carchargeplanner.engine.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.enadeag.carchargeplanner.engine.away.RecurringAwayPeriod;
import io.github.enadeag.carchargeplanner.engine.setting.InvalidSetting;
import io.github.enadeag.carchargeplanner.engine.setting.Setting;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** The values at the edge of what each config record takes, and the first ones past it. */
class RecordEdgeValuesTest {

    private static final Percent LOW = new Percent(30);
    private static final Percent HIGH = new Percent(80);

    private static Arguments ok(String name, Supplier<Object> make) {
        return Arguments.of(name, make);
    }

    static Stream<Arguments> edges() {
        return Stream.of(
                ok("the smallest battery", () -> new Car(Double.MIN_VALUE, 18, 40)),
                ok("the smallest consumption", () -> new Car(60, Double.MIN_VALUE, 40)),
                ok("no driving", () -> new Car(60, 18, 0)),
                ok("a minimum current of 1 A", () -> new Charger(Phases.ONE, 1, 16, 230, 0.9)),
                ok("one current only", () -> new Charger(Phases.ONE, 16, 16, 230, 0.9)),
                ok("the lowest voltage", () -> new Charger(Phases.ONE, 6, 16, Double.MIN_VALUE, 0.9)),
                ok("a charger without losses", () -> new Charger(Phases.ONE, 6, 16, 230, 1)),
                ok("the lowest efficiency", () -> new Charger(Phases.ONE, 6, 16, 230, Double.MIN_VALUE)),
                ok("a house that uses nothing", () -> new House(0)),
                ok("flat panels", () -> new PanelArray("", 5, 0, 0, 0.14)),
                ok("upright panels", () -> new PanelArray("", 5, 90, 0, 0.14)),
                ok("panels facing north from the west", () -> new PanelArray("", 5, 35, -180, 0.14)),
                ok("panels facing north from the east", () -> new PanelArray("", 5, 35, 180, 0.14)),
                ok("panels without losses", () -> new PanelArray("", 5, 35, 0, 0)),
                ok("almost all lost", () -> new PanelArray("", 5, 35, 0, Math.nextDown(1.0))),
                ok("the smallest panels", () -> new PanelArray("", Double.MIN_VALUE, 35, 0, 0.14)),
                ok("a margin of 1", () -> new PlanningPreferences(LOW, HIGH, 1, 0.2, 45, 15)),
                ok("the smallest margin", () -> new PlanningPreferences(LOW, HIGH, Double.MIN_VALUE, 0.2, 45, 15)),
                ok("no import", () -> new PlanningPreferences(LOW, HIGH, 0.85, 0, 45, 15)),
                ok("a window of a minute", () -> new PlanningPreferences(LOW, HIGH, 0.85, 0.2, 1, 15)),
                ok("no lead time", () -> new PlanningPreferences(LOW, HIGH, 0.85, 0.2, 45, 0)),
                ok("a minimum just below the target", () -> new PlanningPreferences(new Percent(79.9), HIGH, 0.85, 0.2, 45, 15)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("edges")
    void aValueAtTheEdgeIsTaken(String name, Supplier<Object> make) {
        assertThatCode(make::get).doesNotThrowAnyException();
    }

    private static Arguments refused(Setting setting, Supplier<Object> make) {
        return Arguments.of(setting, make);
    }

    static Stream<Arguments> pastTheEdge() {
        return Stream.of(
                refused(Setting.CAR_BATTERY, () -> new Car(0, 18, 40)),
                refused(Setting.CAR_CONSUMPTION, () -> new Car(60, 0, 40)),
                refused(Setting.CAR_DAILY_DISTANCE, () -> new Car(60, 18, -Double.MIN_VALUE)),
                refused(Setting.CHARGER_MIN_CURRENT, () -> new Charger(Phases.ONE, 0, 16, 230, 0.9)),
                refused(Setting.CHARGER_MAX_CURRENT, () -> new Charger(Phases.ONE, 16, 15, 230, 0.9)),
                refused(Setting.CHARGER_VOLTAGE, () -> new Charger(Phases.ONE, 6, 16, 0, 0.9)),
                refused(Setting.CHARGER_EFFICIENCY, () -> new Charger(Phases.ONE, 6, 16, 230, 0)),
                refused(Setting.CHARGER_EFFICIENCY, () -> new Charger(Phases.ONE, 6, 16, 230, Math.nextUp(1.0))),
                refused(Setting.HOUSE_BASELOAD, () -> new House(-Double.MIN_VALUE)),
                refused(Setting.PANEL_SIZE, () -> new PanelArray("", 0, 35, 0, 0.14)),
                refused(Setting.PANEL_TILT, () -> new PanelArray("", 5, -Double.MIN_VALUE, 0, 0.14)),
                refused(Setting.PANEL_TILT, () -> new PanelArray("", 5, Math.nextUp(90.0), 0, 0.14)),
                refused(Setting.PANEL_DIRECTION, () -> new PanelArray("", 5, 35, Math.nextDown(-180.0), 0.14)),
                refused(Setting.PANEL_DIRECTION, () -> new PanelArray("", 5, 35, Math.nextUp(180.0), 0.14)),
                refused(Setting.PANEL_LOSSES, () -> new PanelArray("", 5, 35, 0, -Double.MIN_VALUE)),
                refused(Setting.PANEL_LOSSES, () -> new PanelArray("", 5, 35, 0, 1)),
                refused(Setting.PLANNING_MARGIN, () -> new PlanningPreferences(LOW, HIGH, 0, 0.2, 45, 15)),
                refused(Setting.PLANNING_MARGIN, () -> new PlanningPreferences(LOW, HIGH, Math.nextUp(1.0), 0.2, 45, 15)),
                refused(Setting.IMPORT_TOLERANCE, () -> new PlanningPreferences(LOW, HIGH, 0.85, -Double.MIN_VALUE, 45, 15)),
                refused(Setting.MINIMUM_WINDOW, () -> new PlanningPreferences(LOW, HIGH, 0.85, 0.2, 0, 15)),
                refused(Setting.NOTIFY_LEAD, () -> new PlanningPreferences(LOW, HIGH, 0.85, 0.2, 45, -1)),
                refused(Setting.MINIMUM_CHARGE, () -> new PlanningPreferences(HIGH, HIGH, 0.85, 0.2, 45, 15)),
                refused(Setting.CAR_BATTERY, () -> new Car(Double.NaN, 18, 40)),
                refused(Setting.CHARGER_VOLTAGE, () -> new Charger(Phases.ONE, 6, 16, Double.NaN, 0.9)),
                refused(Setting.HOUSE_BASELOAD, () -> new House(Double.NaN)),
                refused(Setting.PANEL_DIRECTION, () -> new PanelArray("", 5, 35, Double.NaN, 0.14)),
                refused(Setting.IMPORT_TOLERANCE, () -> new PlanningPreferences(LOW, HIGH, 0.85, Double.NaN, 45, 15)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("pastTheEdge")
    void aValuePastTheEdgeIsRefusedAndNamed(Setting setting, Supplier<Object> make) {
        assertThatThrownBy(make::get).isInstanceOfSatisfying(InvalidSetting.class,
                problem -> assertThat(problem.setting()).isEqualTo(setting));
    }

    private static final HomeSystem SYSTEM = new HomeSystem(new Coordinates(51.0, 5.0), ZoneId.of("Europe/Brussels"),
            List.of(PanelArray.TYPICAL), House.TYPICAL, Car.TYPICAL, Charger.TYPICAL);
    private static final EngineConfig CONFIG = new EngineConfig(SYSTEM, PlanningPreferences.RECOMMENDED,
            new ForecastSelection(new ProviderId("open-meteo"), Optional.empty(), Map.of()), AwaySchedule.NONE,
            TypicalValues.NONE);

    @Test
    void eachWithMethodChangesItsOwnPartOnly() {
        var panels = List.of(new PanelArray("East", 3, 20, -90, 0.1), new PanelArray("West", 3, 20, 90, 0.1));
        var house = new House(0.3);
        var car = new Car(77, 16, 50);
        var charger = new Charger(Phases.THREE, 6, 32, 230, 0.95);

        assertThat(SYSTEM.withPanels(panels)).isEqualTo(new HomeSystem(SYSTEM.location(), SYSTEM.timezone(), panels,
                SYSTEM.house(), SYSTEM.car(), SYSTEM.charger()));
        assertThat(SYSTEM.withHouseAndCar(house, car)).isEqualTo(new HomeSystem(SYSTEM.location(), SYSTEM.timezone(),
                SYSTEM.panels(), house, car, SYSTEM.charger()));
        assertThat(SYSTEM.withCharger(charger)).isEqualTo(new HomeSystem(SYSTEM.location(), SYSTEM.timezone(),
                SYSTEM.panels(), SYSTEM.house(), SYSTEM.car(), charger));

        var forecast = new ForecastSelection(new ProviderId("forecast-solar"), Optional.of(new ProviderId("open-meteo")),
                Map.of());
        var away = new AwaySchedule(List.of(new RecurringAwayPeriod(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(17, 0))));
        assertThat(CONFIG.withForecast(forecast)).isEqualTo(new EngineConfig(SYSTEM, CONFIG.planning(), forecast,
                AwaySchedule.NONE, TypicalValues.NONE));
        assertThat(CONFIG.withAway(away)).isEqualTo(new EngineConfig(SYSTEM, CONFIG.planning(), CONFIG.forecast(), away,
                TypicalValues.NONE));
    }

    @Test
    void aProvidersSettingsAreKeptAndAnotherHasNone() {
        var openMeteo = new ProviderId("open-meteo");
        var selection = new ForecastSelection(openMeteo, Optional.empty(), Map.of(openMeteo, Map.of("base_url", "x")));

        assertThat(selection.settings()).isEqualTo(Map.of(openMeteo, Map.of("base_url", "x")));
        assertThat(selection.settingsOf(openMeteo)).isEqualTo(Map.of("base_url", "x"));
        assertThat(selection.settingsOf(new ProviderId("forecast-solar"))).isEmpty();
    }

    @Test
    void anArrayTheTypicalValuesDoNotReachHasNone() {
        var typical = new TypicalValues(Set.of(), List.of(Set.of(Setting.PANEL_SIZE)));

        assertThat(typical.ofPanel(0)).containsExactly(Setting.PANEL_SIZE);
        assertThat(typical.ofPanel(1)).isEmpty();
    }
}

package nl.enadeag.carchargeplanner.desktop.dashboard.outlook;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.desktop.dashboard.PlannedDays;
import nl.enadeag.carchargeplanner.desktop.dashboard.day.DayModel;
import nl.enadeag.carchargeplanner.desktop.dashboard.parts.ChipModel;
import nl.enadeag.carchargeplanner.desktop.dashboard.parts.EnergySource;
import nl.enadeag.carchargeplanner.desktop.dashboard.parts.DayKind;
import nl.enadeag.carchargeplanner.desktop.text.Language;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.engine.Outlook;
import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastSource;
import nl.enadeag.carchargeplanner.engine.forecast.Freshness;
import nl.enadeag.carchargeplanner.engine.forecast.ProviderRole;
import nl.enadeag.carchargeplanner.engine.plan.DayPlan;
import nl.enadeag.carchargeplanner.engine.plan.GridAdvice;
import nl.enadeag.carchargeplanner.engine.plan.Window;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OutlookMapperTest {

    private static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");
    /** A Friday. */
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 3);
    private static final OutlookMapper MAPPER = new OutlookMapper(new Messages(Language.ENGLISH));
    private static final OutlookMapper DUTCH = new OutlookMapper(new Messages(Language.DUTCH));
    private static final String CREDIT = "Weather data: Open-Meteo.com (CC BY 4.0)";

    private static Instant at(LocalDate day, int hour, int minute) {
        return day.atTime(hour, minute).atZone(BRUSSELS).toInstant();
    }

    private static Window window(LocalDate day, int startHour, int endHour, double importKwh) {
        return new Window(at(day, startHour, 15), at(day, endHour, 30), 10, 2.3, 7.3, importKwh);
    }

    private static DayPlan day(LocalDate date, Window... windows) {
        return new DayPlan(date, new Percent(50), List.of(windows), new Percent(74), new Percent(62), 12, List.of());
    }

    /** Fetched at 07:45 from the primary provider, Open-Meteo. */
    private static ForecastSource fresh() {
        return new ForecastSource(new ProviderId("open-meteo"), "Open-Meteo", CREDIT, at(TODAY, 7, 45), Freshness.FRESH,
                ProviderRole.PRIMARY, "", Optional.empty());
    }

    private static Outlook.Planned planned(boolean batteryAssumed, DayPlan... days) {
        return new Outlook.Planned(at(TODAY, 8, 12), BRUSSELS, List.of(days), new Percent(50), batteryAssumed, Optional.empty(), fresh(), 0.4, new Percent(30), new Percent(80));
    }

    private static Outlook.Planned planned(ForecastSource source) {
        return new Outlook.Planned(at(TODAY, 8, 12), BRUSSELS, List.of(day(TODAY)), new Percent(50), false, Optional.empty(), source, 0.4, new Percent(30), new Percent(80));
    }

    private static DashboardModel.Plan advised(GridAdvice advice, DayPlan... days) {
        var planned = new Outlook.Planned(at(TODAY, 8, 12), BRUSSELS, List.of(days), new Percent(50), false, Optional.of(advice), fresh(), 0.4, new Percent(30), new Percent(80));
        return (DashboardModel.Plan) MAPPER.map(planned);
    }

    private static DashboardModel.Plan plan(DayPlan... days) {
        return (DashboardModel.Plan) MAPPER.map(planned(false, days));
    }

    @Test
    void theMainCardReadsAsOneSentence() {
        var today = plan(day(TODAY, window(TODAY, 11, 14, 0.6))).days().getFirst();

        assertThat(today.spoken()).isEqualTo("Today: charge window 11:15 to 14:30, 10 A, 92% solar.");
    }

    @Test
    void aDayWithAWindowShowsItInTheHomesTimeZoneWithWhatToSet() {
        var today = plan(day(TODAY, window(TODAY, 11, 14, 0))).days().getFirst();

        assertThat(today.kind()).isEqualTo(DayKind.SOLAR);
        assertThat(today.hero()).isEqualTo("11:15 – 14:30");
        assertThat(today.facts()).containsExactly("Set 10 A (2.3 kW)", "about 7.3 kWh");
    }

    @Test
    void theTabsStartWithTodayAndNameTheOtherDays() {
        var days = plan(day(TODAY), day(TODAY.plusDays(1)), day(TODAY.plusDays(2))).days();

        assertThat(days).extracting(DayModel::tab).containsExactly("Today", "Sat", "Sun");
        assertThat(days).extracting(DayModel::title).containsExactly("Today", "Tomorrow", "Sunday");
    }

    @Test
    void aDayWithoutAWindowSaysSoAndShowsNoTimes() {
        var tomorrow = plan(day(TODAY), day(TODAY.plusDays(1))).days().get(1);

        assertThat(tomorrow.kind()).isEqualTo(DayKind.NONE);
        assertThat(tomorrow.hero()).isEmpty();
        assertThat(tomorrow.spoken()).startsWith("Tomorrow: no charge window.");
    }

    @Test
    void theChipsShowSunShareAndBatteryAndGridOnlyWhenTheWindowTakesFromIt() {
        var withoutGrid = plan(day(TODAY, window(TODAY, 11, 14, 0.04))).days().getFirst().chips();
        var withGrid = plan(day(TODAY, window(TODAY, 11, 14, 0.6))).days().getFirst().chips();

        assertThat(withoutGrid).extracting(ChipModel::source).containsExactly(EnergySource.SOLAR, EnergySource.CAR);
        assertThat(withGrid).containsExactly(
                new ChipModel(EnergySource.SOLAR, "92% solar"), new ChipModel(EnergySource.CAR, "Battery 50 → 74%"),
                new ChipModel(EnergySource.GRID, "0.6 kWh from the grid"));
    }

    @Test
    void laterWindowsOfTheSameDayAreNamedInTheNote() {
        var today = plan(day(TODAY, window(TODAY, 9, 10, 0), window(TODAY, 13, 15, 0))).days().getFirst();

        assertThat(today.hero()).isEqualTo("09:15 – 10:30");
        assertThat(today.note()).isEqualTo("Also 13:15 – 15:30 at 10 A.");
    }

    @Test
    void anAssumedBatteryLevelIsMentionedOnTodayOnly() {
        var days = ((DashboardModel.Plan) MAPPER.map(planned(true, day(TODAY), day(TODAY.plusDays(1))))).days();

        assertThat(days.get(0).note()).contains("assumes the battery is at 50%");
        assertThat(days.get(1).note()).doesNotContain("assumes");
    }

    @Test
    void theFooterCreditsTheProviderAsItsTermsAskAndSaysWhenTheForecastWasFetched() {
        assertThat(plan(day(TODAY)).footer()).isEqualTo(CREDIT + " · updated 07:45");
    }

    @Test
    void aForecastFromTheCacheThatIsNotDueForARefreshIsCalledUpdatedAsWell() {
        var cached = new ForecastSource(new ProviderId("open-meteo"), "Open-Meteo", CREDIT, at(TODAY, 7, 45), Freshness.CACHED,
                ProviderRole.PRIMARY, "", Optional.empty());

        assertThat(((DashboardModel.Plan) MAPPER.map(planned(cached))).footer()).isEqualTo(CREDIT + " · updated 07:45");
    }

    @Test
    void aFallbackForecastSaysWhichProviderItStandsInFor() {
        var fallback = new ForecastSource(new ProviderId("forecast-solar"), "Forecast.Solar", "Solar forecast: Forecast.Solar",
                at(TODAY, 7, 45), Freshness.FRESH, ProviderRole.FALLBACK, "Open-Meteo", Optional.empty());

        assertThat(((DashboardModel.Plan) MAPPER.map(planned(fallback))).footer())
                .isEqualTo("Solar forecast: Forecast.Solar · Open-Meteo unavailable · updated 07:45");
    }

    @Test
    void aStaleForecastAndARateLimitAreNamedUntilTheLimitEnds() {
        var limited = new ForecastSource(new ProviderId("open-meteo"), "Open-Meteo", CREDIT, at(TODAY, 5, 30), Freshness.STALE,
                ProviderRole.PRIMARY, "", Optional.of(at(TODAY, 9, 0)));
        var ended = new ForecastSource(new ProviderId("open-meteo"), "Open-Meteo", CREDIT, at(TODAY, 5, 30), Freshness.STALE,
                ProviderRole.PRIMARY, "", Optional.of(at(TODAY, 8, 0)));

        assertThat(((DashboardModel.Plan) MAPPER.map(planned(limited))).footer())
                .isEqualTo(CREDIT + " · not refreshed since 05:30 · provider busy until 09:00");
        assertThat(((DashboardModel.Plan) MAPPER.map(planned(ended))).footer())
                .isEqualTo(CREDIT + " · not refreshed since 05:30");
    }

    @Test
    void numbersFollowTheChosenLanguageNotTheSystems() {
        var before = Locale.getDefault();
        Locale.setDefault(Locale.GERMANY);
        try {
            assertThat(plan(day(TODAY, window(TODAY, 11, 14, 0))).days().getFirst().facts())
                    .contains("Set 10 A (2.3 kW)");
        } finally {
            Locale.setDefault(before);
        }
    }

    @Test
    void inDutchADayHasADecimalCommaA24HourClockAndDutchDayNames() {
        var days = ((DashboardModel.Plan) DUTCH.map(planned(false, day(TODAY, window(TODAY, 13, 14, 0.6)),
                day(TODAY.plusDays(1)), day(TODAY.plusDays(2))))).days();

        assertThat(days).extracting(DayModel::tab).containsExactly("Vandaag", "Za", "Zo");
        assertThat(days).extracting(DayModel::title).containsExactly("Vandaag", "Morgen", "Zondag");
        assertThat(days.getFirst().hero()).isEqualTo("13:15 – 14:30");
        assertThat(days.getFirst().facts()).containsExactly("Stel 10 A in (2,3 kW)", "ongeveer 7,9 kWh");
    }

    @Test
    void inDutchGridAdviceNamesTheDayInLowerCaseInsideTheSentence() {
        var sunday = TODAY.plusDays(2);
        var advice = new GridAdvice(sunday, 22, 8, 11.0, Optional.empty());
        var planned = new Outlook.Planned(at(TODAY, 8, 12), BRUSSELS, List.of(day(TODAY), day(sunday)), new Percent(50),
                false, Optional.of(advice), fresh(), 0.4, new Percent(30), new Percent(80));

        var model = ((DashboardModel.Plan) DUTCH.map(planned)).advice().orElseThrow();

        assertThat(model.title()).isEqualTo("Laad 11,0 kWh van het net vóór zondag.");
    }

    @Test
    void gridAdviceSaysHowMuchWhenAndWhy() {
        var sunday = TODAY.plusDays(2);
        var night = new Window(at(TODAY.plusDays(1), 1, 0), at(TODAY.plusDays(1), 4, 0), 16, 3.7, 0, 11.0);
        var advice = new GridAdvice(sunday, 22, 8, 11.0, Optional.of(night));

        var model = advised(advice, day(TODAY), day(TODAY.plusDays(1)), day(sunday)).advice().orElseThrow();

        assertThat(model.title()).isEqualTo("Charge 11.0 kWh from the grid: tomorrow 01:00 – 04:00.");
        assertThat(model.detail())
                .isEqualTo("Without it the battery ends Sunday at 22%, below the minimum of 30%. Set 16 A (3.7 kW).");
    }

    @Test
    void gridAdviceWithoutRoomForAChargeSaysSo() {
        var advice = new GridAdvice(TODAY.plusDays(1), -5, 35, 23.3, Optional.empty());

        var model = advised(advice, day(TODAY), day(TODAY.plusDays(1))).advice().orElseThrow();

        assertThat(model.title()).isEqualTo("Charge 23.3 kWh from the grid before tomorrow.");
        assertThat(model.detail()).contains("runs empty tomorrow", "No day before it has room");
    }

    @Test
    void theDayThatNeedsTheGridChargeShowsItOnItsTab() {
        var advice = new GridAdvice(TODAY.plusDays(1), 22, 8, 11.0, Optional.empty());

        var days = advised(advice, day(TODAY), day(TODAY.plusDays(1)), day(TODAY.plusDays(2))).days();

        assertThat(days).extracting(DayModel::kind).containsExactly(DayKind.NONE, DayKind.GRID, DayKind.NONE);
    }

    @Test
    void withoutGridAdviceThereIsNone() {
        assertThat(plan(day(TODAY)).advice()).isEmpty();
    }

    @Test
    void withoutAPlanTheReasonIsShown() {
        var message = (DashboardModel.NoPlan) MAPPER.map(new Outlook.NoForecast("Could not reach Open-Meteo"));

        assertThat(message.headline()).isEqualTo("No forecast");
        assertThat(message.detail()).endsWith("Could not reach Open-Meteo");
        assertThat(message.footer()).isEmpty(); // there is no forecast to credit
    }

    @Test
    void withoutAForecastBecauseTheKeychainIsLockedTheOwnerIsToldToUnlockIt() {
        var message = (DashboardModel.NoPlan) MAPPER.map(new Outlook.NoForecast(
                "API key is required; The system keychain is locked.", true));

        assertThat(message.headline()).isEqualTo("No forecast");
        assertThat(message.detail()).isEqualTo("Unlock your keychain so the app can read your API key.");
    }

    @Test
    void aPlanWithoutDaysIsShownAsNoForecast() {
        assertThat(MAPPER.map(planned(false))).isInstanceOf(DashboardModel.NoPlan.class);
    }

    @Test
    void theDayModelsCarryTheirCharts() {
        var plan = (DashboardModel.Plan) MAPPER.map(PlannedDays.TWO_DAYS);

        assertThat(plan.days()).extracting(day -> day.curve().madeAtHour().isPresent()).containsExactly(true, false);
        assertThat(plan.days()).extracting(day -> day.battery().editable()).containsExactly(true, false);
    }
}

package nl.enadeag.carchargeplanner.desktop.dashboard.outlook;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.desktop.dashboard.Models;
import nl.enadeag.carchargeplanner.desktop.dashboard.PlannedDays;
import nl.enadeag.carchargeplanner.desktop.app.Units;
import nl.enadeag.carchargeplanner.desktop.dashboard.away.OneOffAwayModel;
import nl.enadeag.carchargeplanner.desktop.dashboard.day.DayModel;
import nl.enadeag.carchargeplanner.desktop.dashboard.trip.TripAdviceModel;
import nl.enadeag.carchargeplanner.desktop.dashboard.trip.TripsModel;
import nl.enadeag.carchargeplanner.desktop.dashboard.parts.ChipModel;
import nl.enadeag.carchargeplanner.desktop.text.Language;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.engine.Outlook;
import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod;
import nl.enadeag.carchargeplanner.engine.config.ConfigProblem;
import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import nl.enadeag.carchargeplanner.engine.dashboard.DayKind;
import nl.enadeag.carchargeplanner.engine.dashboard.EnergySource;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastSource;
import nl.enadeag.carchargeplanner.engine.forecast.Freshness;
import nl.enadeag.carchargeplanner.engine.forecast.ProviderRole;
import nl.enadeag.carchargeplanner.engine.plan.DayPlan;
import nl.enadeag.carchargeplanner.engine.plan.GridAdvice;
import nl.enadeag.carchargeplanner.engine.plan.TripAdvice;
import nl.enadeag.carchargeplanner.engine.plan.TripGridCharge;
import nl.enadeag.carchargeplanner.engine.plan.Window;
import nl.enadeag.carchargeplanner.engine.trip.Trip;
import java.nio.file.Path;
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
    private static final OutlookMapper MAPPER = new OutlookMapper(new Messages(Language.ENGLISH), Models.PROVIDERS);
    private static final OutlookMapper DUTCH = new OutlookMapper(new Messages(Language.DUTCH), Models.PROVIDERS);
    private static final String CREDIT = "Weather data: Open-Meteo.com (CC BY 4.0)";

    private static Instant at(LocalDate day, int hour, int minute) {
        return day.atTime(hour, minute).atZone(BRUSSELS).toInstant();
    }

    private static Window window(LocalDate day, int startHour, int endHour, double importKwh) {
        return new Window(at(day, startHour, 15), at(day, endHour, 30), 10, 2.3, 7.3, importKwh);
    }

    private static DayPlan day(LocalDate date, Window... windows) {
        return new DayPlan(date, new Percent(50), List.of(windows), new Percent(74), new Percent(80), new Percent(62), 12,
                List.of());
    }

    /** Fetched at 07:45 from the primary provider, Open-Meteo. */
    private static ForecastSource fresh() {
        return new ForecastSource(new ProviderId("open-meteo"), "Open-Meteo", CREDIT, at(TODAY, 7, 45), Freshness.FRESH,
                ProviderRole.PRIMARY, "", Optional.empty(), List.of());
    }

    /** A day without a window that starts at a level while the plan charges up to another; the target is 80 %. */
    private static DayPlan withoutWindow(LocalDate date, double startPct, double chargeToPct) {
        return new DayPlan(date, new Percent(startPct), List.of(), new Percent(startPct), new Percent(chargeToPct),
                new Percent(startPct), 12, List.of());
    }

    private static Outlook.Planned planned(boolean batteryAssumed, DayPlan... days) {
        return new Outlook.Planned(at(TODAY, 8, 12), BRUSSELS, List.of(days), new Percent(50), batteryAssumed, Optional.empty(), List.of(), fresh(), 0.4, new Percent(30), new Percent(80));
    }

    private static Outlook.Planned planned(ForecastSource source) {
        return new Outlook.Planned(at(TODAY, 8, 12), BRUSSELS, List.of(day(TODAY)), new Percent(50), false, Optional.empty(), List.of(), source, 0.4, new Percent(30), new Percent(80));
    }

    private static DashboardModel.Plan advised(GridAdvice advice, DayPlan... days) {
        var planned = new Outlook.Planned(at(TODAY, 8, 12), BRUSSELS, List.of(days), new Percent(50), false, Optional.of(advice), List.of(), fresh(), 0.4, new Percent(30), new Percent(80));
        return (DashboardModel.Plan) MAPPER.map(planned);
    }

    private static DashboardModel.Plan plan(DayPlan... days) {
        return (DashboardModel.Plan) MAPPER.map(planned(false, days));
    }

    @Test
    void aConfigFileOfANewerVersionIsShownAsAnInvalidOne() {
        var detail = "written by a newer version of the app (file version 2; this version reads up to 1)";
        var newer = new Outlook.ConfigUnusable(ConfigProblem.Kind.NEWER_VERSION, Path.of("/home/owner/engine.json"),
                detail);
        var invalid = new Outlook.ConfigUnusable(ConfigProblem.Kind.INVALID, Path.of("/home/owner/engine.json"),
                detail);

        assertThat(OutlookMapper.whatToFix(newer, new Messages(Language.ENGLISH)))
                .isEqualTo("Fix engine.json: " + detail)
                .isEqualTo(OutlookMapper.whatToFix(invalid, new Messages(Language.ENGLISH)));
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
                ProviderRole.PRIMARY, "", Optional.empty(), List.of());

        assertThat(((DashboardModel.Plan) MAPPER.map(planned(cached))).footer()).isEqualTo(CREDIT + " · updated 07:45");
    }

    @Test
    void aFallbackForecastSaysWhichProviderItStandsInFor() {
        var fallback = new ForecastSource(new ProviderId("forecast-solar"), "Forecast.Solar", "Solar forecast: Forecast.Solar",
                at(TODAY, 7, 45), Freshness.FRESH, ProviderRole.FALLBACK, "Open-Meteo", Optional.empty(), List.of());

        assertThat(((DashboardModel.Plan) MAPPER.map(planned(fallback))).footer())
                .isEqualTo("Solar forecast: Forecast.Solar · Open-Meteo unavailable · updated 07:45");
    }

    @Test
    void aStaleForecastAndARateLimitAreNamedUntilTheLimitEnds() {
        var limited = new ForecastSource(new ProviderId("open-meteo"), "Open-Meteo", CREDIT, at(TODAY, 5, 30), Freshness.STALE,
                ProviderRole.PRIMARY, "", Optional.of(at(TODAY, 9, 0)), List.of());

        assertThat(((DashboardModel.Plan) MAPPER.map(planned(limited))).footer())
                .isEqualTo(CREDIT + " · not refreshed since 05:30 · provider busy until 09:00");
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
        var advice = new GridAdvice(sunday, 22, 8, 11.0, Optional.empty(), Optional.empty());
        var planned = new Outlook.Planned(at(TODAY, 8, 12), BRUSSELS, List.of(day(TODAY), day(sunday)), new Percent(50),
                false, Optional.of(advice), List.of(), fresh(), 0.4, new Percent(30), new Percent(80));

        var model = ((DashboardModel.Plan) DUTCH.map(planned)).days().getFirst().gridAdvice().orElseThrow();

        assertThat(model.title()).isEqualTo("Laad 11,0 kWh uit het net vóór zondag.");
    }

    @Test
    void gridAdviceSaysHowMuchWhenAndWhy() {
        var sunday = TODAY.plusDays(2);
        var evening = new Window(at(TODAY.plusDays(1), 21, 0), at(TODAY.plusDays(2), 0, 0), 16, 3.7, 0, 11.0);
        var advice = new GridAdvice(sunday, 22, 8, 11.0, Optional.of(evening), Optional.of(new Percent(57)));

        var model = advised(advice, day(TODAY), day(TODAY.plusDays(1)), day(sunday)).days().getFirst().gridAdvice()
                .orElseThrow();

        assertThat(model.title()).isEqualTo("Charge 11.0 kWh from the grid: tomorrow 21:00 – 00:00.");
        assertThat(model.detail()).isEqualTo("Without it the battery ends Sunday at 22%, below the minimum of 30%. "
                + "Set 16 A (3.7 kW), and a charge limit of 57% in the car or the charger.");
    }

    @Test
    void gridAdviceWithoutRoomForAChargeSaysSo() {
        var advice = new GridAdvice(TODAY.plusDays(1), -5, 35, 23.3, Optional.empty(), Optional.empty());

        var model = advised(advice, day(TODAY), day(TODAY.plusDays(1))).days().getFirst().gridAdvice().orElseThrow();

        assertThat(model.title()).isEqualTo("Charge 23.3 kWh from the grid before tomorrow.");
        assertThat(model.detail()).contains("runs empty tomorrow", "No day before it has room");
    }

    @Test
    void theDayThatNeedsTheGridChargeShowsItOnItsTab() {
        var advice = new GridAdvice(TODAY.plusDays(1), 22, 8, 11.0, Optional.empty(), Optional.empty());

        var days = advised(advice, day(TODAY), day(TODAY.plusDays(1)), day(TODAY.plusDays(2))).days();

        assertThat(days).extracting(DayModel::kind).containsExactly(DayKind.NONE, DayKind.GRID, DayKind.NONE);
    }

    @Test
    void eachDayCarriesTheTripAdviceTheEngineShowsWithItInTheOwnersUnits() {
        var trip = new Trip(at(TODAY.plusDays(1), 8, 15), at(TODAY.plusDays(1), 19, 0), 160.9344, false);
        var advice = new TripAdvice(trip, new Percent(70), List.of(), new Percent(75), Optional.empty(),
                new Percent(75), 0, new Percent(40));
        var planned = new Outlook.Planned(at(TODAY, 8, 12), BRUSSELS, List.of(day(TODAY), day(TODAY.plusDays(1))),
                new Percent(50), false, Optional.empty(), List.of(advice), fresh(), 0.4, new Percent(30),
                new Percent(80));

        var plan = (DashboardModel.Plan) MAPPER.map(planned, List.of(), List.of(trip), Units.IMPERIAL);

        assertThat(plan.days()).extracting(day -> day.tripAdvice().stream().map(TripAdviceModel::title).toList())
                .containsExactly(List.of(), List.of("Trip Saturday at 08:15, 100 mi there and back."));
    }

    /** Saturday from 08:30 to Monday 19:00, 300 km; and one the owner makes without this car on Tuesday. */
    private static final Trip LONG_TRIP = new Trip(at(TODAY.plusDays(1), 8, 30), at(TODAY.plusDays(3), 19, 0), 300,
            false);
    private static final Trip OTHER_CAR = new Trip(at(TODAY.plusDays(4), 8, 0), at(TODAY.plusDays(4), 18, 0), 200,
            false, true);

    /** Today with a window, Saturday the trip leaves after charging or not, Sunday to Tuesday without windows. */
    private static DashboardModel.Plan withTrip(Messages messages, double saturdayAfterPct) {
        var saturday = new DayPlan(TODAY.plusDays(1), new Percent(94), List.of(), new Percent(saturdayAfterPct),
                new Percent(94), new Percent(32), 70, List.of());
        var days = List.of(day(TODAY, window(TODAY, 11, 14, 0)), saturday,
                new DayPlan(TODAY.plusDays(2), new Percent(32), List.of(), new Percent(32), new Percent(80),
                        new Percent(32), 0, List.of()),
                new DayPlan(TODAY.plusDays(3), new Percent(32), List.of(), new Percent(32), new Percent(80),
                        new Percent(32), 0, List.of()),
                new DayPlan(TODAY.plusDays(4), new Percent(32), List.of(), new Percent(32), new Percent(80),
                        new Percent(20), 12, List.of()));
        var advice = new TripAdvice(LONG_TRIP, new Percent(94), List.of(), new Percent(94), Optional.empty(),
                new Percent(94), 0, new Percent(42));
        var planned = new Outlook.Planned(at(TODAY, 8, 12), BRUSSELS, days, new Percent(50), false, Optional.empty(),
                List.of(advice), fresh(), 0.4, new Percent(30), new Percent(80));
        return (DashboardModel.Plan) new OutlookMapper(messages, Models.PROVIDERS).map(planned, List.of(),
                List.of(LONG_TRIP, OTHER_CAR), Units.METRIC);
    }

    @Test
    void theDayATripLeavesShowsTheLevelItLeavesAndDoesNotSayTheCarNeedsNoCharging() {
        var saturday = withTrip(new Messages(Language.ENGLISH), 94).days().get(1);

        assertThat(saturday.chips()).containsExactly(
                new ChipModel(EnergySource.CAR, "Battery 94 → 32% after the trip"));
        assertThat(saturday.note()).isEmpty();
        assertThat(saturday.spoken()).isEqualTo("Tomorrow: no charge window.");
        assertThat(saturday.battery().levels()).isEqualTo("94 → 32% after the trip");
        assertThat(saturday.battery().spoken())
                .endsWith("After the trip the battery is at 32% at the end of the day.");
    }

    @Test
    void theDayATripLeavesAfterACharge() {
        var english = withTrip(new Messages(Language.ENGLISH), 100).days().get(1);
        var dutch = withTrip(new Messages(Language.DUTCH), 100).days().get(1);

        assertThat(english.chips()).extracting(ChipModel::text).containsExactly("Battery 94 → 100%, 32% after the trip");
        assertThat(english.battery().levels()).isEqualTo("94 → 100%, 32% after the trip");
        assertThat(dutch.chips()).extracting(ChipModel::text).containsExactly("Accu 94 → 100%, 32% na de rit");
        assertThat(dutch.battery().levels()).isEqualTo("94 → 100%, 32% na de rit");
        assertThat(dutch.battery().spoken()).endsWith("Na de rit staat de accu aan het eind van de dag op 32%.");
    }

    @Test
    void theDaysTheCarIsAwayOnATripSaySoWithTheirBatteryChipAndATripWithoutThisCarDoesNot() {
        var english = withTrip(new Messages(Language.ENGLISH), 94).days();
        var dutch = withTrip(new Messages(Language.DUTCH), 94).days();

        assertThat(english.subList(2, 4)).extracting(DayModel::note).containsOnly(
                "The car is away on a trip, or there is too little solar power while it is home.");
        assertThat(dutch.get(2).note())
                .isEqualTo("De auto is weg voor een rit, of er is te weinig zonnestroom terwijl hij thuis is.");
        assertThat(english.subList(2, 4)).extracting(DayModel::chips).allSatisfy(chips ->
                assertThat(chips).extracting(ChipModel::text).containsExactly("Battery 32 → 32%"));
        assertThat(english.get(4).heading()).isEqualTo("No charge window");
        assertThat(english.get(4).note()).isEqualTo("There is too little solar power while the car is home.");
        assertThat(english.get(4).chips()).isEmpty();
        assertThat(english.get(0).chips()).extracting(ChipModel::text).contains("Battery 50 → 74%");
    }

    @Test
    void aDayThatNeedsNoChargingSaysOnlySoWithoutABatteryChip() {
        var english = plan(day(TODAY), withoutWindow(TODAY.plusDays(1), 79.6, 80)).days().get(1);
        var dutch = ((DashboardModel.Plan) DUTCH.map(planned(false, day(TODAY), withoutWindow(TODAY.plusDays(1), 80,
                80)))).days().get(1);

        assertThat(english.heading()).isEqualTo("Charging not needed");
        assertThat(english.chips()).isEmpty();
        assertThat(english.note()).isEmpty();
        assertThat(english.spoken()).isEqualTo("Tomorrow: charging not needed.");
        assertThat(english.kind()).isEqualTo(DayKind.NONE);
        assertThat(english.battery().levels()).isEqualTo("80 → 80%");
        assertThat(dutch.heading()).isEqualTo("Laden niet nodig");
        assertThat(dutch.spoken()).isEqualTo("Morgen: laden niet nodig.");
    }

    @Test
    void aDayThatCouldUseAChargeWithoutAWindowSaysThereIsTooLittleSolarPowerWithoutABatteryChip() {
        var english = plan(day(TODAY), withoutWindow(TODAY.plusDays(1), 79.4, 80)).days().get(1);
        var dutch = ((DashboardModel.Plan) DUTCH.map(planned(false, day(TODAY), withoutWindow(TODAY.plusDays(1), 85,
                92.1)))).days().get(1);

        assertThat(english.heading()).isEqualTo("No charge window");
        assertThat(english.chips()).isEmpty();
        assertThat(english.note()).isEqualTo("There is too little solar power while the car is home.");
        assertThat(english.spoken())
                .isEqualTo("Tomorrow: no charge window. There is too little solar power while the car is home.");
        assertThat(english.kind()).isEqualTo(DayKind.NONE);
        assertThat(english.battery().levels()).isEqualTo("79 → 79%");
        assertThat(dutch.heading()).isEqualTo("Geen laadvenster");
        assertThat(dutch.note()).isEqualTo("Er is te weinig zonnestroom terwijl de auto thuis is.");
    }

    /** Today without a window at the target, with the grid charge for Saturday's trip starting this evening. */
    private static DashboardModel.Plan gridChargeToday(Messages messages) {
        var today = new DayPlan(TODAY, new Percent(80), List.of(), new Percent(94), new Percent(80), new Percent(94), 0,
                List.of());
        var saturday = new DayPlan(TODAY.plusDays(1), new Percent(94), List.of(), new Percent(94), new Percent(94),
                new Percent(32), 70, List.of());
        var grid = new TripGridCharge(window(TODAY, 20, 22, 14.6), TripGridCharge.Start.PLANNED);
        var advice = new TripAdvice(LONG_TRIP, new Percent(94), List.of(), new Percent(80), Optional.of(grid),
                new Percent(94), 0, new Percent(42));
        var planned = new Outlook.Planned(at(TODAY, 8, 12), BRUSSELS, List.of(today, saturday), new Percent(80), false,
                Optional.empty(), List.of(advice), fresh(), 0.4, new Percent(30), new Percent(80));
        return (DashboardModel.Plan) new OutlookMapper(messages, Models.PROVIDERS).map(planned, List.of(),
                List.of(LONG_TRIP), Units.METRIC);
    }

    @Test
    void theDayATripsGridChargeStartsOnSaysOnlyThatThereIsNoChargeWindowWithItsBatteryChip() {
        var english = gridChargeToday(new Messages(Language.ENGLISH)).days().getFirst();
        var dutch = gridChargeToday(new Messages(Language.DUTCH)).days().getFirst();

        assertThat(english.heading()).isEqualTo("No charge window");
        assertThat(english.note()).isEmpty();
        assertThat(english.chips()).extracting(ChipModel::text).containsExactly("Battery 80 → 94%");
        assertThat(english.spoken()).isEqualTo("Today: no charge window.");
        assertThat(english.kind()).isEqualTo(DayKind.NONE);
        assertThat(dutch.heading()).isEqualTo("Geen laadvenster");
        assertThat(dutch.note()).isEmpty();
        assertThat(dutch.spoken()).isEqualTo("Vandaag: geen laadvenster.");
    }

    @Test
    void todayThatNeedsNoChargingStillNotesTheAssumedLevel() {
        var today = ((DashboardModel.Plan) MAPPER.map(planned(true, withoutWindow(TODAY, 80, 80)))).days().getFirst();

        assertThat(today.heading()).isEqualTo("Charging not needed");
        assertThat(today.note()).contains("assumes the battery is at 50%").doesNotContain("solar");
    }

    @Test
    void theGridAdviceIsOnTheDayTheEngineShowsItWith() {
        var advice = new GridAdvice(TODAY.plusDays(1), 22, 8, 11.0, Optional.empty(), Optional.empty());

        var days = advised(advice, day(TODAY), day(TODAY.plusDays(1))).days();

        assertThat(days).extracting(day -> day.gridAdvice().isPresent()).containsExactly(true, false);
    }

    @Test
    void aPlanWhoseFirstDayIsNotTodayHasNoDayWithTheGridAdvice() {
        // the engine shows grid advice with the day the plan was made only, not with the first day
        var advice = new GridAdvice(TODAY.plusDays(2), 22, 8, 11.0, Optional.empty(), Optional.empty());

        var days = advised(advice, day(TODAY.plusDays(1)), day(TODAY.plusDays(2))).days();

        assertThat(days).extracting(DayModel::gridAdvice).containsOnly(Optional.empty());
    }

    @Test
    void theDayWithTheGridAdviceKeepsItsTrips() {
        var advice = new GridAdvice(TODAY.plusDays(1), 22, 8, 11.0, Optional.empty(), Optional.empty());
        var trip = new Trip(at(TODAY, 15, 0), at(TODAY, 18, 0), 40, false);
        var planned = new Outlook.Planned(at(TODAY, 8, 12), BRUSSELS, List.of(day(TODAY), day(TODAY.plusDays(1))),
                new Percent(50), false, Optional.of(advice), List.of(), fresh(), 0.4, new Percent(30),
                new Percent(80));

        var today = ((DashboardModel.Plan) MAPPER.map(planned, List.of(), List.of(trip), Units.METRIC)).days().get(0);

        assertThat(today.gridAdvice()).isPresent();
        assertThat(today.trips()).extracting(TripsModel.Listed::trip).containsExactly(trip);
    }

    @Test
    void withoutGridAdviceThereIsNone() {
        assertThat(plan(day(TODAY)).days().getFirst().gridAdvice()).isEmpty();
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
    void aPlanWithoutDaysHasTheFooterOfItsForecast() {
        assertThat(MAPPER.map(planned(false)).footer()).isEqualTo(CREDIT + " · updated 07:45");
    }

    private static final ProviderId OPEN_METEO = new ProviderId("open-meteo");
    private static final ProviderId FORECAST_SOLAR = new ProviderId("forecast-solar");
    private static final String OPEN_METEO_REFUSED =
            "Open-Meteo refused the API key. Check it under Forecast in Settings.";
    private static final String FORECAST_SOLAR_REFUSED =
            "Forecast.Solar refused the API key. Check it under Forecast in Settings.";

    /** Forecast.Solar's forecast standing in for Open-Meteo, after these providers refused their key. */
    private static ForecastSource fallbackAfterRefused(ProviderId... refused) {
        return new ForecastSource(FORECAST_SOLAR, "Forecast.Solar", "Solar forecast: Forecast.Solar", at(TODAY, 7, 45),
                Freshness.FRESH, ProviderRole.FALLBACK, "Open-Meteo", Optional.empty(), List.of(refused));
    }

    @Test
    void aPlanWarnsOfAProviderThatRefusedItsKeyWhileAFallbackGivesThePlan() {
        var plan = (DashboardModel.Plan) MAPPER.map(planned(fallbackAfterRefused(OPEN_METEO)));

        assertThat(plan.keyRefused()).containsExactly(OPEN_METEO_REFUSED);
        assertThat(plan.days()).hasSize(1);
    }

    @Test
    void aPlanFromAnOlderForecastWarnsOfEveryProviderThatRefusedItsKeyInTheOrderTheyWereAsked() {
        // Forecast.Solar was asked first here, so it comes first, although the app offers Open-Meteo first
        var stale = new ForecastSource(FORECAST_SOLAR, "Forecast.Solar", "Solar forecast: Forecast.Solar",
                at(TODAY, 5, 30), Freshness.STALE, ProviderRole.PRIMARY, "", Optional.empty(),
                List.of(FORECAST_SOLAR, OPEN_METEO));

        var plan = (DashboardModel.Plan) MAPPER.map(planned(stale));

        assertThat(plan.keyRefused()).containsExactly(FORECAST_SOLAR_REFUSED, OPEN_METEO_REFUSED);
    }

    @Test
    void aPlanWhoseProvidersTookTheirKeysHasNoWarning() {
        assertThat(MAPPER.map(planned(fresh())).keyRefused()).isEmpty();
    }

    @Test
    void withoutAForecastEveryProviderThatRefusedItsKeyIsWarnedOfAndTheReasonStays() {
        var reason = "Forecast.Solar refused the request (status 401); Open-Meteo refused the request (status 403)";

        var message = (DashboardModel.NoPlan) MAPPER.map(
                new Outlook.NoForecast(reason, false, List.of(FORECAST_SOLAR, OPEN_METEO)));

        assertThat(message.keyRefused()).containsExactly(FORECAST_SOLAR_REFUSED, OPEN_METEO_REFUSED);
        assertThat(message.headline()).isEqualTo("No forecast");
        assertThat(message.detail()).endsWith(reason);
    }

    @Test
    void withoutAForecastForAnotherReasonThereIsNoWarning() {
        assertThat(MAPPER.map(new Outlook.NoForecast("Could not reach Open-Meteo")).keyRefused()).isEmpty();
    }

    @Test
    void aPlanWithoutDaysWarnsOfAProviderThatRefusedItsKey() {
        var none = new Outlook.Planned(at(TODAY, 8, 12), BRUSSELS, List.of(), new Percent(50), false, Optional.empty(),
                List.of(), fallbackAfterRefused(OPEN_METEO), 0.4, new Percent(30), new Percent(80));

        var message = (DashboardModel.NoPlan) MAPPER.map(none);

        assertThat(message.keyRefused()).containsExactly(OPEN_METEO_REFUSED);
        assertThat(message.detail()).isEqualTo("The forecast has no days to plan.");
    }

    @Test
    void beforeTheFirstPlanAndWithoutAUsableSetUpThereIsNoWarning() {
        var unusable = new Outlook.ConfigUnusable(ConfigProblem.Kind.INVALID, Path.of("/home/owner/engine.json"),
                "system: is required");

        assertThat(List.of(MAPPER.loading(), MAPPER.map(new Outlook.NotSetUp(Path.of("/home/owner/engine.json"))),
                MAPPER.map(unusable))).allSatisfy(model -> assertThat(model.keyRefused()).isEmpty());
    }

    @Test
    void aProviderThisVersionDoesNotKnowIsNamedByItsId() {
        var message = MAPPER.map(new Outlook.NoForecast("refused", false, List.of(new ProviderId("example-solar"))));

        assertThat(message.keyRefused())
                .containsExactly("example-solar refused the API key. Check it under Forecast in Settings.");
    }

    @Test
    void theWarningIsInTheOwnersLanguage() {
        var message = DUTCH.map(new Outlook.NoForecast("refused", false, List.of(FORECAST_SOLAR, OPEN_METEO)));

        assertThat(message.keyRefused()).containsExactly(
                "Forecast.Solar weigert de API-sleutel. Controleer de sleutel bij Voorspelling in Instellingen.",
                "Open-Meteo weigert de API-sleutel. Controleer de sleutel bij Voorspelling in Instellingen.");
    }

    @Test
    void eachDayListsTheTripsTheEngineListsWithItInTheirWords() {
        var plan = withTrip(new Messages(Language.ENGLISH), 94);
        var away = "Away Tomorrow 08:30 – Mon 6 Jul 19:00 · 300 km there and back";

        assertThat(plan.days()).extracting(day -> day.trips().stream().map(TripsModel.Listed::text).toList())
                .containsExactly(List.of(), List.of(away), List.of(away), List.of(away),
                        List.of("Away 08:00 – 18:00 · 200 km there and back"));
        assertThat(plan.days().get(1).trips()).extracting(TripsModel.Listed::trip).containsExactly(LONG_TRIP);
    }

    @Test
    void thePlanTellsTheTripsInTheHomesZoneAndTheOwnersUnits() {
        var plan = withTrip(new Messages(Language.ENGLISH), 94);

        assertThat(plan.trips()).isEqualTo(new TripsModel(BRUSSELS, Units.METRIC));
    }

    @Test
    void theComingAwayPeriodsAreTheEnginesInTheirWords() {
        var ended = new OneOffAwayPeriod(at(TODAY, 6, 0), at(TODAY, 8, 0));
        var tomorrow = OneOffAwayPeriod.allDay(TODAY.plusDays(1), BRUSSELS);

        var plan = (DashboardModel.Plan) MAPPER.map(planned(false, day(TODAY)), List.of(ended, tomorrow), List.of(),
                Units.METRIC);

        assertThat(plan.away().coming()).containsExactly(
                new OneOffAwayModel.Listed(tomorrow, "Tomorrow, all day", "Remove Tomorrow, all day"));
        assertThat(plan.away().zone()).isEqualTo(BRUSSELS);
    }

    @Test
    void theDayModelsCarryTheirCharts() {
        var plan = (DashboardModel.Plan) MAPPER.map(PlannedDays.TWO_DAYS);

        assertThat(plan.days()).extracting(day -> day.curve().chart().now().isPresent()).containsExactly(true, false);
        assertThat(plan.days()).extracting(day -> day.battery().editable()).containsExactly(true, false);
    }
}

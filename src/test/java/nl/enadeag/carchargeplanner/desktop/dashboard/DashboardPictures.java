package nl.enadeag.carchargeplanner.desktop.dashboard;

import nl.enadeag.carchargeplanner.desktop.Fx;
import nl.enadeag.carchargeplanner.desktop.Pictures;
import nl.enadeag.carchargeplanner.desktop.Pictures.Frame;
import nl.enadeag.carchargeplanner.desktop.Pictures.Studio;
import nl.enadeag.carchargeplanner.desktop.app.Units;
import nl.enadeag.carchargeplanner.desktop.dashboard.outlook.DashboardModel;
import nl.enadeag.carchargeplanner.desktop.dashboard.outlook.OutlookMapper;
import nl.enadeag.carchargeplanner.desktop.text.Language;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.Engine;
import nl.enadeag.carchargeplanner.engine.ManualScheduler;
import nl.enadeag.carchargeplanner.engine.away.AwayFile;
import nl.enadeag.carchargeplanner.engine.battery.BatteryFile;
import nl.enadeag.carchargeplanner.engine.calendar.CalendarFeed;
import nl.enadeag.carchargeplanner.engine.calendar.CalendarFetcher;
import nl.enadeag.carchargeplanner.engine.calendar.ConsentFile;
import nl.enadeag.carchargeplanner.engine.calendar.IcsReader;
import nl.enadeag.carchargeplanner.engine.config.ConfigFile;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastCache;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastProvider;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastService;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastStep;
import nl.enadeag.carchargeplanner.engine.forecast.ProviderInfo;
import nl.enadeag.carchargeplanner.engine.forecast.Providers;
import nl.enadeag.carchargeplanner.engine.forecast.ReplayHttp;
import nl.enadeag.carchargeplanner.engine.forecast.SolarSite;
import nl.enadeag.carchargeplanner.engine.forecast.providers.ForecastSolar;
import nl.enadeag.carchargeplanner.engine.forecast.providers.OpenMeteo;
import nl.enadeag.carchargeplanner.engine.notify.AnnouncedWindows;
import nl.enadeag.carchargeplanner.engine.notify.Announcer;
import nl.enadeag.carchargeplanner.engine.notify.ToldTrips;
import nl.enadeag.carchargeplanner.engine.secrets.ApiKeys;
import nl.enadeag.carchargeplanner.engine.secrets.NoKeychain;
import nl.enadeag.carchargeplanner.engine.secrets.SecretsFile;
import nl.enadeag.carchargeplanner.engine.trip.Trip;
import nl.enadeag.carchargeplanner.engine.trip.TripFile;
import nl.enadeag.carchargeplanner.host.AppName;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.application.ColorScheme;
import javafx.scene.control.Button;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

/**
 * The documentation's pictures of the dashboard: the example home's plan, made by the engine from a forecast and a
 * time that never change, so the pictures are the same on every run.
 */
public final class DashboardPictures {

    private static final Path EXAMPLE = Path.of("docs", "example-engine.json");
    private static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");
    /** A Friday in summer, mid-morning. */
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 3);
    private static final Instant NOW = TODAY.atTime(10, 20).atZone(BRUSSELS).toInstant();
    /** The sun's peak in kW from today on: a grey Friday, a sunnier Saturday, then two grey days. */
    private static final double[] PEAKS = {1.3, 2.6, 0.9, 0.8};
    /** The dashboard's width as the app makes it: 30 em of 14 px. */
    private static final double WIDTH = 30 * 14;
    /** The gap between the dashboards side by side. */
    private static final int GAP = 20;

    private DashboardPictures() {
    }

    /**
     * Three dashboards side by side: today with its grid advice, Saturday with its charge window and the expected
     * sun, and today in the window with the chart's readout and the battery level being entered.
     *
     * @param scheme light or dark
     * @param folder an empty folder for the engine's files
     * @return the picture
     * @throws Exception when the plan or the picture cannot be made
     */
    public static WritableImage charts(ColorScheme scheme, Path folder) throws Exception {
        var plan = plan(folder);
        return Fx.call(() -> Pictures.sideBySide(GAP,
                dashboard(plan, scheme, Frame.POPUP, 1, view -> clickDay(view, 0)),
                dashboard(plan, scheme, Frame.POPUP, 1, view -> clickDay(view, 1)),
                dashboard(plan, scheme, Frame.WINDOW, 1, view -> {
                    view.selectDay(0);
                    // the readout moves along the chart's hours from the first: to the one from 13:00
                    for (int hour = 5; hour <= 13; hour++) {
                        view.lookup(".solar-chart").fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.RIGHT,
                                false, false, false, false));
                    }
                    button(view, TextKey.BATTERY_UPDATE).fire();
                })));
    }

    /**
     * Two dashboards side by side on Sunday, the day of a trip: the advice for the trip, which charges on Saturday's
     * sun and then from the grid, and the trip with its choices; and a new trip being entered.
     *
     * @param scheme light or dark
     * @param folder an empty folder for the engine's files
     * @return the picture
     * @throws Exception when the plan or the picture cannot be made
     */
    public static WritableImage trip(ColorScheme scheme, Path folder) throws Exception {
        return trip(scheme, folder, Language.ENGLISH, 1);
    }

    /**
     * The picture of {@link #trip(ColorScheme, Path)} in a language and at a display scaling.
     *
     * @param scheme light or dark
     * @param folder an empty folder for the engine's files
     * @param language the language of the texts
     * @param zoom the system's display scaling: 1 for 100 %, 2 for 200 %
     * @return the picture
     * @throws Exception when the plan or the picture cannot be made
     */
    static WritableImage trip(ColorScheme scheme, Path folder, Language language, double zoom) throws Exception {
        var sunday = TODAY.plusDays(2);
        var trip = new Trip(sunday.atTime(8, 30).atZone(BRUSSELS).toInstant(),
                sunday.atTime(19, 0).atZone(BRUSSELS).toInstant(), 150, false);
        var plan = plan(folder, language, List.of(trip));
        var messages = new Messages(language);
        return Fx.call(() -> Pictures.sideBySide(GAP,
                dashboard(plan, scheme, Frame.POPUP, zoom, messages, view -> clickDay(view, 2)),
                dashboard(plan, scheme, Frame.POPUP, zoom, messages, view -> {
                    view.selectDay(2);
                    button(view, messages, TextKey.TRIP_ADD).fire();
                })));
    }

    /**
     * Today's dashboard in its window at 200 % display scaling, the focus ring on the battery's Update.
     *
     * @param scheme light or dark
     * @param folder an empty folder for the engine's files
     * @return the picture
     * @throws Exception when the plan or the picture cannot be made
     */
    public static WritableImage at200(ColorScheme scheme, Path folder) throws Exception {
        var plan = plan(folder);
        return Fx.call(() -> dashboard(plan, scheme, Frame.WINDOW, 2,
                view -> button(view, TextKey.BATTERY_UPDATE).requestFocus()));
    }

    /** One dashboard, in English, as wide as the app makes it, and as tall as its day asks. */
    private static WritableImage dashboard(DashboardModel plan, ColorScheme scheme, Frame frame,
            double zoom, Consumer<DashboardView> ready) {
        return dashboard(plan, scheme, frame, zoom, new Messages(Language.ENGLISH), ready);
    }

    /** One dashboard, as wide as the app makes it, and as tall as its day asks. */
    private static WritableImage dashboard(DashboardModel plan, ColorScheme scheme, Frame frame,
            double zoom, Messages messages, Consumer<DashboardView> ready) {
        var view = new DashboardView(() -> { }, () -> { }, Fx.REDUCED_MOTION, AppName.TITLE, messages);
        view.setDetachable(frame == Frame.POPUP);
        view.onBatteryEntered(_ -> { });
        view.onOneOffAwayChange(_ -> { }, _ -> { }, Models.CLOCK);
        view.onTripChange(_ -> { }, (_, _) -> { }, _ -> { }, Models.CLOCK);
        view.lookup(".day-tabs").setStyle("-fx-open-tab-animation: none;"); // a growing tab is narrow on purpose
        view.show(plan);
        var studio = new Studio(view, scheme, frame, zoom, WIDTH, Optional.empty());
        try {
            ready.accept(view);
            return studio.take();
        } finally {
            studio.close();
        }
    }

    /**
     * Shows a day and puts the focus on its main card, as a click on the card does: the popup then shows no focus ring,
     * which it does on the first button while the focus has not moved since it opened.
     */
    private static void clickDay(DashboardView view, int day) {
        view.selectDay(day);
        view.applyCss();
        view.lookup(".main-card").requestFocus();
    }

    private static Button button(DashboardView view, TextKey text) {
        return button(view, new Messages(Language.ENGLISH), text);
    }

    private static Button button(DashboardView view, Messages messages, TextKey text) {
        return view.lookupAll(".button").stream().map(Button.class::cast)
                .filter(button -> button.getText().equals(messages.text(text)))
                .findFirst().orElseThrow(() -> new AssertionError("no button " + text));
    }

    /** The example home's plan as the dashboard shows it, made by the engine at a fixed time. */
    private static DashboardModel plan(Path folder) throws IOException {
        return plan(folder, Language.ENGLISH, List.of());
    }

    /** The same with trips, in a language. */
    private static DashboardModel plan(Path folder, Language language, List<Trip> trips) throws IOException {
        var config = folder.resolve("engine.json");
        Files.copy(EXAMPLE, config);
        var providers = new Providers(List.of(new Played(new OpenMeteo(_ -> {
            throw new IOException("no network");
        }).info()), new ForecastSolar(_ -> {
            throw new IOException("no network");
        })));
        var apiKeys = new ApiKeys(new NoKeychain(), new SecretsFile(folder.resolve("secrets.json")));
        var awayFile = new AwayFile(folder.resolve("away.json"));
        var engine = new Engine(new ConfigFile(config),
                new ForecastService(providers, new ForecastCache(folder.resolve("cache")), apiKeys), providers, apiKeys,
                awayFile, new TripFile(folder.resolve("trips.json")), new BatteryFile(folder.resolve("battery.json")),
                new ConsentFile(folder.resolve("calendar-consents.json")),
                new CalendarFeed(new CalendarFetcher(new ReplayHttp(List.of())), new IcsReader()),
                Clock.fixed(NOW, ZoneOffset.UTC),
                new Announcer(new AnnouncedWindows(folder.resolve("announced.json")),
                        new ToldTrips(folder.resolve("trip-notices.json")), _ -> { }),
                new ManualScheduler());
        try {
            for (var trip : trips) {
                engine.addTrip(trip);
            }
            return new OutlookMapper(new Messages(language)).map(engine.outlook(), awayFile.periods(), engine.trips(),
                    Units.METRIC);
        } finally {
            engine.stop();
        }
    }

    /** Open-Meteo's texts and settings, with the forecast of {@link #PEAKS} instead of an answer from the network. */
    private record Played(ProviderInfo info) implements ForecastProvider {

        @Override
        public List<ForecastStep> fetch(SolarSite system, Map<String, String> settings, Instant now) {
            var steps = new ArrayList<ForecastStep>();
            for (int day = 0; day < PEAKS.length; day++) {
                var midnight = TODAY.plusDays(day).atStartOfDay(BRUSSELS).toInstant();
                for (int hour = 0; hour < 24; hour++) {
                    // sun from 06:00 to 21:00, highest at half past one; a grey day comes and goes in waves
                    double middle = hour + 0.5;
                    double bell = middle > 6 && middle < 21 ? Math.sin(Math.PI * (middle - 6) / 15) : 0;
                    double clouds = PEAKS[day] < 2 ? 0.75 + 0.25 * Math.cos(hour * 1.3) : 1;
                    var start = midnight.plusSeconds(hour * 3600L);
                    steps.add(new ForecastStep(start, start.plusSeconds(3600), PEAKS[day] * bell * clouds));
                }
            }
            return steps;
        }
    }
}

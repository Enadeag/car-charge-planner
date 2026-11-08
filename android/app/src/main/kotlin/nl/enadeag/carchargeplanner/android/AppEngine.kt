package nl.enadeag.carchargeplanner.android

import java.nio.file.Path
import java.time.Clock
import java.util.concurrent.Executors
import nl.enadeag.carchargeplanner.engine.Engine
import nl.enadeag.carchargeplanner.engine.away.AwayFile
import nl.enadeag.carchargeplanner.engine.battery.BatteryFile
import nl.enadeag.carchargeplanner.engine.config.ConfigFile
import nl.enadeag.carchargeplanner.engine.forecast.ForecastCache
import nl.enadeag.carchargeplanner.engine.forecast.ForecastService
import nl.enadeag.carchargeplanner.engine.forecast.HttpGet
import nl.enadeag.carchargeplanner.engine.forecast.Providers
import nl.enadeag.carchargeplanner.engine.forecast.providers.ForecastSolar
import nl.enadeag.carchargeplanner.engine.forecast.providers.OpenMeteo
import nl.enadeag.carchargeplanner.engine.notify.AnnouncedWindows
import nl.enadeag.carchargeplanner.engine.notify.Announcer
import nl.enadeag.carchargeplanner.engine.notify.Notifier
import nl.enadeag.carchargeplanner.engine.notify.ToldTrips
import nl.enadeag.carchargeplanner.engine.secrets.ApiKeys
import nl.enadeag.carchargeplanner.engine.store.PrivateFiles
import nl.enadeag.carchargeplanner.engine.trip.TripFile

/**
 * Makes the app's [Engine] from the platform's parts, with the same files and the same forecast providers as the
 * desktop app's (`DesktopApp.engine`), so a settings file moves between the two as it is. The engine is not started,
 * so it makes no rounds of its own: the owner is told what is due only in a round that the work in the background
 * makes (`background.BackgroundRounds`).
 */
internal object AppEngine {

    /** The settings file in the data folder, in the desktop's format. */
    const val SETTINGS_FILE = "engine.json"

    /**
     * The app's engine on the platform's parts.
     *
     * @param platform the process's one set of parts, which the app's Application makes
     * @param notifier tells the owner about an announcement; the engine calls it only in a round
     * @param clock what the engine plans and announces from; the same as the notifications'
     * @return the engine, with nothing read or fetched yet
     */
    fun build(platform: AndroidPlatform, notifier: Notifier, clock: Clock): Engine =
        build(platform.dataFolder(), platform.http(), platform.files(), platform.apiKeys(), notifier, clock)

    /**
     * The app's engine on these parts, for a test that keeps its files elsewhere.
     *
     * @param dataFolder where the settings file, the trips, the away periods and the forecast cache are kept
     * @param http the way to the network, for the forecast providers
     * @param files how the engine writes its files
     * @param apiKeys the store of the providers' secret settings
     * @param notifier tells the owner about an announcement; the engine calls it only in a round
     * @param clock what the engine plans and announces from; the same as the notifications'
     * @return the engine, with nothing read or fetched yet
     */
    fun build(dataFolder: Path, http: HttpGet, files: PrivateFiles, apiKeys: ApiKeys, notifier: Notifier,
        clock: Clock): Engine {
        val providers = providers(http)
        val announcer = Announcer(AnnouncedWindows(dataFolder.resolve("announced.json"), files),
            ToldTrips(dataFolder.resolve("trip-notices.json"), files), notifier)
        // a thread is made only for the first task, which only a start gives it
        val planner = Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, "planner").apply { isDaemon = true }
        }
        return Engine(ConfigFile(dataFolder.resolve(SETTINGS_FILE), files),
            ForecastService(providers, ForecastCache(dataFolder.resolve("forecasts"), files), apiKeys), providers,
            apiKeys, AwayFile(dataFolder.resolve("away.json"), files),
            TripFile(dataFolder.resolve("trips.json"), files),
            BatteryFile(dataFolder.resolve("battery.json"), files), clock, announcer, planner)
    }

    /**
     * The forecast providers the app offers, in the desktop's order; a new provider is added here and in
     * `DesktopApp.engine` (docs/providers.md).
     *
     * @param http the way to the network the providers ask through
     * @return Open-Meteo, then Forecast.Solar
     */
    fun providers(http: HttpGet): Providers = Providers(listOf(OpenMeteo(http), ForecastSolar(http)))
}

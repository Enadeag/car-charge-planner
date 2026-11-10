package nl.enadeag.carchargeplanner.android

import java.nio.file.Path
import java.time.Clock
import java.util.concurrent.Executors
import nl.enadeag.carchargeplanner.engine.Engine
import nl.enadeag.carchargeplanner.engine.forecast.HttpGet
import nl.enadeag.carchargeplanner.engine.notify.Notifier
import nl.enadeag.carchargeplanner.engine.secrets.ApiKeys
import nl.enadeag.carchargeplanner.engine.store.PrivateFiles

/**
 * Makes the app's [Engine] from the platform's parts, through [Engine.inFolder], with the settings file in the data
 * folder: the same files and the same forecast providers as the desktop app's, so a settings file moves between the
 * two as it is. The engine is not started, so it makes no rounds of its own: the owner is told what is due only in a
 * round that the work in the background makes (`background.BackgroundRounds`).
 */
internal object AppEngine {

    /**
     * @param notifier tells the owner about an announcement; the engine calls it only in a round
     * @param clock what the engine plans and announces from; the same as the notifications'
     * @return the engine, with nothing read or fetched yet
     */
    fun build(platform: AndroidPlatform, notifier: Notifier, clock: Clock): Engine =
        build(platform.dataFolder(), platform.http(), platform.files(), platform.apiKeys(), notifier, clock)

    /**
     * For a test that keeps its files elsewhere.
     *
     * @param notifier tells the owner about an announcement; the engine calls it only in a round
     * @param clock what the engine plans and announces from; the same as the notifications'
     * @return the engine, with nothing read or fetched yet
     */
    fun build(dataFolder: Path, http: HttpGet, files: PrivateFiles, apiKeys: ApiKeys, notifier: Notifier,
        clock: Clock): Engine {
        // a thread is made only for the first task, which only a start gives it
        val planner = Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, "planner").apply { isDaemon = true }
        }
        return Engine.inFolder(dataFolder, http, files, apiKeys, notifier, clock, planner)
    }
}

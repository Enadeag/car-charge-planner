package nl.enadeag.carchargeplanner.android.background

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import nl.enadeag.carchargeplanner.android.AppEngine
import nl.enadeag.carchargeplanner.android.CarChargePlannerApp
import nl.enadeag.carchargeplanner.android.testPlatform
import nl.enadeag.carchargeplanner.android.notify.ProblemEpisode
import nl.enadeag.carchargeplanner.android.notify.EpisodeFlag
import nl.enadeag.carchargeplanner.android.store.EngineOnDispatcher
import nl.enadeag.carchargeplanner.android.store.FakeEngineAccess
import nl.enadeag.carchargeplanner.android.ui.notify.Notifications
import nl.enadeag.carchargeplanner.android.ui.notify.createNoticeChannels
import nl.enadeag.carchargeplanner.engine.ForecastUse
import nl.enadeag.carchargeplanner.engine.Outlook
import nl.enadeag.carchargeplanner.engine.forecast.HttpGet
import nl.enadeag.carchargeplanner.engine.store.FilePrivacy
import nl.enadeag.carchargeplanner.engine.store.PrivateFiles
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** The worker of the rounds that ask the providers, made by WorkManager's test builder, on the app's parts. */
@RunWith(AndroidJUnit4::class)
class RoundWorkerTest {

    private companion object {
        /** The docs' example settings file: Europe/Brussels, Open-Meteo, a lead of 15 minutes, away on Thursdays. */
        val EXAMPLE: Path = Path.of(requireNotNull(System.getProperty("example.config")))
        val ZONE: ZoneId = ZoneId.of("Europe/Brussels")

        /** A Monday, so the example's Thursday away does not count. */
        val DAY: LocalDate = LocalDate.of(2026, 6, 15)
    }

    private val app = ApplicationProvider.getApplicationContext<CarChargePlannerApp>()
    private val notificationManager = app.getSystemService(NotificationManager::class.java)
    private val alarms = shadowOf(app.getSystemService(AlarmManager::class.java))
    private val background = FakeBackground()

    @Before
    fun allowNotifications() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        Settings.System.putString(app.contentResolver, Settings.System.TIME_12_24, "24")
        createNoticeChannels(app)
    }

    private fun work(): ListenableWorker.Result = runBlocking {
        TestListenableWorkerBuilder<RoundWorker>(app).build().doWork()
    }

    @Test
    fun theWorkerMakesARoundThatAsksTheProvidersAndEndsAsASuccess() {
        val access = FakeEngineAccess()
        access.roundOutlook = Outlook.NoForecast("Open-Meteo could not be reached")
        app.rounds = BackgroundRounds({ access }, { _, _ -> }, background.alarm, background.work, { true },
            Clock.systemUTC())

        assertThat(work()).isEqualTo(ListenableWorker.Result.success())
        assertThat(access.rounds).containsExactly(ForecastUse.ASK_PROVIDERS)
        assertThat(background.done).containsExactly("alarm cancelled")
    }

    @Test
    fun aRoundThatFailsStillEndsAsASuccessSoNothingIsTriedAgain() {
        val access = FakeEngineAccess()
        access.roundThrows = IllegalStateException("a fault")
        app.rounds = BackgroundRounds({ access }, { _, _ -> }, background.alarm, background.work, { true },
            Clock.systemUTC())

        assertThat(work()).isEqualTo(ListenableWorker.Result.success())
    }

    @Test
    fun theWorkerStartsAProblemsEpisode() {
        val access = FakeEngineAccess()
        access.roundOutlook = Outlook.NoForecast("Open-Meteo refused the request (status 401)", false,
            listOf(nl.enadeag.carchargeplanner.engine.config.ProviderId("open-meteo")))
        val flag = object : EpisodeFlag {
            var started = false
            override suspend fun episodeStarted() = started
            override suspend fun episodeStarted(started: Boolean) {
                this.started = started
            }
        }
        val problems = ProblemEpisode(flag, Notifications(app))
        app.rounds = BackgroundRounds({ access }, problems::afterRound, background.alarm, background.work, { true },
            Clock.systemUTC())

        work()

        assertThat(flag.started).isTrue()
        assertThat(shadowOf(notificationManager).getNotification("problems", 0)).isNotNull()
    }

    /**
     * From a real round of the app's engine, on a fixed clock and a forecast stored a few minutes before, to the
     * notice Android shows and the alarm for the next one.
     */
    @Test
    fun aRealRoundOfTheEngineEndsInAPostedNoticeAndTheNextAlarm() {
        val folder = onDisk { Files.createDirectories(app.filesDir.toPath().resolve("end-to-end")) }
        val morning = DAY.atTime(6, 0).atZone(ZONE).toInstant()
        onDisk {
            Files.copy(EXAMPLE, folder.resolve(AppEngine.SETTINGS_FILE))
            storeForecast(folder, fetchedAt = morning.minus(Duration.ofMinutes(5)))
        }
        val asked = mutableListOf<HttpGet.Request>()

        fun access(at: Instant) = Clock.fixed(at, ZoneOffset.UTC).let { clock ->
            val engine = AppEngine.build(folder, { request -> asked += request; throw IOException("offline") },
                PrivateFiles(FilePrivacy.NONE), platform().apiKeys(), Notifications(app, clock), clock)
            EngineOnDispatcher(engine, Dispatchers.IO) to clock
        }

        // where the plan of the morning puts its first notice
        val (early, _) = access(morning)
        val due = runBlocking { early.nextNoticeDue(early.outlook()) }
        assertThat(due).isNotNull().isAfter(morning)

        // Open-Meteo answered a few minutes before the notice, so the round asks nobody
        val noticeAt = due!!.plus(Duration.ofMinutes(1))
        onDisk { storeForecast(folder, fetchedAt = noticeAt.minus(Duration.ofMinutes(5))) }
        val (atNotice, clock) = access(noticeAt)
        val alarm = AndroidNoticeAlarm(app)
        // the alarm that woke the phone for this notice, as the morning's round set it
        alarm.set(noticeAt)
        app.rounds = BackgroundRounds({ atNotice }, { _, _ -> }, alarm, background.work, { true }, clock)

        assertThat(work()).isEqualTo(ListenableWorker.Result.success())

        // the window starts at the notice's due time plus the example's lead of 15 minutes
        val windowStart = due.plus(Duration.ofMinutes(15))
        assertThat(shadowOf(notificationManager).allNotifications).hasSize(1)
        val posted = shadowOf(notificationManager).getNotification("solar_window:${windowStart.epochSecond}", 0)
        assertThat(posted.channelId).isEqualTo("charge_windows")
        assertThat(posted.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
            .isEqualTo("Charge window 09:00 to 17:00")
        assertThat(asked).isEmpty()
        // tomorrow's window lies beyond the six hours a forecast is trusted for, so no notice is due next
        assertThat(alarms.scheduledAlarms).isEmpty()
    }

    private fun platform() = testPlatform(app.filesDir, File(app.filesDir, "no_backup"))

    /**
     * Two cloudless days of hourly steps in the example's cache file of Open-Meteo, under the key the engine gives the
     * example's home, as if Open-Meteo had answered at [fetchedAt].
     */
    private fun storeForecast(folder: Path, fetchedAt: Instant) {
        val steps = listOf(DAY, DAY.plusDays(1)).flatMap { day ->
            val start = day.atStartOfDay(ZONE).toInstant()
            (0 until 24).map { hour ->
                val stepStart = start.plus(Duration.ofHours(hour.toLong()))
                val sun = sin(PI * (hour + 0.5 - 6.0) / 14.0)
                "[\"$stepStart\",\"${stepStart.plus(Duration.ofHours(1))}\",${max(0.0, 4.5 * sun)}]"
            }
        }
        val key = """{"latitude":51.0,"longitude":5.0,"timezone":"Europe/Brussels","arrays":[{"kwp":5.0,""" +
            """"tiltDeg":35.0,"azimuthDeg":0.0,"systemLosses":0.14}],"settings":{}}"""
        Files.createDirectories(folder.resolve("forecasts"))
        folder.resolve("forecasts/forecast-open-meteo.json").toFile().writeText(
            """{"key":$key,"fetchedAt":"$fetchedAt","steps":[${steps.joinToString(",")}],""" +
                """"cooldownUntil":null,"refreshFailed":false}""")
    }
}

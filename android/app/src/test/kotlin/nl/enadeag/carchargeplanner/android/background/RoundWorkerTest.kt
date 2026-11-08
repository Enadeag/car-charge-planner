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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import nl.enadeag.carchargeplanner.android.AppEngine
import nl.enadeag.carchargeplanner.android.CarChargePlannerApp
import nl.enadeag.carchargeplanner.android.MainActivity
import nl.enadeag.carchargeplanner.android.testPlatform
import nl.enadeag.carchargeplanner.android.notify.ProblemEpisode
import nl.enadeag.carchargeplanner.android.notify.EpisodeFlag
import nl.enadeag.carchargeplanner.android.store.EngineOnDispatcher
import nl.enadeag.carchargeplanner.android.store.FakeEngineAccess
import nl.enadeag.carchargeplanner.android.store.TripSave
import nl.enadeag.carchargeplanner.android.ui.notify.Notifications
import nl.enadeag.carchargeplanner.android.ui.notify.createNoticeChannels
import nl.enadeag.carchargeplanner.engine.ForecastUse
import nl.enadeag.carchargeplanner.engine.Outlook
import nl.enadeag.carchargeplanner.engine.forecast.HttpGet
import nl.enadeag.carchargeplanner.engine.store.FilePrivacy
import nl.enadeag.carchargeplanner.engine.store.PrivateFiles
import nl.enadeag.carchargeplanner.engine.plan.TripGridCharge
import nl.enadeag.carchargeplanner.engine.trip.Trip
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
            Clock.systemUTC(), background.missed, background.oneRoundAtATime)

        assertThat(work()).isEqualTo(ListenableWorker.Result.success())
        assertThat(access.rounds).containsExactly(ForecastUse.ASK_PROVIDERS)
        assertThat(background.done).containsExactly("alarm cancelled")
    }

    @Test
    fun aRoundThatFailsStillEndsAsASuccessSoNothingIsTriedAgain() {
        val access = FakeEngineAccess()
        access.roundThrows = IllegalStateException("a fault")
        app.rounds = BackgroundRounds({ access }, { _, _ -> }, background.alarm, background.work, { true },
            Clock.systemUTC(), background.missed, background.oneRoundAtATime)

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
        val problems = ProblemEpisode(flag, Notifications(app, Clock.systemUTC()))
        app.rounds = BackgroundRounds({ access }, problems::afterRound, background.alarm, background.work, { true },
            Clock.systemUTC(), background.missed, background.oneRoundAtATime)

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
        app.rounds = BackgroundRounds({ atNotice }, { _, _ -> }, alarm, background.work, { true }, clock,
            background.missed, background.oneRoundAtATime)

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

    /**
     * The app's engine in a folder of its own with the docs' example settings and a forecast stored five minutes
     * before [at], on a clock fixed at [at], behind the app's own [ReschedulingAccess] that schedules the work in
     * memory as the app's does, through [BackgroundSchedule].
     */
    private fun appAccess(name: String, at: Instant): ReschedulingAccess {
        val folder = onDisk { Files.createDirectories(app.filesDir.toPath().resolve(name)) }
        onDisk {
            Files.copy(EXAMPLE, folder.resolve(AppEngine.SETTINGS_FILE))
            storeForecast(folder, fetchedAt = at.minus(Duration.ofMinutes(5)))
        }
        val clock = Clock.fixed(at, ZoneOffset.UTC)
        val engine = AppEngine.build(folder, { throw IOException("offline") }, PrivateFiles(FilePrivacy.NONE),
            platform().apiKeys(), Notifications(app, clock), clock)
        lateinit var access: ReschedulingAccess
        val schedule = BackgroundSchedule({ access }, background.work, background.alarm, flowOf(false),
            background.oneRoundAtATime) { true }
        access = ReschedulingAccess(EngineOnDispatcher(engine, Dispatchers.IO)) {
            runBlocking { schedule.refresh() }
        }
        app.rounds = BackgroundRounds({ access }, { _, _ -> }, background.alarm, background.work, { true }, clock,
            background.missed, background.oneRoundAtATime)
        return access
    }

    /**
     * A trip saved through the app's engine access asks for a round now, and that round sets the alarm at the moment
     * the trip's grid charge is to be told: its start less the example's lead of 15 minutes.
     */
    @Test
    fun aSavedTripLeadsToARoundNowAndThenAnAlarmAtTheTripsChargeStart() {
        // today's window has passed and tomorrow's lies beyond the six hours a forecast is trusted for
        val evening = DAY.atTime(20, 0).atZone(ZONE).toInstant()
        val access = appAccess("trip-alarm", evening)
        assertThat(runBlocking { access.nextNoticeDue(access.outlook()) }).isNull()
        // tomorrow early, farther than the battery holds, so a grid charge tonight
        val trip = Trip(DAY.plusDays(1).atTime(7, 0).atZone(ZONE).toInstant(),
            DAY.plusDays(1).atTime(13, 0).atZone(ZONE).toInstant(), 150.0, false)

        assertThat(runBlocking { access.addTrip(trip) }).isEqualTo(TripSave.Saved(listOf(trip)))
        assertThat(background.done).containsExactly("hourly any", "once any")

        assertThat(work()).isEqualTo(ListenableWorker.Result.success())

        val grid = (runBlocking { access.outlook() } as Outlook.Planned).tripAdvice().single().grid().orElseThrow()
        assertThat(grid.start()).isEqualTo(TripGridCharge.Start.PLANNED)
        assertThat(grid.window().start()).isEqualTo(DAY.atTime(21, 30).atZone(ZONE).toInstant())
        assertThat(background.done.last()).isEqualTo("alarm ${DAY.atTime(21, 15).atZone(ZONE).toInstant()}")
    }

    /**
     * The hourly round, on the app's engine with trips saved through its access, tells a trip that a full battery
     * cannot make to plan a charging stop, and asks for the battery level for a trip that depends on it, by a
     * notification whose action opens the level's entry, as no level was entered.
     */
    @Test
    fun theHourlyRoundTellsATripTooFarAndAsksTheBatteryLevelForATightOne() {
        val evening = DAY.atTime(19, 30).atZone(ZONE).toInstant()
        val access = appAccess("trip-notices", evening)
        val tight = Trip(DAY.plusDays(1).atTime(7, 0).atZone(ZONE).toInstant(),
            DAY.plusDays(1).atTime(11, 0).atZone(ZONE).toInstant(), 150.0, false)
        val tooFar = Trip(DAY.plusDays(1).atTime(12, 0).atZone(ZONE).toInstant(),
            DAY.plusDays(1).atTime(22, 0).atZone(ZONE).toInstant(), 1000.0, false)
        runBlocking {
            access.addTrip(tight)
            access.addTrip(tooFar)
        }

        assertThat(work()).isEqualTo(ListenableWorker.Result.success())

        val notifications = shadowOf(notificationManager)
        val short = notifications.getNotification("trip_short:${tooFar.departure().epochSecond}", 0)
        assertThat(short.extras.getCharSequence(Notification.EXTRA_TITLE).toString()).isEqualTo("Plan a charging stop")
        assertThat(short.channelId).isEqualTo("battery_trip_checks")
        val check = notifications.getNotification("battery_check:${tight.departure().epochSecond}", 0)
        assertThat(check.channelId).isEqualTo("battery_trip_checks")
        assertThat(check.actions.map { it.title.toString() }).containsExactly("Enter battery level")
        assertThat(shadowOf(check.actions[0].actionIntent).savedIntent.action)
            .isEqualTo(MainActivity.OPEN_BATTERY_ENTRY)
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

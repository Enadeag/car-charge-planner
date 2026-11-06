package nl.enadeag.carchargeplanner.android.background

import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import nl.enadeag.carchargeplanner.android.store.FakeEngineAccess
import nl.enadeag.carchargeplanner.engine.ForecastUse
import nl.enadeag.carchargeplanner.engine.Outlook
import nl.enadeag.carchargeplanner.engine.config.ConfigProblem
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class BackgroundRoundsTest {

    private val now = Instant.parse("2026-06-07T09:00:00Z")
    private val access = FakeEngineAccess()
    private val background = FakeBackground()
    private var allowed = true

    /** The outlooks and uses the Problems notification's episodes were given, as the alarm and the work's steps. */
    private var afterRound: suspend (Outlook, ForecastUse) -> Unit = { outlook, use ->
        background.done += "after ${outlook.javaClass.simpleName} $use"
    }

    private val rounds = BackgroundRounds({ access }, { outlook, use -> afterRound(outlook, use) }, background.alarm,
        background.work, { allowed }, Clock.fixed(now, ZoneOffset.UTC), background.missed)

    private val noForecast = Outlook.NoForecast("Open-Meteo could not be reached")

    @ParameterizedTest
    @EnumSource(ForecastUse::class)
    fun aRoundTellsTheProblemsEpisodesThenSetsTheAlarmForTheNextNotice(use: ForecastUse) = runTest {
        access.roundOutlook = noForecast
        access.noticeDue = now.plus(Duration.ofHours(2))

        rounds.run(use)

        assertThat(access.rounds).containsExactly(use)
        assertThat(access.dueAskedFor).containsExactly(noForecast)
        assertThat(background.done).containsExactly("after NoForecast $use", "alarm ${now.plus(Duration.ofHours(2))}")
    }

    @Test
    fun aNoticeDueWithinNineMinutesGetsItsAlarmNineMinutesFromNow() = runTest {
        access.roundOutlook = noForecast
        access.noticeDue = now.plus(Duration.ofMinutes(3))

        rounds.run(ForecastUse.STORED_ONLY)

        assertThat(background.done).last().isEqualTo("alarm ${now.plus(Duration.ofMinutes(9))}")
    }

    @Test
    fun withNothingDueTheAlarmIsCancelledAndTheHourlyWorkStays() = runTest {
        access.roundOutlook = Outlook.ConfigUnusable(ConfigProblem.Kind.INVALID, Path.of("engine.json"), "detail")

        rounds.run(ForecastUse.ASK_PROVIDERS)

        assertThat(background.done).containsExactly("after ConfigUnusable ASK_PROVIDERS", "alarm cancelled")
    }

    @Test
    fun withoutASettingsFileTheEpisodeMayEndAndTheWorkAndTheAlarmAreCancelled() = runTest {
        access.roundOutlook = Outlook.NotSetUp(Path.of("engine.json"))
        access.noticeDue = now.plus(Duration.ofHours(2))

        rounds.run(ForecastUse.ASK_PROVIDERS)

        assertThat(background.done).containsExactly("after NotSetUp ASK_PROVIDERS", "alarm cancelled",
            "work cancelled")
        assertThat(access.dueAskedFor).isEmpty()
    }

    @Test
    fun whileNotificationsAreNotAllowedThereIsNoRoundAndTheWorkAndTheAlarmAreCancelled() = runTest {
        allowed = false
        access.noticeDue = now.plus(Duration.ofHours(2))

        rounds.run(ForecastUse.ASK_PROVIDERS)

        assertThat(access.rounds).isEmpty()
        assertThat(background.done).containsExactly("alarm cancelled", "work cancelled")
    }

    @ParameterizedTest
    @EnumSource(ForecastUse::class)
    fun aRoundThatFailsEndsQuietlyAndSetsTheAlarmNineMinutesAheadSoTheAlarmsRoundTriesAgain(use: ForecastUse) =
        runTest {
            access.roundThrows = IllegalStateException("a fault")
            access.noticeDue = now.plus(Duration.ofHours(2))

            rounds.run(use)

            assertThat(access.rounds).containsExactly(use)
            assertThat(access.dueAskedFor).isEmpty()
            assertThat(background.done).containsExactly("alarm ${now.plus(Duration.ofMinutes(9))}")
        }

    @Test
    fun eachRoundWithoutAnOutlookInARowSetsTheAlarmTwiceAsFarAheadUpToAnHour() = runTest {
        access.roundThrows = IllegalStateException("a fault")

        repeat(5) { rounds.run(ForecastUse.STORED_ONLY) }

        assertThat(background.done).containsExactlyElementsOf(listOf(9L, 18L, 36L, 60L, 60L)
            .map { "alarm ${now.plus(Duration.ofMinutes(it))}" })
        assertThat(background.missedRounds).isEqualTo(5)
    }

    @Test
    fun theRoundsWithoutAnOutlookAreCountedOnFromWhatWasKept() = runTest {
        access.roundThrows = IllegalStateException("a fault")
        background.missedRounds = 2

        rounds.run(ForecastUse.STORED_ONLY)

        assertThat(background.done).containsExactly("alarm ${now.plus(Duration.ofMinutes(36))}")
        assertThat(background.missedRounds).isEqualTo(3)
    }

    @Test
    fun aRoundWithAnOutlookStartsTheCountAgain() = runTest {
        background.missedRounds = 4
        access.roundOutlook = noForecast
        access.noticeDue = now.plus(Duration.ofHours(2))

        rounds.run(ForecastUse.STORED_ONLY)
        access.roundThrows = IllegalStateException("a fault")
        rounds.run(ForecastUse.STORED_ONLY)

        assertThat(background.done).containsExactly("after NoForecast STORED_ONLY",
            "alarm ${now.plus(Duration.ofHours(2))}", "alarm ${now.plus(Duration.ofMinutes(9))}")
        assertThat(background.missedRounds).isEqualTo(1)
    }

    @Test
    fun withoutASettingsFileTheCountStartsAgainToo() = runTest {
        background.missedRounds = 4
        access.roundOutlook = Outlook.NotSetUp(Path.of("engine.json"))

        rounds.run(ForecastUse.STORED_ONLY)

        assertThat(background.missedRounds).isZero()
    }

    @Test
    fun whileNotificationsAreNotAllowedTheCountStaysAsItIs() = runTest {
        allowed = false
        background.missedRounds = 2

        rounds.run(ForecastUse.STORED_ONLY)

        assertThat(background.missedRounds).isEqualTo(2)
    }

    @Test
    fun aRoundStoppedBeforeItsOutlookSetsTheAlarmNineMinutesAheadAndStaysStopped() = runTest {
        access.roundOutlook = noForecast
        access.noticeDue = now.plus(Duration.ofHours(2))
        access.roundGate = CompletableDeferred()
        var ended = false

        val run = launch {
            rounds.run(ForecastUse.ASK_PROVIDERS)
            ended = true
        }
        runCurrent()
        run.cancel()
        run.join()

        assertThat(ended).isFalse()
        assertThat(access.dueAskedFor).isEmpty()
        assertThat(background.done).containsExactly("alarm ${now.plus(Duration.ofMinutes(9))}")
    }

    @Test
    fun aFailureOfTheEpisodesStillSetsTheAlarm() = runTest {
        access.roundOutlook = noForecast
        access.noticeDue = now.plus(Duration.ofHours(2))
        afterRound = { _, _ -> throw IllegalStateException("the store failed") }

        rounds.run(ForecastUse.ASK_PROVIDERS)

        assertThat(background.done).containsExactly("alarm ${now.plus(Duration.ofHours(2))}")
    }

    @Test
    fun aFailureToCancelTheWorkIsNoFailureOfTheRun() = runTest {
        allowed = false
        background.workCancelThrows = IllegalStateException("WorkManager is not initialized")

        rounds.run(ForecastUse.STORED_ONLY)

        assertThat(background.done).containsExactly("alarm cancelled")
    }

    @Test
    fun anAlarmThatCannotBeSetIsNoFailureOfTheRun() = runTest {
        access.roundOutlook = noForecast
        access.noticeDue = now.plus(Duration.ofHours(2))
        val failing = BackgroundRounds({ access }, { _, _ -> }, object : NoticeAlarm {
            override fun set(at: Instant) = throw SecurityException("too many alarms")
            override fun cancel() = Unit
        }, background.work, { true }, Clock.fixed(now, ZoneOffset.UTC), background.missed)

        failing.run(ForecastUse.STORED_ONLY)

        assertThat(access.dueAskedFor).containsExactly(noForecast)
    }

    @Test
    fun aRoundStoppedAfterItsOutlookStillSetsTheAlarm() = runTest {
        access.roundOutlook = noForecast
        access.noticeDue = now.plus(Duration.ofHours(2))
        val stopHere = CompletableDeferred<Unit>()
        afterRound = { _, _ -> stopHere.await() }
        var ended = false

        val run = launch {
            rounds.run(ForecastUse.ASK_PROVIDERS)
            ended = true
        }
        runCurrent()
        run.cancel()
        run.join()

        assertThat(ended).isFalse()
        assertThat(background.done).containsExactly("alarm ${now.plus(Duration.ofHours(2))}")
    }

    @Test
    fun aRoundStartedWhileAnotherRunsWaitsForItsAlarm() = runTest {
        access.roundOutlook = noForecast
        access.noticeDue = now.plus(Duration.ofHours(2))
        val gate = CompletableDeferred<Unit>()
        access.roundGate = gate

        val first = launch { rounds.run(ForecastUse.ASK_PROVIDERS) }
        runCurrent()
        val second = launch { rounds.run(ForecastUse.STORED_ONLY) }
        runCurrent()

        assertThat(access.rounds).containsExactly(ForecastUse.ASK_PROVIDERS)
        gate.complete(Unit)
        first.join()
        second.join()
        assertThat(access.rounds).containsExactly(ForecastUse.ASK_PROVIDERS, ForecastUse.STORED_ONLY)
        assertThat(background.done.filter { it.startsWith("after") })
            .containsExactly("after NoForecast ASK_PROVIDERS", "after NoForecast STORED_ONLY")
        assertThat(background.done.indexOf("after NoForecast STORED_ONLY")).isGreaterThan(1)
    }
}

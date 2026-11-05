package nl.enadeag.carchargeplanner.android.notify

import java.nio.file.Path
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import nl.enadeag.carchargeplanner.android.home.Plans
import nl.enadeag.carchargeplanner.engine.ForecastUse
import nl.enadeag.carchargeplanner.engine.Outlook
import nl.enadeag.carchargeplanner.engine.config.ConfigProblem
import nl.enadeag.carchargeplanner.engine.config.ProviderId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class ProblemEpisodeTest {

    /** The episode's flag in memory, and the notification as what was done to it, in order. */
    private class Phone : EpisodeFlag, ProblemNotice {
        var started = false
        val done = mutableListOf<String>()

        override suspend fun episodeStarted() = started

        override suspend fun episodeStarted(started: Boolean) {
            this.started = started
            done += "flag $started"
        }

        override fun show() {
            done += "show"
        }

        override fun remove() {
            done += "remove"
        }
    }

    private val phone = Phone()
    private val episode = ProblemEpisode(phone, phone)
    private val zone = ZoneId.of("Europe/Amsterdam")
    private val openMeteo = ProviderId("open-meteo")
    private val file = Path.of("engine.json")

    private fun planned(vararg refusedBy: ProviderId): Outlook.Planned {
        val madeAt = LocalDate.of(2026, 6, 1).atTime(8, 0).atZone(zone)
        return Plans.planned(madeAt, listOf(listOf()),
            source = Plans.source(madeAt.toInstant(), keyRefusedBy = refusedBy.toList()))
    }

    private val keyRefused = Outlook.NoForecast("Open-Meteo refused the request (status 401)", false,
        listOf(openMeteo))
    private val offline = Outlook.NoForecast("Open-Meteo could not be reached")

    @ParameterizedTest
    @EnumSource(value = ConfigProblem.Kind::class, names = ["UNREADABLE", "INVALID", "NEWER_VERSION"])
    fun aSettingsFileThatCannotBeUsedIsAProblem(kind: ConfigProblem.Kind) {
        assertThat(ProblemEpisode.seen(Outlook.ConfigUnusable(kind, file, "detail")))
            .isEqualTo(ProblemEpisode.Seen.PROBLEM)
    }

    @Test
    fun aRefusedKeyIsAProblemWithoutAForecastAndOnAPlanThatAnotherForecastMade() {
        assertThat(ProblemEpisode.seen(keyRefused)).isEqualTo(ProblemEpisode.Seen.PROBLEM)
        assertThat(ProblemEpisode.seen(planned(openMeteo))).isEqualTo(ProblemEpisode.Seen.PROBLEM)
    }

    @Test
    fun aPlanWithoutARefusedKeyAndNoSettingsFileShowNoProblem() {
        assertThat(ProblemEpisode.seen(planned())).isEqualTo(ProblemEpisode.Seen.NONE)
        assertThat(ProblemEpisode.seen(Outlook.NotSetUp(file))).isEqualTo(ProblemEpisode.Seen.NONE)
    }

    @Test
    fun noForecastForAnotherReasonCannotTell() {
        assertThat(ProblemEpisode.seen(offline)).isEqualTo(ProblemEpisode.Seen.UNKNOWN)
    }

    @Test
    fun aRoundThatAskedTheProvidersAndSawAProblemStartsAnEpisodeWithOneNotification() = runTest {
        episode.afterRound(keyRefused, ForecastUse.ASK_PROVIDERS)

        assertThat(phone.done).containsExactly("flag true", "show")
    }

    @Test
    fun theNotificationComesOncePerEpisodeAlsoWhenTheProblemChanges() = runTest {
        episode.afterRound(keyRefused, ForecastUse.ASK_PROVIDERS)
        episode.afterRound(planned(openMeteo), ForecastUse.ASK_PROVIDERS)
        episode.afterRound(Outlook.ConfigUnusable(ConfigProblem.Kind.INVALID, file, "detail"),
            ForecastUse.ASK_PROVIDERS)

        assertThat(phone.done).containsExactly("flag true", "show")
    }

    @Test
    fun anEpisodeGoingOnWhenTheAppStartsIsNotToldAgain() = runTest {
        phone.started = true

        episode.afterRound(keyRefused, ForecastUse.ASK_PROVIDERS)

        assertThat(phone.done).isEmpty()
    }

    @Test
    fun aRoundOnTheStoredForecastOnlyNeitherStartsNorEndsAnEpisode() = runTest {
        episode.afterRound(keyRefused, ForecastUse.STORED_ONLY)
        phone.started = true
        episode.afterRound(planned(), ForecastUse.STORED_ONLY)

        assertThat(phone.done).isEmpty()
        assertThat(phone.started).isTrue()
    }

    @Test
    fun aRoundWithoutTheProblemEndsTheEpisodeAndTakesTheNotificationAway() = runTest {
        phone.started = true

        episode.afterRound(planned(), ForecastUse.ASK_PROVIDERS)

        assertThat(phone.done).containsExactly("remove", "flag false")
    }

    @Test
    fun anEndedEpisodeLetsTheNextProblemNotifyAgain() = runTest {
        episode.afterRound(keyRefused, ForecastUse.ASK_PROVIDERS)
        episode.afterRound(Outlook.NotSetUp(file), ForecastUse.ASK_PROVIDERS)
        episode.afterRound(keyRefused, ForecastUse.ASK_PROVIDERS)

        assertThat(phone.done).containsExactly("flag true", "show", "remove", "flag false", "flag true", "show")
    }

    @Test
    fun aRoundThatCannotTellLeavesTheEpisodeAsItIs() = runTest {
        phone.started = true

        episode.afterRound(offline, ForecastUse.ASK_PROVIDERS)

        assertThat(phone.done).isEmpty()
        assertThat(phone.started).isTrue()
    }

    @Test
    fun withoutAnEpisodeARoundWithoutTheProblemOnlyMakesSureNoNotificationIsLeft() = runTest {
        episode.afterRound(planned(), ForecastUse.ASK_PROVIDERS)

        assertThat(phone.done).containsExactly("remove")
    }

    @Test
    fun theDashboardEndsAnEpisodeWhenItsPlanNoLongerShowsTheProblem() = runTest {
        phone.started = true

        episode.dashboardSaw(planned())

        assertThat(phone.done).containsExactly("remove", "flag false")
    }

    @Test
    fun afterTheDashboardEndedAnEpisodeTheNextRoundWithTheProblemNotifiesAgain() = runTest {
        phone.started = true
        episode.dashboardSaw(planned())

        episode.afterRound(keyRefused, ForecastUse.ASK_PROVIDERS)

        assertThat(phone.done).containsExactly("remove", "flag false", "flag true", "show")
    }

    @Test
    fun theDashboardNeverStartsAnEpisode() = runTest {
        episode.dashboardSaw(keyRefused)
        episode.dashboardSaw(planned(openMeteo))
        episode.dashboardSaw(Outlook.ConfigUnusable(ConfigProblem.Kind.UNREADABLE, file, "detail"))

        assertThat(phone.done).isEmpty()
        assertThat(phone.started).isFalse()
    }

    @Test
    fun theDashboardLeavesAnEpisodeItCannotTellAbout() = runTest {
        phone.started = true

        episode.dashboardSaw(offline)
        episode.dashboardSaw(keyRefused)

        assertThat(phone.done).isEmpty()
        assertThat(phone.started).isTrue()
    }
}

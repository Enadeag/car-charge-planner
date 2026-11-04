package nl.enadeag.carchargeplanner.android.setup

import androidx.lifecycle.SavedStateHandle
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import nl.enadeag.carchargeplanner.android.afterTheSystemEndedTheApp
import nl.enadeag.carchargeplanner.android.store.FakeEngineAccess
import nl.enadeag.carchargeplanner.engine.config.Coordinates
import nl.enadeag.carchargeplanner.engine.config.ProviderId
import nl.enadeag.carchargeplanner.engine.forecast.Place
import nl.enadeag.carchargeplanner.engine.forecast.PlaceResults
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaceSearchTest {

    private val handle = SavedStateHandle()
    private val access = FakeEngineAccess()
    private val amsterdam = ZoneId.of("Europe/Amsterdam")

    private fun place(name: String, area: String = "", region: String = "Utrecht") =
        Place(name, Coordinates(52.09, 5.12), amsterdam, region, "Netherlands", area)

    private fun TestScope.search(): PlaceSearch =
        PlaceSearch(handle, access, backgroundScope, ProviderId("open-meteo")) { "nl" }

    @Test
    fun theTrimmedTextGoesToTheSearchInTheAppsLanguageOnlyWhenAsked() = runTest {
        val search = search()

        search.edit("  Utrecht ")
        runCurrent()
        assertThat(access.searched).isEmpty()
        search.search()
        runCurrent()

        assertThat(search.state.value).isEqualTo(PlaceSearch.State.Searching)
        assertThat(access.searched).containsExactly("Utrecht" to "nl")
    }

    @Test
    fun aTextLongerThanAHundredCharactersIsCutOffThere() = runTest {
        val search = search()

        search.edit("a".repeat(101))

        assertThat(search.text()).isEqualTo("a".repeat(100))
        val again = PlaceSearch(handle.afterTheSystemEndedTheApp(), access, backgroundScope, ProviderId("open-meteo"),
            language = { "nl" })
        assertThat(again.text()).hasSize(100)
    }

    @Test
    fun aPlaceWithALabelLongerThanAHundredCharactersIsShownAndKeptCutOffThere() = runTest {
        val search = search()
        search.edit("Long")
        search.search()
        runCurrent()
        access.searches.single().complete(PlaceResults.Found(listOf(place("a".repeat(120)))))
        runCurrent()

        val found = (search.state.value as PlaceSearch.State.Found).results.single()
        search.choose(found)

        assertThat(found.label).isEqualTo("a".repeat(100))
        assertThat(search.chosen.value).isEqualTo("a".repeat(100))
        assertThat(search.text()).isEqualTo("a".repeat(100))
    }

    @Test
    fun atMostEightPlacesShowAndTwoThatReadAlikeNameTheirFinerArea() = runTest {
        val search = search()
        val places = listOf(place("Bergen", area = "Noord-Holland", region = "Noord-Holland"),
            place("Bergen", area = "Limburg-Noord")) + (1..8).map { place("Place $it") }
        search.edit("Bergen")

        search.search()
        runCurrent()
        access.searches.single().complete(PlaceResults.Found(places))
        runCurrent()

        val found = search.state.value as PlaceSearch.State.Found
        assertThat(found.text).isEqualTo("Bergen")
        assertThat(found.results).hasSize(8)
        assertThat(found.results.map { it.label }.take(3)).containsExactly(
            "Bergen, Noord-Holland, Netherlands", "Bergen, Utrecht, Netherlands", "Place 1, Utrecht, Netherlands")
    }

    @Test
    fun aTwinBeyondTheEighthPlaceDoesNotGiveOneShownTheFinerArea() = runTest {
        val search = search()
        val places = (1..7).map { place("Place $it") } + place("Bergen", area = "Noord-Holland") +
            place("Bergen", area = "Limburg-Noord")
        search.edit("Bergen")

        search.search()
        runCurrent()
        access.searches.single().complete(PlaceResults.Found(places))
        runCurrent()

        assertThat((search.state.value as PlaceSearch.State.Found).results.last().label)
            .isEqualTo("Bergen, Utrecht, Netherlands")
    }

    @Test
    fun placesThatReadAlikeOnlyByTheirAreaShowTheirArea() = runTest {
        val search = search()
        search.edit("Bergen")

        search.search()
        runCurrent()
        access.searches.single().complete(PlaceResults.Found(listOf(place("Bergen", area = "Bergen NH"),
            place("Bergen", area = "Bergen L"))))
        runCurrent()

        assertThat((search.state.value as PlaceSearch.State.Found).results.map { it.label }).containsExactly(
            "Bergen, Bergen NH, Utrecht, Netherlands", "Bergen, Bergen L, Utrecht, Netherlands")
    }

    @Test
    fun aFailedSearchSaysWhyAndAProviderWithoutOneSaysSo() = runTest {
        val search = search()
        search.edit("U")

        search.search()
        runCurrent()
        access.searches[0].complete(PlaceResults.Failed(PlaceResults.Problem.TOO_SHORT))
        runCurrent()
        assertThat(search.state.value).isEqualTo(PlaceSearch.State.Failed(PlaceResults.Problem.TOO_SHORT))

        search.search()
        runCurrent()
        access.searches[1].complete(null)
        runCurrent()
        assertThat(search.state.value).isEqualTo(PlaceSearch.State.NoSearch)
    }

    @Test
    fun aSearchThatFailsInTheEngineCouldNotBeReached() = runTest {
        val search = search()
        search.edit("Utrecht")

        search.search()
        runCurrent()
        access.searches.single().completeExceptionally(IllegalStateException("a fault"))
        runCurrent()

        assertThat(search.state.value).isEqualTo(PlaceSearch.State.Failed(PlaceResults.Problem.UNAVAILABLE))
    }

    @Test
    fun theTextAndThePlaceChosenSurviveTheSystemEndingTheAppButTheResultsDoNot() = runTest {
        val search = search()
        search.edit("Utrecht")
        search.search()
        runCurrent()
        access.searches.single().complete(PlaceResults.Found(listOf(place("Utrecht"))))
        runCurrent()
        val found = (search.state.value as PlaceSearch.State.Found).results.single()

        search.choose(found)

        assertThat(search.state.value).isEqualTo(PlaceSearch.State.Idle)
        val again = PlaceSearch(handle.afterTheSystemEndedTheApp(), access, backgroundScope, ProviderId("open-meteo"),
            language = { "nl" })
        assertThat(again.text()).isEqualTo("Utrecht, Netherlands")
        assertThat(again.chosen.value).isEqualTo("Utrecht, Netherlands")
        assertThat(again.state.value).isEqualTo(PlaceSearch.State.Idle)
    }

    @Test
    fun aSearchStillRunningWhenAPlaceIsChosenDoesNotShowItsResultsAfterwards() = runTest {
        val search = search()
        search.edit("Utrecht")
        search.search()
        runCurrent()

        search.choose(PlaceSearch.Result(place("Utrecht"), "Utrecht, Netherlands"))
        access.searches.single().complete(PlaceResults.Found(listOf(place("Utrecht"))))
        runCurrent()

        assertThat(search.state.value).isEqualTo(PlaceSearch.State.Idle)
    }

    @Test
    fun typedCoordinatesForgetThePlaceChosenAndEmptyTheFieldThatNamedIt() = runTest {
        val search = search()
        search.choose(PlaceSearch.Result(place("Utrecht"), "Utrecht, Netherlands"))

        search.forgetChosen()

        assertThat(search.chosen.value).isNull()
        assertThat(search.text()).isEmpty()
        val again = PlaceSearch(handle.afterTheSystemEndedTheApp(), access, backgroundScope, ProviderId("open-meteo"),
            language = { "nl" })
        assertThat(again.chosen.value).isNull()
        assertThat(again.text()).isEmpty()
    }

    @Test
    fun typedCoordinatesWithNoPlaceChosenLeaveTheTypedTextAlone() = runTest {
        val search = search()
        search.edit("Utrecht")

        search.forgetChosen()

        assertThat(search.text()).isEqualTo("Utrecht")
    }

    @Test
    fun eachChoiceCountsAndAnEditKeepsThePlaceChosen() = runTest {
        val search = search()
        assertThat(search.chosenCount.value).isZero()

        search.choose(PlaceSearch.Result(place("Utrecht"), "Utrecht, Netherlands"))
        search.edit("Amersfoort")
        assertThat(search.chosen.value).isEqualTo("Utrecht, Netherlands")
        search.choose(PlaceSearch.Result(place("Amersfoort"), "Amersfoort, Netherlands"))

        assertThat(search.chosenCount.value).isEqualTo(2)
        assertThat(search.chosen.value).isEqualTo("Amersfoort, Netherlands")
    }

    @Test
    fun twoSearchesOnOneHandleWithTheirOwnPrefixKeepTheirTextsApart() = runTest {
        val setUp = search()
        val settings = PlaceSearch(handle, access, backgroundScope, ProviderId("open-meteo"),
            "settings.place") { "nl" }

        setUp.edit("Utrecht")
        settings.edit("Lisbon")

        assertThat(setUp.text()).isEqualTo("Utrecht")
        assertThat(settings.text()).isEqualTo("Lisbon")
        val kept = handle.afterTheSystemEndedTheApp()
        assertThat(PlaceSearch(kept, access, backgroundScope, ProviderId("open-meteo"), language = { "nl" }).text())
            .isEqualTo("Utrecht")
        assertThat(PlaceSearch(kept, access, backgroundScope, ProviderId("open-meteo"), "settings.place") { "nl" }
            .text()).isEqualTo("Lisbon")
    }
}

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
        PlaceSearch(handle, access, backgroundScope, ProviderId("open-meteo"), "nl")

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
        assertThat(found.results).hasSize(PlaceSearch.MOST_RESULTS)
        assertThat(found.results.map { it.label }.take(3)).containsExactly(
            "Bergen, Noord-Holland, Netherlands", "Bergen, Utrecht, Netherlands", "Place 1, Utrecht, Netherlands")
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
            "nl")
        assertThat(again.text()).isEqualTo("Utrecht")
        assertThat(again.chosen.value).isEqualTo("Utrecht, Netherlands")
        assertThat(again.state.value).isEqualTo(PlaceSearch.State.Idle)
    }

    @Test
    fun typedCoordinatesForgetThePlaceChosen() = runTest {
        val search = search()
        search.choose(PlaceSearch.Result(place("Utrecht"), "Utrecht, Netherlands"))

        search.forgetChosen()

        assertThat(search.chosen.value).isNull()
        assertThat(PlaceSearch(handle.afterTheSystemEndedTheApp(), access, backgroundScope, ProviderId("open-meteo"),
            "nl").chosen.value).isNull()
    }
}

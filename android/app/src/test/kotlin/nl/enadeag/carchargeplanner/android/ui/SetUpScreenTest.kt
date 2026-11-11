package nl.enadeag.carchargeplanner.android.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import nl.enadeag.carchargeplanner.android.R
import nl.enadeag.carchargeplanner.android.afterTheSystemEndedTheApp
import nl.enadeag.carchargeplanner.android.form.CarForm
import nl.enadeag.carchargeplanner.android.form.LocationForm
import nl.enadeag.carchargeplanner.android.form.NumberText
import nl.enadeag.carchargeplanner.android.form.WeeklyAwayForm
import nl.enadeag.carchargeplanner.android.form.fillIn
import nl.enadeag.carchargeplanner.android.setup.PlaceSearch
import nl.enadeag.carchargeplanner.android.setup.SetUpSession
import nl.enadeag.carchargeplanner.android.setup.SetUpStep
import nl.enadeag.carchargeplanner.android.store.FakeEngineAccess
import nl.enadeag.carchargeplanner.android.store.SaveOutcome
import nl.enadeag.carchargeplanner.android.store.Saves
import nl.enadeag.carchargeplanner.android.store.StartState
import nl.enadeag.carchargeplanner.android.ui.setup.SetUpScreen
import nl.enadeag.carchargeplanner.engine.config.Coordinates
import nl.enadeag.carchargeplanner.engine.forecast.ConnectionResult
import nl.enadeag.carchargeplanner.engine.forecast.Place
import nl.enadeag.carchargeplanner.engine.forecast.PlaceResults
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Set-up's screen on Robolectric, on its logic with an engine in memory. */
@RunWith(AndroidJUnit4::class)
class SetUpScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val access = FakeEngineAccess(StartState.NotSetUp)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val saves = Saves(access, scope)
    private val ended = mutableListOf<SaveOutcome>()
    private val handle = SavedStateHandle()
    private val amsterdam = ZoneId.of("Europe/Amsterdam")
    private val utrecht = Place("Utrecht", Coordinates(52.0907, 5.1214), amsterdam, "Utrecht", "Netherlands", "")
    private val amersfoort = Place("Amersfoort", Coordinates(52.1561, 5.3878), amsterdam, "Utrecht", "Netherlands", "")
    private val longName = Place("Sint-Michielsgestel", Coordinates(51.6417, 5.3528), amsterdam, "North Brabant",
        "Netherlands", "")
    private var colours = lightColorScheme()

    private fun session(on: SavedStateHandle = handle) =
        SetUpSession(on, NumberText(Locale.ENGLISH), { amsterdam }, { "en" }, access, saves, scope) { ended += it }

    @After
    fun stop() {
        scope.cancel()
    }

    private fun show(session: SetUpSession = session(), fontScaleTwo: Boolean = false): SetUpSession {
        compose.setContent {
            AppTheme {
                colours = MaterialTheme.colorScheme
                if (fontScaleTwo) AtFontScaleTwo { SetUpScreen(session) } else SetUpScreen(session)
            }
        }
        return session
    }

    private fun heading(step: Int, title: Int) =
        compose.onNode(isHeading() and hasText(text(R.string.setup_step_of, step, 7), substring = true) and
            hasText(text(title), substring = true))

    private fun click(id: Int) = compose.onNodeWithText(text(id)).performScrollTo().performClick()

    private fun field(label: Int) = compose.onNode(hasSetTextAction() and hasText(text(label), substring = true))

    private fun chosenCard(place: String = "Utrecht, Netherlands") =
        compose.onNode(hasText(text(R.string.place_chosen_label, place)))

    /** Opens a drop-down by its label and chooses an option in its menu, as the owner does. */
    private fun choose(label: Int, option: Int) {
        compose.onNode(hasText(text(label), substring = true)
            and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList)).performScrollTo()
            .performClick()
        compose.waitForIdle()
        compose.onNodeWithText(text(option)).performClick()
        compose.waitForIdle()
    }

    /** Fills the panels step as the owner does: 20 panels facing south, pitched, placed in 2021. */
    private fun fillPanels() {
        field(R.string.panel_count).performScrollTo().performTextInput("20")
        choose(R.string.panel_direction, R.string.direction_south)
        choose(R.string.panel_tilt, R.string.tilt_pitched)
        field(R.string.panel_year).performScrollTo().performTextInput("2021")
        compose.waitForIdle()
    }

    /** Searches for Utrecht and chooses it, on the location step. */
    private fun chooseUtrecht() {
        field(R.string.place_field).performTextInput("Utrecht")
        click(R.string.place_search)
        compose.waitForIdle()
        access.searches.last().complete(PlaceResults.Found(listOf(utrecht)))
        compose.onNodeWithText("Utrecht, Netherlands").performScrollTo().performClick()
        compose.waitForIdle()
    }

    @Test
    fun theWelcomeSaysThatTheAppAlsoAsksTheProviderAboutOnceAnHourWhileItIsClosed() {
        show()

        compose.onNodeWithText(text(R.string.setup_welcome_background, "Open-Meteo")).performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "nl")
    fun theWelcomeSaysInDutchThatTheAppAlsoAsksTheProviderAboutOnceAnHourWhileItIsClosed() {
        show()

        compose.onNodeWithText("Ook als de app dicht is, vraagt de app ongeveer elk uur aan Open-Meteo om een " +
            "voorspelling, zodat je een melding krijgt voordat een laadvenster begint.").performScrollTo()
            .assertIsDisplayed()
    }

    /** Goes through every step to the check, each one checked as it comes in the language the test runs in. */
    private fun walkEveryStep() {
        val englishOnly = englishOnlyTexts()
        val titles = listOf(R.string.setup_welcome_title, R.string.setup_location_title, R.string.setup_panels_title,
            R.string.setup_car_title, R.string.setup_charger_title, R.string.setup_more_title,
            R.string.setup_check_title)
        titles.forEachIndexed { index, title ->
            heading(index + 1, title).assertExists()
            if (index == 5) {
                click(R.string.away_add)
            }
            compose.assertShowsNoneOf(englishOnly)
            if (index == 1) {
                chooseUtrecht()
            }
            if (index == 2) {
                fillPanels()
                compose.assertShowsNoneOf(englishOnly)
            }
            if (index < titles.size - 1) {
                click(R.string.action_next)
                compose.waitForIdle()
            }
        }
    }

    @Test
    fun everyStepIsInEnglish() {
        show()
        walkEveryStep()
    }

    @Test
    @Config(qualifiers = "nl")
    fun everyStepIsInDutch() {
        show()
        walkEveryStep()
    }

    @Test
    fun everyStepScrollsAndItsControlsAreNamedAndLargeEnoughAtTwiceTheFontSize() {
        val session = show(fontScaleTwo = true)
        for (step in SetUpStep.entries) {
            if (step == SetUpStep.MORE) {
                click(R.string.away_add)
            }
            if (step == SetUpStep.LOCATION) {
                compose.onNodeWithText(text(R.string.coordinates_toggle)).performScrollTo().performClick()
                compose.onNodeWithText(text(R.string.advanced)).performScrollTo().performClick()
            }
            compose.waitForIdle()
            compose.assertScrolls()
            compose.assertEveryControlIsNamedAndLargeEnough()
            if (step == SetUpStep.LOCATION) {
                chooseUtrecht()
            }
            if (step == SetUpStep.PANELS) {
                fillPanels()
            }
            if (step != SetUpStep.CHECK) {
                click(R.string.action_next)
            }
        }
        assertThat(session.flow.step.value).isEqualTo(SetUpStep.CHECK)
    }

    @Test
    fun thePanelsStepStartsEmptyAndShowsWhatTheEnteredPanelsGiveTogether() {
        val session = show()
        session.next()
        session.choose(PlaceSearch.Result(utrecht, "Utrecht, Netherlands"))
        session.next()
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.use_typical)).assertDoesNotExist()

        click(R.string.action_next)
        compose.waitForIdle()
        assertThat(session.flow.step.value).isEqualTo(SetUpStep.PANELS)
        field(R.string.panel_count).assertIsFocused()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, text(R.string.problem_panel_count)))

        fillPanels()

        compose.onNodeWithText(text(R.string.panel_together, "7.2")).performScrollTo().assertIsDisplayed()
        field(R.string.panel_power).assert(hasText(text(R.string.panel_power_hint_typical, 360, "2021"),
            substring = true))
        compose.onNodeWithText(text(R.string.panel_losses), substring = true).assertDoesNotExist()
        click(R.string.more_settings)
        compose.waitForIdle()
        field(R.string.panel_losses).performScrollTo().assertIsDisplayed()
        click(R.string.action_next)
        compose.waitForIdle()
        assertThat(session.flow.step.value).isEqualTo(SetUpStep.CAR)
    }

    @Test
    fun aNewStepMovesTheFocusToItsHeading() {
        show()

        click(R.string.action_next)
        compose.waitForIdle()

        heading(2, R.string.setup_location_title).assertIsFocused()
    }

    @Test
    fun nextWithoutAPlaceShowsTheProblemAtTheSearchAndMovesTheFocusThere() {
        show()
        click(R.string.action_next)

        click(R.string.action_next)
        compose.waitForIdle()

        val problem = text(R.string.problem_no_place)
        field(R.string.place_field).assertIsFocused()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, problem))
        heading(2, R.string.setup_location_title).assertExists()
    }

    @Test
    fun aProblemGoesWhenTheFieldIsEdited() {
        val session = show()
        session.next()
        session.choose(PlaceSearch.Result(utrecht, "Utrecht, Netherlands"))
        session.flow.panels.fillIn()
        repeat(2) { session.next() }
        compose.waitForIdle()
        field(R.string.car_battery).performTextReplacement("sixty")
        click(R.string.action_next)
        compose.waitForIdle()
        field(R.string.car_battery).assertIsFocused()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, text(R.string.problem_not_a_number)))

        field(R.string.car_battery).performTextReplacement("60")

        field(R.string.car_battery).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Error))
    }

    @Test
    fun theKeyboardsSearchKeySearchesInTheAppsLanguageAndEachProblemHasItsText() {
        val session = show()
        session.next()
        compose.waitForIdle()

        field(R.string.place_field).performTextInput("U")
        field(R.string.place_field).performImeAction()
        compose.waitForIdle()
        access.searches.last().complete(PlaceResults.Failed(PlaceResults.Problem.TOO_SHORT))
        compose.onNodeWithText(text(R.string.place_too_short)).assertExists()

        click(R.string.place_search)
        compose.waitForIdle()
        access.searches.last().complete(PlaceResults.Failed(PlaceResults.Problem.UNAVAILABLE))
        compose.onNodeWithText(text(R.string.place_unavailable)).assertExists()
        assertThat(access.searched).containsExactly("U" to "en", "U" to "en")
    }

    @Test
    fun typicalValuesAreMarkedWithAWordAndASentenceForTheScreenReader() {
        val session = show()
        session.next()
        session.choose(PlaceSearch.Result(utrecht, "Utrecht, Netherlands"))
        session.flow.panels.fillIn()
        repeat(2) { session.next() }
        compose.waitForIdle()

        val marks = compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription,
            listOf(text(R.string.typical_mark_description)))).fetchSemanticsNodes()
        assertThat(marks).hasSize(3)
        field(R.string.car_battery).performTextReplacement("75")
        assertThat(compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription,
            listOf(text(R.string.typical_mark_description)))).fetchSemanticsNodes()).hasSize(2)
    }

    @Test
    fun theCheckSaysHowManyDaysCameAlsoForOneAndNone() {
        val session = show()
        toTheCheck(session)
        access.checks.last().complete(ConnectionResult(true, 1, "Received a forecast for 1 day."))
        compose.onNodeWithText(compose.activity.resources.getQuantityString(R.plurals.check_works, 1, "Open-Meteo", 1))
            .assertIsDisplayed()

        click(R.string.action_back)
        click(R.string.action_next)
        compose.waitForIdle()
        access.checks.last().complete(ConnectionResult(true, 0, "Received a forecast for 0 days."))

        compose.onNodeWithText(compose.activity.resources.getQuantityString(R.plurals.check_works, 0, "Open-Meteo", 0))
            .assertIsDisplayed()
    }

    @Test
    fun aFailedCheckShowsItsEnglishDetailsAndDoesNotBlockFinish() {
        val session = show()
        toTheCheck(session)

        access.checks.last().complete(ConnectionResult(false, 0, "Open-Meteo did not answer."))
        compose.waitForIdle()

        assertEnglish("Open-Meteo did not answer.")
        compose.onNodeWithText(text(R.string.check_again)).assertIsDisplayed()
        click(R.string.action_finish)
        compose.waitForIdle()
        assertThat(ended).containsExactly(SaveOutcome.Saved)
    }

    @Test
    fun aFailedFinishShowsItsEnglishDetailsInALiveRegionAndMayBeTriedAgain() {
        val session = show()
        toTheCheck(session)
        access.writeFails = true

        click(R.string.action_finish)
        compose.waitForIdle()

        assertEnglish("No space left on device")
        assertThat(compose.liveRegionOf(text(R.string.finish_failed))).isEqualTo(LiveRegionMode.Assertive)
        access.writeFails = false
        click(R.string.action_try_again)
        compose.waitForIdle()
        assertThat(ended).containsExactly(SaveOutcome.Saved)
    }

    @Test
    fun finishWaitsWhileItSaves() {
        val session = show()
        toTheCheck(session)
        val gate = CompletableDeferred<Unit>()
        access.readGate = gate

        click(R.string.action_finish)
        compose.waitForIdle()

        compose.onNodeWithText(text(R.string.finish_saving), substring = true).assertIsNotEnabled()
        compose.activity.onBackPressedDispatcher.onBackPressed()
        assertThat(session.flow.step.value).isEqualTo(SetUpStep.CHECK)
        gate.complete(Unit)
        compose.waitForIdle()
        assertThat(ended).containsExactly(SaveOutcome.Saved)
    }

    @Test
    fun backOnALaterStepGoesOneStepBackAndOnTheWelcomeLeaves() {
        val session = show()
        click(R.string.action_next)
        compose.waitForIdle()

        compose.activity.onBackPressedDispatcher.onBackPressed()
        compose.waitForIdle()
        assertThat(session.flow.step.value).isEqualTo(SetUpStep.WELCOME)
        assertThat(compose.activity.isFinishing).isFalse()

        compose.activity.onBackPressedDispatcher.onBackPressed()
        assertThat(compose.activity.isFinishing).isTrue()
    }

    @Test
    fun theTimeZoneIsChosenFromTheListOfZoneIds() {
        val session = show()
        session.next()
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.advanced)).performScrollTo().performClick()
        click(R.string.time_zone_change)

        compose.onNode(hasSetTextAction() and hasText(text(R.string.time_zone_filter))).performTextInput("tokyo")
        compose.onNodeWithText("Asia/Tokyo").performClick()
        compose.waitForIdle()

        assertThat(session.flow.location.timeZone()).isEqualTo(ZoneId.of("Asia/Tokyo"))
        compose.onNodeWithText(text(R.string.time_zone_now, "Asia/Tokyo")).assertIsDisplayed()
    }

    @Test
    fun theStepTheTextsAndWhatIsOpenSurviveTurningThePhone() {
        val restoration = StateRestorationTester(compose)
        val session = session()
        restoration.setContent { AppTheme { SetUpScreen(session) } }
        session.next()
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.advanced)).performScrollTo().performClick()
        field(R.string.place_field).performTextInput("Utrecht")

        restoration.emulateSavedInstanceStateRestore()

        heading(2, R.string.setup_location_title).assertExists()
        compose.onNodeWithText(text(R.string.advanced))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text(R.string.fold_expanded)))
        field(R.string.place_field).assert(hasText("Utrecht"))
    }

    @Test
    fun theStepAndTheTextsSurviveTheSystemEndingTheAppAndTypedCoordinatesOpenByThemselves() {
        val restoration = StateRestorationTester(compose)
        var session by mutableStateOf(session())
        restoration.setContent { AppTheme { SetUpScreen(session) } }
        session.next()
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.coordinates_toggle)).performScrollTo().performClick()
        field(R.string.latitude).performTextReplacement("52.1")
        field(R.string.longitude).performTextReplacement("5.1")

        session = session(handle.afterTheSystemEndedTheApp())
        restoration.emulateSavedInstanceStateRestore()

        heading(2, R.string.setup_location_title).assertExists()
        compose.onNodeWithText(text(R.string.coordinates_toggle)).assertIsOn()
        field(R.string.latitude).assert(hasText("52.1"))
        compose.onNodeWithText(text(R.string.action_next)).assertIsEnabled()
    }

    @Test
    fun aDraftWithTypedCoordinatesOpensThemByItself() {
        val first = session()
        first.next()
        first.typeCoordinate(LocationForm.Field.LATITUDE, "52.1")

        show(session(handle.afterTheSystemEndedTheApp()))

        compose.onNodeWithText(text(R.string.coordinates_toggle)).assertIsOn()
        field(R.string.latitude).assertIsDisplayed()
    }

    @Test
    fun eachAwayPeriodNamesItsControlsAndAProblemOfTheSecondTakesTheFocusByItsName() {
        val session = show()
        toTheMoreStep(session)
        click(R.string.away_add)
        click(R.string.away_add)

        awayField(R.string.away_until, 2).performTextReplacement("07:00")
        click(R.string.action_next)
        compose.waitForIdle()

        compose.onNode(isHeading() and hasText(text(R.string.away_period, 2))).assertExists()
        compose.onNodeWithText(text(R.string.away_weekday, 2)).assertExists()
        compose.onNodeWithContentDescription(text(R.string.away_weekday, 2)).assertExists()
        awayField(R.string.away_until, 2).assertIsFocused()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, text(R.string.problem_not_after_start)))
        awayField(R.string.away_until, 1).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Error))
    }

    @Test
    fun withTwoProblemsTheFocusGoesToTheUpperOne() {
        val session = show()
        toTheMoreStep(session)
        click(R.string.away_add)
        click(R.string.away_add)
        awayField(R.string.away_until, 2).performTextReplacement("07:00")
        awayField(R.string.away_from, 1).performTextReplacement("eight")

        click(R.string.action_next)
        compose.waitForIdle()

        awayField(R.string.away_from, 1).assertIsFocused()
        awayField(R.string.away_until, 2).assertIsNotFocused()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
    }

    @Test
    fun aValueRefusedAtFinishShowsWhyInALiveRegionAndMovesTheFocusToItsField() {
        val session = show()
        toTheCheck(session)
        session.flow.car.edit(CarForm.Field.CONSUMPTION, "0")

        click(R.string.action_finish)
        compose.waitForIdle()

        heading(4, R.string.setup_car_title).assertExists()
        assertInLiveRegion(text(R.string.setup_refused), LiveRegionMode.Polite)
        field(R.string.car_consumption).assertIsFocused()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
    }

    @Test
    fun eachPlaceSearchProblemHasItsTextInALiveRegion() {
        val session = show()
        session.next()
        compose.waitForIdle()
        field(R.string.place_field).performTextInput("Atlantis")
        val answers = listOf(
            PlaceResults.Failed(PlaceResults.Problem.RATE_LIMITED) to text(R.string.place_busy),
            PlaceResults.Failed(PlaceResults.Problem.BAD_RESPONSE) to text(R.string.place_bad_response),
            null to text(R.string.place_no_search),
            PlaceResults.Found(listOf()) to text(R.string.place_none, "Atlantis"),
        )

        for ((answer, shown) in answers) {
            click(R.string.place_search)
            compose.waitForIdle()
            access.searches.last().complete(answer)
            compose.waitForIdle()
            assertInLiveRegion(shown, LiveRegionMode.Polite)
        }
    }

    @Test
    fun thePlacesFoundAreInALiveRegion() {
        val session = show()
        session.next()
        compose.waitForIdle()
        field(R.string.place_field).performTextInput("Utrecht")
        click(R.string.place_search)
        compose.waitForIdle()

        access.searches.last().complete(PlaceResults.Found(listOf(utrecht)))
        compose.waitForIdle()

        assertInLiveRegion(text(R.string.place_choose), LiveRegionMode.Polite)
        assertInLiveRegion("Utrecht, Netherlands", LiveRegionMode.Polite)
    }

    @Test
    fun aChosenPlaceShowsOnACardReadAsOneWithItsCoordinatesAndTimeZoneAndInTheField() {
        show()
        click(R.string.action_next)
        click(R.string.action_next)
        compose.waitForIdle()

        chooseUtrecht()

        val card = chosenCard().assertIsDisplayed().fetchSemanticsNode()
        assertThat(card.config[SemanticsProperties.Text].map { it.text }).containsExactly(
            text(R.string.place_chosen_label, "Utrecht, Netherlands"),
            text(R.string.place_chosen_coordinates, "52.0907", "5.1214"), text(R.string.time_zone_now, amsterdam.id))
        assertThat(card.config.getOrNull(SemanticsProperties.ContentDescription)).isNull()
        field(R.string.place_field).assert(hasText("Utrecht, Netherlands"))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Error))
        compose.onNodeWithText(text(R.string.coordinates_toggle)).assertIsOff()
    }

    @Test
    fun editingTheFieldOrSearchingAgainKeepsTheCardUnderTheSearchButtonUntilAnotherPlaceIsChosen() {
        val session = show()
        session.next()
        compose.waitForIdle()
        chooseUtrecht()

        field(R.string.place_field).performTextReplacement("Amersfoort")
        click(R.string.place_search)
        compose.waitForIdle()
        access.searches.last().complete(PlaceResults.Found(listOf(amersfoort)))
        compose.waitForIdle()

        val card = chosenCard().assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val result = compose.onNodeWithText("Amersfoort, Utrecht, Netherlands").fetchSemanticsNode().boundsInRoot
        val search = compose.onNodeWithText(text(R.string.place_search)).fetchSemanticsNode().boundsInRoot
        assertThat(card.top).`as`("the card's top").isGreaterThanOrEqualTo(search.bottom)
        assertThat(result.top).`as`("the first result's top").isGreaterThanOrEqualTo(card.bottom)
        compose.onNodeWithText("Amersfoort, Utrecht, Netherlands").performScrollTo().performClick()
        compose.waitForIdle()
        chosenCard().assertDoesNotExist()
        chosenCard("Amersfoort, Utrecht, Netherlands")
            .assert(hasText(text(R.string.place_chosen_coordinates, "52.1561", "5.3878")))
    }

    @Test
    fun aTypedCoordinateTakesTheCardAwayAndEmptiesTheField() {
        val session = show()
        session.next()
        compose.waitForIdle()
        chooseUtrecht()

        click(R.string.coordinates_toggle)
        field(R.string.latitude).performTextReplacement("52.1")

        chosenCard().assertDoesNotExist()
        field(R.string.place_field).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText,
            AnnotatedString("")))
        compose.onNodeWithText(text(R.string.place_chosen_label, ""), substring = true).assertDoesNotExist()
    }

    @Test
    fun aChoiceMovesTheFocusToTheCardButNotAgainAfterTurningThePhone() {
        val restoration = StateRestorationTester(compose)
        val session = session()
        restoration.setContent { AppTheme { SetUpScreen(session) } }
        session.next()
        compose.waitForIdle()

        chooseUtrecht()

        chosenCard().assertIsFocused()
        field(R.string.place_field).performClick()
        restoration.emulateSavedInstanceStateRestore()
        chosenCard().assertIsDisplayed().assertIsNotFocused()
    }

    @Test
    fun comingBackToTheStepMovesTheFocusToItsHeadingNotToTheCard() {
        val session = show()
        session.next()
        compose.waitForIdle()
        chooseUtrecht()

        click(R.string.action_next)
        compose.waitForIdle()
        click(R.string.action_back)
        compose.waitForIdle()

        heading(2, R.string.setup_location_title).assertIsFocused()
        chosenCard().assertIsNotFocused()
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp")
    fun thePlacesFoundAreRowsThatReadAsChoicesAndWrapAtTwiceTheFontSizeOnANarrowPhone() {
        val session = show(fontScaleTwo = true)
        session.next()
        compose.waitForIdle()
        field(R.string.place_field).performTextInput("Sint")
        click(R.string.place_search)
        compose.waitForIdle()

        access.searches.last().complete(PlaceResults.Found(listOf(longName, utrecht)))
        compose.waitForIdle()

        val label = "Sint-Michielsgestel, North Brabant, Netherlands"
        val row = compose.onNode(hasClickAction() and hasText(label)).performScrollTo().assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).fetchSemanticsNode()
        val words = compose.onNode(hasText(label), useUnmergedTree = true).fetchSemanticsNode()
        val layout = mutableListOf<TextLayoutResult>()
        words.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layout)
        assertThat(layout.single().lineCount).isGreaterThan(1)
        assertThat(layout.single().hasVisualOverflow).isFalse()
        assertThat(layout.single().layoutInput.style.color).isEqualTo(colours.onSurface)
        assertThat(row.size.height / row.layoutInfo.density.density).isGreaterThanOrEqualTo(48f)
        assertThat(row.boundsInRoot.bottom).isGreaterThanOrEqualTo(words.boundsInRoot.bottom)
        compose.assertEveryControlIsNamedAndLargeEnough()
    }

    @Test
    fun aDividerSetsThePlacesFoundApartButNotTheFirstFromItsHeading() {
        val session = show()
        session.next()
        compose.waitForIdle()
        field(R.string.place_field).performTextInput("Utrecht")
        click(R.string.place_search)
        compose.waitForIdle()

        access.searches.last().complete(PlaceResults.Found(listOf(utrecht, amersfoort, longName)))
        compose.waitForIdle()

        val heading = compose.onNodeWithText(text(R.string.place_choose), useUnmergedTree = true).fetchSemanticsNode()
            .boundsInRoot
        val rows = listOf("Utrecht, Netherlands", "Amersfoort, Utrecht, Netherlands",
            "Sint-Michielsgestel, North Brabant, Netherlands")
            .map { compose.onNode(hasClickAction() and hasText(it)).fetchSemanticsNode() }
        val density = rows.first().layoutInfo.density.density
        val gaps = rows.zipWithNext { above, below -> below.boundsInRoot.top - above.boundsInRoot.bottom }
        assertThat(gaps).`as`("the gaps between the rows").allSatisfy { assertThat(it).isGreaterThan(0f) }
        // the column's own spacing of 4 dp, with no divider in it
        assertThat(rows.first().boundsInRoot.top - heading.bottom).`as`("the gap under the heading")
            .isLessThan(4 * density + gaps.first())
    }

    @Test
    fun choosingTheSamePlaceAgainMovesTheFocusToTheCardAgain() {
        val session = show()
        session.next()
        compose.waitForIdle()
        chooseUtrecht()
        field(R.string.place_field).performClick()
        chosenCard().assertIsNotFocused()

        chooseUtrecht()

        chosenCard().assertIsFocused()
    }

    /** Searches for Sint-Michielsgestel, whose name wraps at twice the font size, and chooses it. */
    private fun chooseALongName(): String {
        field(R.string.place_field).performTextInput("Sint")
        click(R.string.place_search)
        compose.waitForIdle()
        access.searches.last().complete(PlaceResults.Found(listOf(longName)))
        val label = "Sint-Michielsgestel, North Brabant, Netherlands"
        compose.onNodeWithText(label).performScrollTo().performClick()
        compose.waitForIdle()
        return label
    }

    /** Checks that the chosen card's first line wraps in full and that the card can be scrolled into view. */
    private fun assertTheCardWrapsAndIsInView(label: String) {
        chosenCard(label).performScrollTo().assertIsDisplayed()
        val words = compose.onNode(hasText(text(R.string.place_chosen_label, label)), useUnmergedTree = true)
            .fetchSemanticsNode()
        val layout = mutableListOf<TextLayoutResult>()
        words.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layout)
        assertThat(layout.single().lineCount).isGreaterThan(1)
        assertThat(layout.single().hasVisualOverflow).isFalse()
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp")
    fun theCardOfALongPlaceWrapsAtTwiceTheFontSizeOnANarrowPhone() {
        val session = show(fontScaleTwo = true)
        session.next()
        compose.waitForIdle()

        val label = chooseALongName()

        assertTheCardWrapsAndIsInView(label)
    }

    @Test
    @Config(qualifiers = "w640dp-h320dp")
    fun theCardOfALongPlaceWrapsAtTwiceTheFontSizeOnAPhoneOnItsSide() {
        val session = show(fontScaleTwo = true)
        session.next()
        compose.waitForIdle()

        val label = chooseALongName()

        assertTheCardWrapsAndIsInView(label)
    }

    @Test
    fun theCheckSaysWhatCameInALiveRegion() {
        val session = show()
        toTheCheck(session)

        access.checks.last().complete(ConnectionResult(true, 7, "Received a forecast for 7 days."))
        compose.waitForIdle()

        assertInLiveRegion(compose.activity.resources.getQuantityString(R.plurals.check_works, 7, "Open-Meteo", 7),
            LiveRegionMode.Polite)
    }

    @Test
    fun theSignKeyPutsAMinusBeforeTheNumberAndTakesItAway() {
        val session = show()
        session.next()
        compose.waitForIdle()
        click(R.string.coordinates_toggle)
        field(R.string.latitude).performTextReplacement("52.1")
        val sign = compose.onAllNodesWithContentDescription(text(R.string.sign_toggle))[0]

        sign.performScrollTo().performClick()
        field(R.string.latitude).assert(hasText("-52.1"))
        assertThat(session.flow.location.text(LocationForm.Field.LATITUDE)).isEqualTo("-52.1")

        sign.performClick()
        field(R.string.latitude).assert(hasText("52.1"))
    }

    @Test
    fun aChoiceIsAGroupOfRadioButtonsThatShowWhichIsChosen() {
        val session = show()
        toTheMoreStep(session)
        click(R.string.away_add)
        val monday = compose.onNodeWithText(DayOfWeek.MONDAY.getDisplayName(TextStyle.FULL, Locale.ENGLISH))
        val friday = compose.onNodeWithText(DayOfWeek.FRIDAY.getDisplayName(TextStyle.FULL, Locale.ENGLISH))
        val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        monday.assert(radio).assertIsSelected()
        friday.assert(radio).assertIsNotSelected()

        friday.performScrollTo().performClick()

        friday.assertIsSelected()
        monday.assertIsNotSelected()
        val row = session.flow.away.rowIds.value.single()
        assertThat(session.flow.away.text(row, WeeklyAwayForm.Field.WEEKDAY)).isEqualTo("FRIDAY")
    }

    @Test
    fun theTimeZoneDialogsControlsAreNamedAndLargeEnoughAtTwiceTheFontSize() {
        val session = show(fontScaleTwo = true)
        session.next()
        compose.waitForIdle()
        click(R.string.advanced)
        click(R.string.time_zone_change)
        compose.waitForIdle()

        compose.onNode(isDialog()).assertExists()
        compose.assertEveryControlIsNamedAndLargeEnough()
        compose.onNodeWithText(text(R.string.action_cancel)).assertIsDisplayed()
    }

    @Test
    fun aStepsControlsAreNamedAndLargeEnoughAtTheNormalFontSize() {
        val session = show()
        toTheMoreStep(session)
        click(R.string.away_add)
        compose.waitForIdle()

        compose.assertEveryControlIsNamedAndLargeEnough()
    }

    @Test
    fun onEveryStepOnlyTheLastFieldHasTheKeyboardsDone() {
        val session = show()
        session.next()
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.coordinates_toggle)).performScrollTo().performClick()
        compose.waitForIdle()
        compose.assertOnlyTheLastFieldIsDone()
        session.choose(PlaceSearch.Result(utrecht, "Utrecht, Netherlands"))
        session.flow.panels.fillIn()
        repeat(4) {
            session.next()
            compose.waitForIdle()
            compose.assertOnlyTheLastFieldIsDone()
        }
        assertThat(session.flow.step.value).isEqualTo(SetUpStep.MORE)

        repeat(2) { click(R.string.away_add) }
        compose.waitForIdle()

        compose.assertOnlyTheLastFieldIsDone()
    }

    /** Goes from the welcome to the step with the house and the away periods, Utrecht chosen. */
    private fun toTheMoreStep(session: SetUpSession) {
        session.next()
        session.choose(PlaceSearch.Result(utrecht, "Utrecht, Netherlands"))
        session.flow.panels.fillIn()
        repeat(4) { session.next() }
        compose.waitForIdle()
    }

    private fun awayField(label: Int, period: Int) =
        compose.onNode(hasSetTextAction() and hasText(text(label, period), substring = true))

    /** The text is on screen inside a live region of that mode, which a screen reader reads when it changes. */
    private fun assertInLiveRegion(shown: String, mode: LiveRegionMode) {
        val live = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, mode)
        compose.onNode(hasText(shown) and (live or hasAnyAncestor(live)), useUnmergedTree = true).assertExists()
    }

    private fun toTheCheck(session: SetUpSession) {
        session.next()
        session.choose(PlaceSearch.Result(utrecht, "Utrecht, Netherlands"))
        session.flow.panels.fillIn()
        repeat(5) { session.next() }
        compose.waitForIdle()
    }

    /** The details are on screen in the sentence that says they are English, and marked as English. */
    private fun assertEnglish(details: String) {
        val sentence = text(R.string.details_in_english, details)
        val node = compose.onNodeWithText(sentence, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
            .fetchSemanticsNode()
        val shown = node.config[SemanticsProperties.Text].single()
        assertThat(shown.spanStyles).anySatisfy { span ->
            assertThat(shown.text.substring(span.start, span.end)).isEqualTo(details)
            assertThat(span.item.localeList?.first()?.language).isEqualTo("en")
        }
    }
}

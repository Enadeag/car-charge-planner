package nl.enadeag.carchargeplanner.android.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
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
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
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

    private fun session(on: SavedStateHandle = handle) =
        SetUpSession(on, NumberText(Locale.ENGLISH), { amsterdam }, { "en" }, access, saves, scope) { ended += it }

    @After
    fun stop() {
        scope.cancel()
    }

    private fun show(session: SetUpSession = session(), fontScaleTwo: Boolean = false): SetUpSession {
        compose.setContent {
            AppTheme {
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

    /** Searches for Utrecht and chooses it, on the location step. */
    private fun chooseUtrecht() {
        field(R.string.place_field).performTextInput("Utrecht")
        click(R.string.place_search)
        compose.waitForIdle()
        access.searches.last().complete(PlaceResults.Found(listOf(utrecht)))
        compose.onNodeWithText("Utrecht, Netherlands").performScrollTo().performClick()
        compose.waitForIdle()
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
            if (step != SetUpStep.CHECK) {
                click(R.string.action_next)
            }
        }
        assertThat(session.flow.step.value).isEqualTo(SetUpStep.CHECK)
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
        assertThat(compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
            .fetchSemanticsNodes().flatMap { node -> node.children.flatMap { child ->
                child.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } } })
            .contains(text(R.string.finish_failed))
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
        compose.onNodeWithText(text(R.string.advanced)).assertIsOn()
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

    /** Goes from the welcome to the step with the house and the away periods, Utrecht chosen. */
    private fun toTheMoreStep(session: SetUpSession) {
        session.next()
        session.choose(PlaceSearch.Result(utrecht, "Utrecht, Netherlands"))
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
        repeat(5) { session.next() }
        compose.waitForIdle()
    }

    /** The details are on screen in the sentence that says they are English, and marked as English. */
    private fun assertEnglish(details: String) {
        val sentence = text(R.string.details_in_english, details)
        val node = compose.onNodeWithText(sentence).performScrollTo().assertIsDisplayed().fetchSemanticsNode()
        val shown = node.config[SemanticsProperties.Text].single()
        assertThat(shown.spanStyles).anySatisfy { span ->
            assertThat(shown.text.substring(span.start, span.end)).isEqualTo(details)
            assertThat(span.item.localeList?.first()?.language).isEqualTo("en")
        }
    }
}

package nl.enadeag.carchargeplanner.android.ui

import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.os.SystemClock
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextLayoutResult
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.ZoneId
import java.util.Locale
import java.util.Optional
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import nl.enadeag.carchargeplanner.android.R
import nl.enadeag.carchargeplanner.android.afterTheSystemEndedTheApp
import nl.enadeag.carchargeplanner.android.form.CarForm
import nl.enadeag.carchargeplanner.android.form.NumberText
import nl.enadeag.carchargeplanner.android.settings.SettingsPage
import nl.enadeag.carchargeplanner.android.settings.SettingsSession
import nl.enadeag.carchargeplanner.android.store.FakeEngineAccess
import nl.enadeag.carchargeplanner.android.store.Saves
import nl.enadeag.carchargeplanner.android.store.StartState
import nl.enadeag.carchargeplanner.android.ui.settings.SettingsScreen
import nl.enadeag.carchargeplanner.android.ui.settings.title
import nl.enadeag.carchargeplanner.engine.config.Coordinates
import nl.enadeag.carchargeplanner.engine.config.EngineConfig
import nl.enadeag.carchargeplanner.engine.config.ForecastSelection
import nl.enadeag.carchargeplanner.engine.config.ProviderId
import nl.enadeag.carchargeplanner.engine.forecast.ConnectionResult
import nl.enadeag.carchargeplanner.engine.secrets.Secrets
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/** Settings' screen on Robolectric, on its logic with an engine in memory. */
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val amsterdam = ZoneId.of("Europe/Amsterdam")
    private val openMeteo = ProviderId("open-meteo")
    private val draft = EngineConfig.setUpDraft(amsterdam, openMeteo)
    private val stored = draft.withSystem(draft.system().withLocation(Coordinates(52.0907, 5.1214), amsterdam),
        draft.typical())
    private val access = FakeEngineAccess(StartState.Ready(stored))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val saves = Saves(access, scope)
    private val handle = SavedStateHandle()
    private var closed = 0

    @After
    fun stop() {
        scope.cancel()
    }

    private fun session(on: SavedStateHandle = handle, config: EngineConfig = stored) = SettingsSession(on,
        NumberText(compose.activity.resources.configuration.locales[0]), { amsterdam }, { "en" }, access, saves,
        scope, Dispatchers.Main.immediate, config, { closed++ }) { }

    private fun show(session: SettingsSession = session(), fontScaleTwo: Boolean = false): SettingsSession {
        compose.setContent {
            AppTheme {
                val page by session.page.collectAsState()
                if (fontScaleTwo) AtFontScaleTwo { SettingsScreen(session, page) } else SettingsScreen(session, page)
            }
        }
        compose.waitForIdle()
        return session
    }

    /** Clicks a control, scrolled to first when it is on the page, not in the top app bar or a dialog. */
    private fun click(id: Int, vararg args: Any) {
        val control = compose.onNodeWithText(text(id, *args))
        if (hasAnyAncestor(hasScrollAction()).matches(control.fetchSemanticsNode())) {
            control.performScrollTo()
        }
        control.performClick()
    }

    private fun open(page: SettingsPage) {
        click(page.title())
        compose.waitForIdle()
    }

    private fun pageScroll() = compose.onAllNodes(hasScrollAction()).onFirst()
        .fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    private fun up() = compose.onNodeWithContentDescription(text(R.string.action_up)).performClick()

    private fun field(label: String) = compose.onNode(hasSetTextAction() and hasText(label, substring = true))

    private fun title(page: SettingsPage) = compose.onNode(isHeading() and hasText(text(page.title())))

    private fun assertInLiveRegion(shown: String, mode: LiveRegionMode = LiveRegionMode.Polite) {
        assertThat(compose.liveRegionOf(shown)).`as`("the live region of %s", shown).isEqualTo(mode)
    }

    private val apiKey get() = field(text(R.string.provider_setting_of, text(R.string.provider_api_key), "Open-Meteo"))

    private val baseUrl get() = field(text(R.string.provider_setting_of, text(R.string.provider_base_url), "Open-Meteo"))

    /** Opens every page from the list and goes back, each checked as it comes in the language the test runs in. */
    private fun walkEveryPage() {
        val englishOnly = englishOnlyTexts()
        compose.onNode(isHeading() and hasText(text(R.string.settings_title))).assertExists()
        compose.assertShowsNoneOf(englishOnly)
        for (page in SettingsPage.entries) {
            open(page)
            title(page).assertExists()
            if (page == SettingsPage.AWAY) {
                click(R.string.away_add)
            }
            compose.assertShowsNoneOf(englishOnly)
            up()
            compose.waitForIdle()
            if (page == SettingsPage.AWAY) {
                compose.assertShowsNoneOf(englishOnly)
                click(R.string.settings_discard)
                compose.waitForIdle()
            }
        }
    }

    @Test
    fun everyPageIsInEnglish() {
        show()
        walkEveryPage()
    }

    @Test
    @Config(qualifiers = "nl")
    fun everyPageIsInDutch() {
        show()
        walkEveryPage()
    }

    @Test
    fun theListAndEveryPageScrollAndTheirControlsAreNamedAndLargeEnoughAtTwiceTheFontSize() {
        val session = show(fontScaleTwo = true)
        compose.assertScrolls()
        compose.assertEveryControlIsNamedAndLargeEnough()
        for (page in SettingsPage.entries) {
            session.open(page)
            compose.waitForIdle()
            when (page) {
                SettingsPage.SOLAR -> click(R.string.panel_add)
                SettingsPage.LOCATION -> click(R.string.advanced)
                SettingsPage.AWAY -> click(R.string.away_add)
                else -> Unit
            }
            compose.waitForIdle()
            compose.assertScrolls()
            compose.assertEveryControlIsNamedAndLargeEnough()
            session.back()
            session.discard()
            compose.waitForIdle()
        }
    }

    @Test
    fun eachRowSaysWhatThePageHoldsNow() {
        show()

        val kwp = NumberText(Locale.ENGLISH).write(stored.system().panels().single().kwp(), 2)
        compose.onNodeWithText(compose.activity.resources.getQuantityString(R.plurals.settings_solar_summary, 1, 1,
            kwp)).assertExists()
        compose.onNodeWithText(text(R.string.settings_location_summary, "52.0907", "5.1214", "Europe/Amsterdam"))
            .assertExists()
        compose.onNodeWithText(text(R.string.settings_forecast_summary_alone, "Open-Meteo")).assertExists()
        compose.onNodeWithText(text(R.string.settings_away_none)).assertExists()
    }

    @Test
    fun aRowOpensItsPageAndMovesTheFocusToItsTitle() {
        val session = show()

        open(SettingsPage.CHARGER)

        assertThat(session.page.value).isEqualTo(SettingsPage.CHARGER)
        title(SettingsPage.CHARGER).assertIsFocused()
    }

    @Test
    fun saveInTheTopAppBarSavesThePageWhichStaysOpenAndSaysSoInASnackbar() {
        val session = show()
        open(SettingsPage.CAR_AND_HOUSE)
        field(text(R.string.car_battery)).performTextReplacement("75")

        compose.onNodeWithText(text(R.string.action_save)).assert(!hasAnyAncestor(hasScrollAction())).performClick()
        compose.waitForIdle()

        assertThat((access.settings as StartState.Ready).config.system().car().usableBatteryKwh()).isEqualTo(75.0)
        assertThat(session.page.value).isEqualTo(SettingsPage.CAR_AND_HOUSE)
        // Material's snackbar, outside the page that scrolls, which a screen reader reads politely
        assertInLiveRegion(text(R.string.settings_saved))
        compose.onNodeWithText(text(R.string.settings_saved)).assert(!hasAnyAncestor(hasScrollAction()))
        assertThat(session.saving.value).isEqualTo(SettingsSession.SaveState.Idle)
        field(text(R.string.car_battery)).assert(hasText("75.0"))
        up()
        compose.waitForIdle()
        assertThat(session.page.value).isNull()
        // the snackbar stays while the list opens
        compose.onNodeWithText(text(R.string.settings_saved)).assertIsDisplayed()
    }

    @Test
    fun aSecondSavesSnackbarReplacesTheFirstInsteadOfWaitingBehindIt() {
        val session = show()
        open(SettingsPage.CAR_AND_HOUSE)
        compose.mainClock.autoAdvance = false

        repeat(2) { time ->
            session.forms.car.edit(CarForm.Field.BATTERY, "7$time")
            compose.runOnIdle { session.save() }
            compose.mainClock.advanceTimeBy(1_000)
        }
        compose.onNodeWithText(text(R.string.settings_saved)).assertExists()
        compose.mainClock.advanceTimeBy(5_500)

        compose.onNodeWithText(text(R.string.settings_saved)).assertDoesNotExist()
    }

    @Test
    fun aFailedSaveFromTheBottomOfThePageScrollsBackToItsReason() {
        val session = show(fontScaleTwo = true)
        open(SettingsPage.CAR_AND_HOUSE)
        session.forms.car.edit(CarForm.Field.BATTERY, "75")
        access.writeFails = true
        field(text(R.string.baseload)).performScrollTo()
        compose.onNodeWithText(text(R.string.settings_save_failed)).assertDoesNotExist()

        compose.onNodeWithText(text(R.string.action_save)).performClick()
        compose.waitForIdle()

        compose.onNodeWithText(text(R.string.settings_save_failed)).assertIsDisplayed()
    }

    @Test
    fun aSuccessfulSaveFromTheBottomOfThePageLeavesTheScrollWhereItWas() {
        val session = show(fontScaleTwo = true)
        open(SettingsPage.CAR_AND_HOUSE)
        session.forms.car.edit(CarForm.Field.BATTERY, "75")
        access.writeFails = true
        compose.onNodeWithText(text(R.string.action_save)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.settings_save_failed)).assertIsDisplayed()
        access.writeFails = false
        field(text(R.string.baseload)).performScrollTo()
        assertThat(pageScroll()).isGreaterThan(0f)

        val reading = CompletableDeferred<Unit>()
        access.readGate = reading
        compose.onNodeWithText(text(R.string.action_save)).performClick()
        compose.waitForIdle()
        assertThat(pageScroll()).`as`("while saving").isGreaterThan(0f)
        reading.complete(Unit)
        compose.waitForIdle()

        assertThat(access.written).isNotEmpty()
        assertThat(pageScroll()).`as`("once saved").isGreaterThan(0f)
    }

    @Test
    fun keysThatCouldNotBeStoredFromTheBottomOfThePageScrollBackToTheirReason() {
        show(fontScaleTwo = true)
        open(SettingsPage.FORECAST)
        apiKey.performTextInput("K1")
        access.keysFail = true
        baseUrl.performScrollTo()
        compose.onNodeWithText(text(R.string.settings_keys_not_saved)).assertDoesNotExist()

        compose.onNodeWithText(text(R.string.action_save)).performClick()
        compose.waitForIdle()

        compose.onNodeWithText(text(R.string.settings_keys_not_saved)).assertIsDisplayed()
    }

    @Test
    fun aRefusedValueMovesTheFocusToItsField() {
        show()
        open(SettingsPage.PLANNING)
        field(text(R.string.planning_target)).performTextReplacement("10")

        click(R.string.action_save)
        compose.waitForIdle()

        field(text(R.string.planning_minimum)).assertIsFocused()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, text(R.string.problem_not_below_target)))
        assertInLiveRegion(text(R.string.setup_refused))
        assertThat(access.written).isEmpty()
    }

    @Test
    fun backWithEditsAsksAndKeepEditingKeepsThem() {
        val session = show()
        open(SettingsPage.CAR_AND_HOUSE)
        field(text(R.string.car_battery)).performTextReplacement("75")

        compose.activity.onBackPressedDispatcher.onBackPressed()
        compose.waitForIdle()

        compose.onNode(isDialog()).assertExists()
        compose.onNodeWithText(text(R.string.settings_discard_question)).assertExists()
        click(R.string.settings_keep_editing)
        compose.waitForIdle()
        compose.onNode(isDialog()).assertDoesNotExist()
        field(text(R.string.car_battery)).assert(hasText("75"))
        assertThat(session.page.value).isEqualTo(SettingsPage.CAR_AND_HOUSE)
    }

    @Test
    fun upWithEditsAsksAsBackDoesAndDiscardGoesToTheList() {
        val session = show()
        open(SettingsPage.CAR_AND_HOUSE)
        field(text(R.string.car_battery)).performTextReplacement("75")

        up()
        compose.waitForIdle()
        click(R.string.settings_discard)
        compose.waitForIdle()

        assertThat(session.page.value).isNull()
        compose.onNode(isHeading() and hasText(text(R.string.settings_title))).assertExists()
        assertThat(access.written).isEmpty()
    }

    @Test
    fun backAndUpOnTheListLeaveSettings() {
        show()

        compose.activity.onBackPressedDispatcher.onBackPressed()
        up()

        assertThat(closed).isEqualTo(2)
        assertThat(compose.activity.isFinishing).isFalse()
    }

    @Test
    fun theForecastPageAllowsNoScreenshotAndNoAutofillOnlyWhileItShows() {
        show()
        assertThat(secure()).isFalse()

        open(SettingsPage.FORECAST)
        assertThat(secure()).isTrue()
        assertThat(autofillExcluded()).isTrue()
        up()
        compose.waitForIdle()

        assertThat(secure()).isFalse()
        assertThat(autofillExcluded()).isFalse()
    }

    @Test
    fun anApiKeyIsMaskedFilledWithTheStoredKeyAndShowShowsIt() {
        access.keys = Secrets(mapOf(openMeteo to mapOf("api_key" to "STORED1")))
        show()
        open(SettingsPage.FORECAST)
        val key = field(text(R.string.provider_setting_of, text(R.string.provider_api_key), "Open-Meteo"))
        key.assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        assertThat(drawn(key)).isNotEqualTo("STORED1").hasSize("STORED1".length)

        compose.onNodeWithContentDescription(text(R.string.key_show_of, "Open-Meteo")).performScrollTo()
            .performClick()

        assertThat(drawn(key)).isEqualTo("STORED1")
        // a screen reader still treats it as a password, and the keyboard neither suggests nor learns it
        key.assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        compose.onNodeWithContentDescription(text(R.string.key_hide_of, "Open-Meteo")).assertExists()
    }

    @Test
    fun keysThatCouldNotBeReadSaySoAndMayBeEnteredAgain() {
        access.keysUnreadable = true
        show()

        open(SettingsPage.FORECAST)

        compose.onNodeWithText(text(R.string.keys_unreadable)).assertExists()
        field(text(R.string.provider_setting_of, text(R.string.provider_api_key), "Open-Meteo")).assertIsEnabled()
    }

    @Test
    fun keysThatCouldNotBeStoredSaySoInALiveRegion() {
        show()
        open(SettingsPage.FORECAST)
        field(text(R.string.provider_setting_of, text(R.string.provider_api_key), "Open-Meteo"))
            .performTextInput("K1")
        access.keysFail = true

        click(R.string.action_save)
        compose.waitForIdle()

        assertInLiveRegion(text(R.string.settings_not_saved), LiveRegionMode.Assertive)
        assertInLiveRegion(text(R.string.settings_keys_not_saved))
    }

    @Test
    fun checkConnectionShowsTheEnginesEnglishDetailsForAServerOnThisPhone() {
        show()
        open(SettingsPage.FORECAST)
        field(text(R.string.provider_setting_of, text(R.string.provider_base_url), "Open-Meteo"))
            .performTextInput("http://localhost:8080")

        click(R.string.forecast_check, "Open-Meteo")
        compose.waitForIdle()
        access.checks.single().complete(ConnectionResult(false, 0, "Open-Meteo did not answer."))
        compose.waitForIdle()

        assertThat(access.checked.single().second).containsEntry("base_url", "http://localhost:8080")
        assertInLiveRegion(text(R.string.forecast_check_failed))
        compose.onNodeWithText(text(R.string.details_in_english, "Open-Meteo did not answer.")).assertExists()
    }

    @Test
    fun aFallbackThatIsThePrimaryShowsItsProblemAtTheChoiceAndTakesTheFocus() {
        show()
        open(SettingsPage.FORECAST)
        val group = SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription,
            listOf(text(R.string.forecast_fallback)))
        compose.onNode(hasText("Open-Meteo") and hasAnyAncestor(group)).performScrollTo().performClick()

        click(R.string.action_save)
        compose.waitForIdle()

        compose.onNode(group).assertIsFocused()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, text(R.string.problem_same_as_primary)))
    }

    @Test
    fun theLastArraysRemoveIsOffWithItsReasonAndArraysAreNamedByPlaceAndName() {
        show()
        open(SettingsPage.SOLAR)
        compose.onNodeWithText(text(R.string.panel_remove, 1)).assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text(R.string.panel_keep_one)))
        field(text(R.string.panel_in_array, text(R.string.panel_name), 1)).performTextInput("east roof")
        compose.onNode(isHeading() and hasText(text(R.string.panel_array_named, 1, "east roof"))).assertExists()

        click(R.string.panel_add)
        compose.onNode(isHeading() and hasText(text(R.string.panel_array, 2))).assertExists()
        field(text(R.string.panel_in_array, text(R.string.panel_tilt), 2)).assertExists()
        click(R.string.panel_remove, 1)
        compose.waitForIdle()

        compose.onNode(isHeading() and hasText(text(R.string.panel_array, 1))).assertExists()
        compose.onNodeWithText(text(R.string.panel_remove, 1)).assertIsNotEnabled()
    }

    @Test
    fun aProviderWithoutAPlaceSearchHasTheLocationPageSaySo() {
        val forecastSolar = ProviderId("forecast-solar")
        val config = stored.withForecast(ForecastSelection(forecastSolar, Optional.empty(), mapOf()))
        show(session(config = config))

        open(SettingsPage.LOCATION)

        compose.onNodeWithText(text(R.string.settings_no_place_search, "Forecast.Solar")).assertExists()
        field(text(R.string.place_field)).assertDoesNotExist()
        field(text(R.string.latitude)).assertExists()
    }

    @Test
    fun thePageAndItsTextsSurviveTurningThePhone() {
        val restoration = StateRestorationTester(compose)
        val session = session()
        restoration.setContent {
            AppTheme {
                val page by session.page.collectAsState()
                SettingsScreen(session, page)
            }
        }
        open(SettingsPage.CAR_AND_HOUSE)
        field(text(R.string.car_battery)).performTextReplacement("75")

        restoration.emulateSavedInstanceStateRestore()

        title(SettingsPage.CAR_AND_HOUSE).assertExists()
        field(text(R.string.car_battery)).assert(hasText("75"))
    }

    @Test
    fun thePageAndItsTextsSurviveTheSystemEndingTheAppButATypedKeyDoesNot() {
        val restoration = StateRestorationTester(compose)
        var session by mutableStateOf(session())
        restoration.setContent {
            AppTheme {
                val page by session.page.collectAsState()
                SettingsScreen(session, page)
            }
        }
        open(SettingsPage.FORECAST)
        val key = field(text(R.string.provider_setting_of, text(R.string.provider_api_key), "Open-Meteo"))
        key.performTextInput("TYPED2")
        field(text(R.string.provider_setting_of, text(R.string.provider_base_url), "Open-Meteo"))
            .performTextInput("https://example.org")

        session = session(handle.afterTheSystemEndedTheApp())
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        title(SettingsPage.FORECAST).assertExists()
        field(text(R.string.provider_setting_of, text(R.string.provider_base_url), "Open-Meteo"))
            .assert(hasText("https://example.org"))
        assertThat(session.forms.forecast.text(openMeteo, "api_key")).isEmpty()
    }

    @Test
    fun aKeyReadAfterThePageShowedFillsItsMaskedField() {
        access.keys = Secrets(mapOf(openMeteo to mapOf("api_key" to "STORED1")))
        val keys = CompletableDeferred<Unit>()
        access.keysGate = keys
        show()
        open(SettingsPage.FORECAST)
        compose.onNodeWithText(text(R.string.keys_reading)).assertExists()

        keys.complete(Unit)
        compose.waitForIdle()

        assertThat(drawn(apiKey)).isNotEqualTo("STORED1").hasSize("STORED1".length)
    }

    @Test
    fun aKeyShownComesBackMaskedAfterTurningThePhoneAndAfterTheSystemEndedTheApp() {
        access.keys = Secrets(mapOf(openMeteo to mapOf("api_key" to "STORED1")))
        val restoration = StateRestorationTester(compose)
        var session by mutableStateOf(session())
        restoration.setContent {
            AppTheme {
                val page by session.page.collectAsState()
                SettingsScreen(session, page)
            }
        }
        open(SettingsPage.FORECAST)
        val show = text(R.string.key_show_of, "Open-Meteo")
        compose.onNodeWithContentDescription(show).performScrollTo().performClick()
        assertThat(drawn(apiKey)).isEqualTo("STORED1")

        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        assertThat(drawn(apiKey)).isNotEqualTo("STORED1").hasSize("STORED1".length)
        compose.onNodeWithContentDescription(show).performScrollTo().performClick()
        assertThat(drawn(apiKey)).isEqualTo("STORED1")

        session = session(handle.afterTheSystemEndedTheApp())
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        assertThat(drawn(apiKey)).isNotEqualTo("STORED1").hasSize("STORED1".length)
    }

    /** Takes the focus off the headings, so what moves it back is what is under test. */
    private fun focusAwayFromTheHeadings() {
        compose.onAllNodes(hasSetTextAction()).onFirst().performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitForIdle()
    }

    private fun array(number: Int) = compose.onNode(isHeading() and hasText(text(R.string.panel_array, number)))

    private fun removeArray(number: Int) {
        focusAwayFromTheHeadings()
        click(R.string.panel_remove, number)
        compose.waitForIdle()
    }

    @Test
    fun addAndRemoveOfAnArrayMoveTheFocusToTheNewArrayAndToTheOneBefore() {
        show()
        open(SettingsPage.SOLAR)

        click(R.string.panel_add)
        compose.waitForIdle()
        array(2).assertIsFocused()
        click(R.string.panel_add)
        compose.waitForIdle()
        removeArray(3)

        array(2).assertIsFocused()
        removeArray(1)
        array(1).assertIsFocused()
    }

    @Test
    fun removingTheFirstOfSeveralArraysMovesTheFocusToTheNewFirst() {
        show()
        open(SettingsPage.SOLAR)
        repeat(2) { click(R.string.panel_add) }
        compose.waitForIdle()

        removeArray(1)

        array(1).assertIsFocused()
    }

    @Test
    fun removingAMiddleArrayMovesTheFocusToTheOneBefore() {
        show()
        open(SettingsPage.SOLAR)
        repeat(2) { click(R.string.panel_add) }
        compose.waitForIdle()

        removeArray(2)

        array(1).assertIsFocused()
    }

    @Test
    fun theFocusMovesAgainToAHeadingItAlreadyWentToOnceTheFocusLeftIt() {
        show()
        open(SettingsPage.SOLAR)
        repeat(2) { click(R.string.panel_add) }
        compose.waitForIdle()
        removeArray(1)
        array(1).assertIsFocused()

        removeArray(2)

        array(1).assertIsFocused()
    }

    @Test
    fun addAndRemoveOfAnAwayPeriodMoveTheFocusToTheNewPeriodAndToTheOneBefore() {
        show()
        open(SettingsPage.AWAY)

        repeat(2) {
            click(R.string.away_add)
            compose.waitForIdle()
        }
        compose.onNode(isHeading() and hasText(text(R.string.away_period, 2))).assertIsFocused()
        focusAwayFromTheHeadings()
        click(R.string.away_remove, 2)
        compose.waitForIdle()

        compose.onNode(isHeading() and hasText(text(R.string.away_period, 1))).assertIsFocused()
    }

    @Test
    fun removingTheOnlyAwayPeriodMovesTheFocusToTheLineThatSaysThereIsNone() {
        show()
        open(SettingsPage.AWAY)
        click(R.string.away_add)
        compose.waitForIdle()
        focusAwayFromTheHeadings()

        click(R.string.away_remove, 1)
        compose.waitForIdle()

        compose.onNodeWithText(text(R.string.away_none)).assertIsFocused()
        compose.onAllNodesWithText(text(R.string.away_add)).assertCountEquals(1)
    }

    @Test
    fun aRefusedValueFarDownThePageIsNotScrolledAwayFromItsField() {
        show(fontScaleTwo = true)
        open(SettingsPage.AWAY)
        repeat(4) {
            click(R.string.away_add)
            compose.waitForIdle()
        }
        field(text(R.string.away_from, 4)).performScrollTo().performTextReplacement("not a time")
        field(text(R.string.away_from, 4)).assertIsDisplayed()

        compose.onNodeWithText(text(R.string.action_save)).performClick()
        compose.waitForIdle()

        field(text(R.string.away_from, 4)).assertIsFocused().assertIsDisplayed()
    }

    /** The text a field draws, after its mask. */
    private fun drawn(field: SemanticsNodeInteraction): String {
        val layouts = mutableListOf<TextLayoutResult>()
        field.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        return layouts.first().layoutInput.text.text
    }

    private fun secure() = (compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0

    private fun autofillExcluded(): Boolean = views(compose.activity.window.decorView)
        .any { it.importantForAutofill == View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS }

    private fun views(view: View): List<View> = listOf(view) +
        ((view as? ViewGroup)?.let { group -> (0 until group.childCount).flatMap { views(group.getChildAt(it)) } })
            .orEmpty()

    @Test
    fun theListAPageAndADialogHaveTheirControlsNamedAndLargeEnoughAtTheNormalFontSize() {
        val session = show()
        compose.assertEveryControlIsNamedAndLargeEnough()
        session.open(SettingsPage.FORECAST)
        compose.waitForIdle()
        compose.assertEveryControlIsNamedAndLargeEnough()

        session.forms.forecast.chooseFallback(ProviderId("forecast-solar"))
        compose.activity.onBackPressedDispatcher.onBackPressed()
        compose.waitForIdle()

        compose.onNode(isDialog()).assertExists()
        compose.assertEveryControlIsNamedAndLargeEnough()
    }

    @Test
    fun eachRowIsOneButtonThatReadsThePagesNameAndItsValues() {
        show()

        compose.onNode(hasClickAction() and hasText(text(R.string.settings_away))
            and hasText(text(R.string.settings_away_none))).assertExists()
    }

    @Test
    fun dismissingTheDiscardQuestionWithBackKeepsEditing() {
        val session = askToDiscard()

        compose.runOnIdle { (ShadowDialog.getLatestDialog() as ComponentDialog).onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()

        assertStillEditing(session)
    }

    @Test
    fun dismissingTheDiscardQuestionWithATapOutsideKeepsEditing() {
        val session = askToDiscard()

        compose.runOnIdle {
            val window = ShadowDialog.getLatestDialog().window!!.decorView
            val now = SystemClock.uptimeMillis()
            listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
                // well away from the dialog, above and to the left of its window
                val touch = MotionEvent.obtain(now, now, action, -100f, -100f, 0)
                window.dispatchTouchEvent(touch)
                touch.recycle()
            }
        }
        compose.waitForIdle()

        assertStillEditing(session)
    }

    /** The Car and house page with an edit, and Back's question. */
    private fun askToDiscard(): SettingsSession {
        val session = show()
        open(SettingsPage.CAR_AND_HOUSE)
        field(text(R.string.car_battery)).performTextReplacement("75")
        compose.activity.onBackPressedDispatcher.onBackPressed()
        compose.waitForIdle()
        compose.onNode(isDialog()).assertExists()
        return session
    }

    private fun assertStillEditing(session: SettingsSession) {
        compose.onNode(isDialog()).assertDoesNotExist()
        assertThat(session.discardAsked.value).isFalse()
        assertThat(session.page.value).isEqualTo(SettingsPage.CAR_AND_HOUSE)
        field(text(R.string.car_battery)).assert(hasText("75"))
    }

    @Test
    fun aFailedSaveIsReadAtOnceUnderTheTopAppBarAndItsEnglishDetailsOpenThePage() {
        show()
        open(SettingsPage.CAR_AND_HOUSE)
        field(text(R.string.car_battery)).performTextReplacement("75")
        access.writeFails = true

        click(R.string.action_save)
        compose.waitForIdle()

        compose.onNodeWithText(text(R.string.settings_not_saved)).assert(!hasAnyAncestor(hasScrollAction()))
        assertInLiveRegion(text(R.string.settings_not_saved), LiveRegionMode.Assertive)
        val details = text(R.string.details_in_english, "No space left on device")
        compose.onNodeWithText(details, useUnmergedTree = true).assert(hasAnyAncestor(hasScrollAction()))
        assertInLiveRegion(text(R.string.settings_save_failed))
        assertInLiveRegion(details)
    }

    @Test
    @Config(qualifiers = "w480dp-h320dp-land")
    fun aFailedSaveWithALongDetailLeavesThePageItsRoomOnASmallScreenAtTwiceTheFontSize() {
        val detail = "Could not write /data/user/0/nl.enadeag.carchargeplanner/files/engine.json.tmp-1730637045123 " +
            "because the file system reported that no space is left on the device and the write stopped."
        assertThat(detail.length).isGreaterThan(180)
        show(fontScaleTwo = true)
        open(SettingsPage.CAR_AND_HOUSE)
        field(text(R.string.car_battery)).performTextReplacement("75")
        access.writeFails = true
        access.writeProblem = detail

        click(R.string.action_save)
        compose.waitForIdle()

        compose.onNodeWithText(text(R.string.settings_not_saved)).assertIsDisplayed()
        compose.assertScrolls()
        compose.onNodeWithText(text(R.string.details_in_english, detail), useUnmergedTree = true).performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun withTwoProblemsTheFocusGoesToTheUpperOne() {
        show()
        open(SettingsPage.PLANNING)
        field(text(R.string.planning_margin)).performTextReplacement("lots")
        field(text(R.string.planning_minimum)).performTextReplacement("lots")

        click(R.string.action_save)
        compose.waitForIdle()

        field(text(R.string.planning_minimum)).assertIsFocused()
    }

    @Test
    fun onEveryPageOnlyTheLastFieldHasTheKeyboardsDone() {
        val session = show()
        for (page in SettingsPage.entries) {
            session.open(page)
            compose.waitForIdle()
            when (page) {
                SettingsPage.SOLAR -> click(R.string.panel_add)
                SettingsPage.AWAY -> repeat(2) { click(R.string.away_add) }
                else -> Unit
            }
            compose.waitForIdle()
            compose.assertOnlyTheLastFieldIsDone()
            session.back()
            session.discard()
            compose.waitForIdle()
        }
    }

    @Test
    fun theApiKeysKeyboardIsForAPasswordWithoutCorrections() {
        show()
        open(SettingsPage.FORECAST)

        apiKey.performScrollTo().performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitForIdle()

        val editor = EditorInfo()
        compose.runOnIdle { textEditor(compose.activity.window.decorView)!!.onCreateInputConnection(editor) }
        assertThat(editor.inputType and InputType.TYPE_MASK_VARIATION)
            .isEqualTo(InputType.TYPE_TEXT_VARIATION_PASSWORD)
        assertThat(editor.inputType and InputType.TYPE_TEXT_FLAG_AUTO_CORRECT).isZero()
    }

    @Test
    fun saveOnTheForecastPageWaitsForTheKeysAndWhileItSaves() {
        val keys = CompletableDeferred<Unit>()
        access.keysGate = keys
        show()
        open(SettingsPage.FORECAST)
        compose.onNodeWithText(text(R.string.action_save)).assertIsNotEnabled()

        keys.complete(Unit)
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.action_save)).assertIsEnabled()
        baseUrl.performTextInput("https://example.org")
        access.readGate = CompletableDeferred()
        click(R.string.action_save)
        compose.waitForIdle()

        compose.onNodeWithText(text(R.string.settings_saving)).assertIsNotEnabled()
    }

    @Test
    fun anotherPageAllowsScreenshotsAndAutofill() {
        show()

        open(SettingsPage.CAR_AND_HOUSE)

        assertThat(secure()).isFalse()
        assertThat(autofillExcluded()).isFalse()
    }

    @Test
    fun systemBackFromTheForecastPageEndsItsProtection() {
        show()
        open(SettingsPage.FORECAST)
        assertThat(secure()).isTrue()

        compose.activity.onBackPressedDispatcher.onBackPressed()
        compose.waitForIdle()

        assertThat(secure()).isFalse()
        assertThat(autofillExcluded()).isFalse()
    }

    @Test
    fun backAndDiscardFromTheForecastPageEndItsProtection() {
        show()
        open(SettingsPage.FORECAST)
        baseUrl.performTextInput("https://example.org")
        compose.activity.onBackPressedDispatcher.onBackPressed()
        compose.waitForIdle()
        assertThat(secure()).isTrue()

        click(R.string.settings_discard)
        compose.waitForIdle()

        assertThat(secure()).isFalse()
        assertThat(autofillExcluded()).isFalse()
    }

    @Test
    fun theForecastPageIsProtectedAgainAfterTurningThePhoneAndAfterTheSystemEndedTheApp() {
        val restoration = StateRestorationTester(compose)
        var session by mutableStateOf(session())
        restoration.setContent {
            AppTheme {
                val page by session.page.collectAsState()
                SettingsScreen(session, page)
            }
        }
        open(SettingsPage.FORECAST)

        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        assertThat(secure()).isTrue()
        assertThat(autofillExcluded()).isTrue()

        session = session(handle.afterTheSystemEndedTheApp())
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        assertThat(secure()).isTrue()
        assertThat(autofillExcluded()).isTrue()
    }

    @Test
    fun theOpenMeteoAddressHintIsTheAppsOwnAndSaysHttps() {
        show()

        open(SettingsPage.FORECAST)

        val hint = text(R.string.provider_open_meteo_base_url_hint)
        assertThat(hint).contains("https://")
        compose.onNodeWithText(hint, useUnmergedTree = true).assertExists()
    }

    /** The view that takes the keyboard's input, Compose's own. */
    private fun textEditor(view: View): View? = views(view).firstOrNull { it.onCheckIsTextEditor() }
}

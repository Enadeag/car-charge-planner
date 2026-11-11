package nl.enadeag.carchargeplanner.android.ui

import android.provider.Settings
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.os.SystemClock
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Year
import java.time.ZoneId
import java.util.Locale
import java.util.Optional
import java.util.OptionalInt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import nl.enadeag.carchargeplanner.android.R
import nl.enadeag.carchargeplanner.android.afterTheSystemEndedTheApp
import nl.enadeag.carchargeplanner.android.form.CarForm
import nl.enadeag.carchargeplanner.android.form.NumberText
import nl.enadeag.carchargeplanner.android.prefs.WifiOnly
import nl.enadeag.carchargeplanner.android.settings.SettingsPage
import nl.enadeag.carchargeplanner.android.settings.SettingsSession
import nl.enadeag.carchargeplanner.android.store.FakeEngineAccess
import nl.enadeag.carchargeplanner.android.store.Saves
import nl.enadeag.carchargeplanner.android.store.StartState
import nl.enadeag.carchargeplanner.android.ui.settings.SettingsIcons
import nl.enadeag.carchargeplanner.android.ui.settings.SettingsScreen
import nl.enadeag.carchargeplanner.android.ui.settings.icon
import nl.enadeag.carchargeplanner.android.ui.settings.title
import nl.enadeag.carchargeplanner.engine.config.Coordinates
import nl.enadeag.carchargeplanner.engine.config.EngineConfig
import nl.enadeag.carchargeplanner.engine.config.ForecastSelection
import nl.enadeag.carchargeplanner.engine.config.House
import nl.enadeag.carchargeplanner.engine.config.PanelArray
import nl.enadeag.carchargeplanner.engine.config.PanelSize
import nl.enadeag.carchargeplanner.engine.config.PlaceName
import nl.enadeag.carchargeplanner.engine.config.ProviderId
import nl.enadeag.carchargeplanner.engine.config.TypicalValues
import nl.enadeag.carchargeplanner.engine.forecast.ConnectionResult
import nl.enadeag.carchargeplanner.engine.forecast.Place
import nl.enadeag.carchargeplanner.engine.forecast.PlaceResults
import nl.enadeag.carchargeplanner.engine.secrets.Secrets
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
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
    private val stored = draft.withSystem(draft.system().withUnnamedLocation(Coordinates(52.0907, 5.1214), amsterdam),
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

    private var errorColour = Color.Unspecified

    private var onSurfaceVariant = Color.Unspecified

    private fun session(on: SavedStateHandle = handle, config: EngineConfig = stored) = SettingsSession(on,
        NumberText(compose.activity.resources.configuration.locales[0]), { amsterdam }, { "en" }, access, saves,
        scope, Dispatchers.Main.immediate, config, { closed++ }) { }

    /** The "Wi-Fi only" switch in memory, with every switch the screen asked for. */
    private class Switch : WifiOnly {
        val now = MutableStateFlow(false)
        val asked = mutableListOf<Boolean>()

        override val on: Flow<Boolean> = now

        override fun switch(on: Boolean) {
            asked += on
            now.value = on
        }
    }

    private val wifiOnly = Switch()

    private fun show(session: SettingsSession = session(), fontScaleTwo: Boolean = false): SettingsSession {
        compose.setContent {
            AppTheme {
                errorColour = MaterialTheme.colorScheme.error
                onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
                val page by session.page.collectAsState()
                if (fontScaleTwo) {
                    AtFontScaleTwo { SettingsScreen(session, page, wifiOnly) }
                } else {
                    SettingsScreen(session, page, wifiOnly)
                }
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
                click(R.string.discard)
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

        val kwp = NumberText(Locale.ENGLISH).write(stored.system().panels().single().kwp(), 1)
        compose.onNodeWithText(compose.activity.resources.getQuantityString(R.plurals.settings_solar_summary, 1, 1,
            kwp)).assertExists()
        compose.onNodeWithText(text(R.string.settings_location_summary, "52.0907", "5.1214")).assertExists()
        compose.onNodeWithText("Europe/Amsterdam", substring = true).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.settings_forecast_summary_alone, "Open-Meteo")).assertExists()
        compose.onNodeWithText(text(R.string.settings_away_none)).assertExists()
    }

    @Test
    fun theCarAndHouseRowGivesTheBatteryOnly() {
        show()

        val battery = NumberText(Locale.ENGLISH).write(stored.system().car().usableBatteryKwh(), 1)
        compose.onNodeWithText("Battery $battery kWh").assertExists()
    }

    @Test
    @Config(qualifiers = "nl")
    fun inDutchTheCarAndHouseRowGivesTheBatteryOnly() {
        show()

        val battery = NumberText(Locale.forLanguageTag("nl")).write(stored.system().car().usableBatteryKwh(), 1)
        compose.onNodeWithText("Accu $battery kWh").assertExists()
    }

    private fun houseFold() = compose.onNodeWithText(text(R.string.more_settings))

    private fun houseFoldIs(state: Int) =
        houseFold().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text(state)))

    @Test
    fun theHousesUseIsFoldedAwayAlsoWhenItIsNotTheTypicalOneAndAnUntouchedOneIsSavedExactly() {
        val own = stored.withSystem(stored.system().withHouseAndCar(House(0.123), stored.system().car()),
            TypicalValues.NONE)
        access.settings = StartState.Ready(own)
        show(session(config = own))
        open(SettingsPage.CAR_AND_HOUSE)

        houseFoldIs(R.string.fold_collapsed)
        compose.onNode(isHeading() and hasText("House")).assertDoesNotExist()
        field(text(R.string.household_use)).assertDoesNotExist()
        compose.onAllNodesWithText(text(R.string.use_typical)).assertCountEquals(1)
        click(R.string.more_settings)
        field(text(R.string.household_use)).assert(hasText("3.0"))
            .assert(hasText(text(R.string.household_use_hint), substring = true))
        compose.onAllNodesWithText(text(R.string.use_typical)).assertCountEquals(2)
        compose.assertOnlyTheLastFieldIsDone()

        field(text(R.string.car_battery)).performTextReplacement("75")
        click(R.string.action_save)
        compose.waitForIdle()

        val saved = (access.settings as StartState.Ready).config.system()
        assertThat(saved.car().usableBatteryKwh()).isEqualTo(75.0)
        assertThat(saved.house()).isEqualTo(House(0.123))
    }

    @Test
    fun aProblemInTheHousesUseOpensTheFoldWhichStaysOpenUntilItIsPutRight() {
        show()
        open(SettingsPage.CAR_AND_HOUSE)
        click(R.string.more_settings)
        field(text(R.string.household_use)).performTextReplacement("-1")
        click(R.string.more_settings)
        houseFoldIs(R.string.fold_collapsed)

        click(R.string.action_save)
        compose.waitForIdle()

        houseFoldIs(R.string.fold_expanded)
        field(text(R.string.household_use)).assertIsFocused()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, text(R.string.problem_out_of_range)))
        assertThat(access.written).isEmpty()
        click(R.string.more_settings)
        houseFoldIs(R.string.fold_expanded)

        field(text(R.string.household_use)).performTextReplacement("480")
        compose.waitForIdle()
        houseFoldIs(R.string.fold_expanded)
        click(R.string.more_settings)
        houseFoldIs(R.string.fold_collapsed)
        click(R.string.action_save)
        compose.waitForIdle()

        assertThat((access.settings as StartState.Ready).config.system().house()).isEqualTo(House(20.0))
    }

    /** Dutch numbers have a decimal comma, so a semicolon keeps the latitude and the longitude apart. */
    @Test
    @Config(qualifiers = "nl")
    fun theLocationRowInDutchPutsASemicolonBetweenTheCoordinates() {
        show()

        compose.onNodeWithText("52,0907; 5,1214").assertExists()
    }

    @Test
    fun theLocationRowGivesThePlaceNameInPlaceOfTheCoordinates() {
        show(session(config = stored.withSystem(stored.system().withPlace(Coordinates(52.0907, 5.1214), amsterdam,
            PlaceName("Utrecht, Netherlands")), stored.typical())))

        compose.onNodeWithText("Utrecht, Netherlands").assertExists()
        compose.onNodeWithText(text(R.string.settings_location_summary, "52.0907", "5.1214")).assertDoesNotExist()
    }

    @Test
    fun theSolarRowGivesACountedArraysPeakPowerWithOneDecimal() {
        val counted = stored.withSystem(stored.system().withPanels(listOf(PanelArray("roof",
            PanelSize.Panels(10, OptionalInt.empty(), Optional.of(Year.of(2021))), 35.0, 0.0, 0.14))),
            TypicalValues.NONE)
        show(session(config = counted))

        compose.onNodeWithText("1 array, 3.6 kWp").assertExists()
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
        compose.onNodeWithText(text(R.string.more_settings)).performScrollTo()
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
        compose.onNodeWithText(text(R.string.more_settings)).performScrollTo()
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
        assertThat(compose.liveRegionOf(text(R.string.problem_not_below_target))).isNull()
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
        compose.onNodeWithText(text(R.string.discard_question)).assertExists()
        compose.onNodeWithText(text(R.string.settings_discard_text)).assertExists()
        click(R.string.keep_editing)
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
        compose.onNodeWithText(text(R.string.settings_discard_text)).assertExists()
        click(R.string.discard)
        compose.waitForIdle()

        assertThat(session.page.value).isNull()
        compose.onNode(isHeading() and hasText(text(R.string.settings_title))).assertExists()
        assertThat(access.written).isEmpty()
    }

    @Test
    fun discardIsDrawnInTheErrorColourAndKeepEditingIsNot() {
        show()
        open(SettingsPage.CAR_AND_HOUSE)
        field(text(R.string.car_battery)).performTextReplacement("75")
        up()
        compose.waitForIdle()

        fun colourOf(id: Int): Color {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(text(id), useUnmergedTree = true).fetchSemanticsNode()
                .config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
            return layouts.single().layoutInput.style.color
        }
        assertThat(errorColour).isNotEqualTo(Color.Unspecified)
        assertThat(colourOf(R.string.discard)).isEqualTo(errorColour)
        assertThat(colourOf(R.string.keep_editing)).isNotEqualTo(errorColour)
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
    fun anArrayStoredByItsPeakPowerSaysSoAndShowsTheNearestChoices() {
        show()
        open(SettingsPage.SOLAR)

        compose.onNodeWithText(text(R.string.panel_now_set, "5.0")).performScrollTo().assertIsDisplayed()
        field(text(R.string.panel_in_array, text(R.string.panel_count), 1)).assert(hasText("", substring = true))
        compose.onNode(hasText(text(R.string.panel_in_array, text(R.string.panel_direction), 1), substring = true)
            and hasText(text(R.string.direction_south), substring = true)).assertExists()
        compose.onNodeWithText(text(R.string.use_typical), substring = true).assertDoesNotExist()

        field(text(R.string.panel_in_array, text(R.string.panel_count), 1)).performTextInput("10")
        field(text(R.string.panel_in_array, text(R.string.panel_year), 1)).performTextInput("2021")
        compose.waitForIdle()

        compose.onNodeWithText(text(R.string.panel_now_set, "5.0")).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.panel_together, "3.6")).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theLastArraysRemoveIsOffWithItsReasonAndArraysAreNamedByPlaceAndName() {
        show()
        open(SettingsPage.SOLAR)
        compose.onNodeWithText(text(R.string.panel_remove, 1)).assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text(R.string.panel_keep_one)))
        compose.onNodeWithText(text(R.string.panel_in_array, text(R.string.more_settings), 1)).performScrollTo()
            .performClick()
        field(text(R.string.panel_in_array, text(R.string.panel_name), 1)).performTextInput("east roof")
        compose.onNode(isHeading() and hasText(text(R.string.panel_array_named, 1, "east roof"))).assertExists()

        click(R.string.panel_add)
        compose.onNode(isHeading() and hasText(text(R.string.panel_array, 2))).assertExists()
        field(text(R.string.panel_in_array, text(R.string.panel_count), 2)).assertExists()
        listOf(R.string.panel_direction, R.string.panel_tilt).forEach { label ->
            compose.onNode(hasText(text(R.string.panel_in_array, text(label), 2), substring = true)
                and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList))
                .assert(hasText(text(R.string.choose), substring = true))
        }
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
        compose.onNodeWithText(text(R.string.time_zone_now, amsterdam.id)).assertExists()
        compose.onNodeWithText(text(R.string.coordinates_fold)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.place_not_chosen)).assertDoesNotExist()
    }

    @Test
    fun aStoredPlaceShowsAtTheTopWithTheCoordinatesFolded() {
        show(session(config = stored.withSystem(stored.system().withPlace(Coordinates(52.0907, 5.1214), amsterdam,
            PlaceName("Utrecht, Netherlands")), stored.typical())))

        open(SettingsPage.LOCATION)

        compose.onNodeWithText(text(R.string.place_chosen, "Utrecht, Netherlands")).assertExists()
        compose.onNodeWithText(text(R.string.coordinates_fold))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text(R.string.fold_collapsed)))
        field(text(R.string.latitude)).assertDoesNotExist()
        field(text(R.string.place_field)).assert(hasText(text(R.string.place_another), substring = true))
    }

    @Test
    fun aChosenPlaceShowsAtTheTopTakesTheFocusAndFoldsTheCoordinatesAway() {
        show()
        open(SettingsPage.LOCATION)
        compose.onNodeWithText(text(R.string.place_not_chosen)).assertExists()
        field(text(R.string.latitude)).assertExists()
        field(text(R.string.place_field)).performTextInput("Amersfoort")
        click(R.string.place_search)
        compose.waitForIdle()
        access.searches.last().complete(PlaceResults.Found(listOf(Place("Amersfoort", Coordinates(52.1561, 5.3878),
            amsterdam, "Utrecht", "Netherlands", ""))))
        compose.waitForIdle()

        compose.onNodeWithText("Amersfoort, Utrecht, Netherlands").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNode(hasText(text(R.string.place_chosen, "Amersfoort, Utrecht, Netherlands"))).assertIsDisplayed()
            .assertIsFocused()
        field(text(R.string.latitude)).assertDoesNotExist()
        click(R.string.coordinates_fold)
        field(text(R.string.latitude)).assert(hasText("52.1561"))
    }

    @Test
    fun openingTheLocationPageAgainAfterAChoiceThatWasNotSavedShowsNoPlaceAndAnEmptyPlaceField() {
        val session = show()
        open(SettingsPage.LOCATION)
        field(text(R.string.place_field)).performTextInput("Amersfoort")
        click(R.string.place_search)
        compose.waitForIdle()
        access.searches.last().complete(PlaceResults.Found(listOf(Place("Amersfoort", Coordinates(52.1561, 5.3878),
            amsterdam, "Utrecht", "Netherlands", ""))))
        compose.waitForIdle()
        compose.onNodeWithText("Amersfoort, Utrecht, Netherlands").performScrollTo().performClick()
        compose.waitForIdle()

        session.back()
        session.discard()
        compose.waitForIdle()
        open(SettingsPage.LOCATION)

        compose.onNodeWithText(text(R.string.place_not_chosen)).assertExists()
        compose.onNode(hasText(text(R.string.place_chosen, "Amersfoort, Utrecht, Netherlands"))).assertDoesNotExist()
        field(text(R.string.place_field)).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText,
            AnnotatedString("")))
    }

    @Test
    fun thePageAndItsTextsSurviveTurningThePhone() {
        val restoration = StateRestorationTester(compose)
        val session = session()
        restoration.setContent {
            AppTheme {
                val page by session.page.collectAsState()
                SettingsScreen(session, page, OffWifiOnly)
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
                SettingsScreen(session, page, OffWifiOnly)
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
                SettingsScreen(session, page, OffWifiOnly)
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
    fun everyPageHasMaterialsFilledIconForWhatItHolds() {
        val names = SettingsPage.entries.associateWith { it.icon().name }

        assertThat(names).isEqualTo(mapOf(
            SettingsPage.SOLAR to "Filled.SolarPower",
            SettingsPage.LOCATION to "Filled.LocationOn",
            SettingsPage.CAR_AND_HOUSE to "Filled.ElectricCar",
            SettingsPage.CHARGER to "Filled.EvStation",
            SettingsPage.FORECAST to "Filled.WbCloudy",
            SettingsPage.AWAY to "Filled.EventBusy",
            SettingsPage.PLANNING to "Filled.Schedule",
        ))
        assertThat(SettingsPage.entries.map { it.icon() }.toSet()).hasSize(SettingsPage.entries.size)
    }

    @Test
    fun theCopiedIconsDrawMaterialsShapesUnchanged() {
        val copied = listOf(SettingsIcons.SolarPower, SettingsIcons.ElectricCar, SettingsIcons.EvStation,
            SettingsIcons.WbCloudy, SettingsIcons.EventBusy, SettingsIcons.Schedule)

        // measured on Icons.Filled's own icons of material-icons-extended-android 1.7.8 with shape(): the number of
        // paths, of their nodes, and the hash of the nodes' text, and how each path is filled, which is materialPath's
        // default there: black, fully opaque, not stroked, non-zero
        assertThat(copied.associate { it.name to shape(it) }).isEqualTo(mapOf(
            "Filled.SolarPower" to expected(10, 50, -877365170),
            "Filled.ElectricCar" to expected(2, 43, 995439527),
            "Filled.EvStation" to expected(1, 42, -585541679),
            "Filled.WbCloudy" to expected(1, 9, 1954581329),
            "Filled.EventBusy" to expected(1, 38, -955125091),
            "Filled.Schedule" to expected(2, 19, -750104808),
        ))
        assertThat(copied).allSatisfy {
            assertThat(listOf(it.viewportWidth, it.viewportHeight)).containsExactly(24f, 24f)
            assertThat(listOf(it.defaultWidth, it.defaultHeight)).containsExactly(24.dp, 24.dp)
            assertThat(it.autoMirror).`as`("%s mirrored right to left", it.name).isFalse()
        }
    }

    /** What an icon draws: its paths' count, their nodes' count and the hash of the nodes' text, and their fills. */
    private data class Shape(val paths: Int, val nodes: Int, val hash: Int, val fills: List<Fill>)

    /** How one path is filled and stroked. */
    private data class Fill(
        val fill: Brush?,
        val fillAlpha: Float,
        val stroke: Brush?,
        val strokeAlpha: Float,
        val type: PathFillType,
    )

    /** The [Shape] of an icon whose paths all are filled as materialPath does by default. */
    private fun expected(paths: Int, nodes: Int, hash: Int) = Shape(paths, nodes, hash,
        List(paths) { Fill(SolidColor(Color.Black), 1f, null, 1f, PathFillType.NonZero) })

    /** An icon's [Shape]; `Close` is an object, whose own text differs per run. */
    private fun shape(icon: ImageVector): Shape {
        val paths = (0 until icon.root.size).map { icon.root[it] as VectorPath }
        val nodes = paths.joinToString(";") { path ->
            path.pathData.joinToString(" ") { if (it == PathNode.Close) "Close" else it.toString() }
        }
        return Shape(paths.size, paths.sumOf { it.pathData.size }, nodes.hashCode(),
            paths.map { Fill(it.fill, it.fillAlpha, it.stroke, it.strokeAlpha, it.pathFillType) })
    }

    @Test
    fun eachRowIsOneButtonThatReadsItsNameAndValuesButNothingOfItsIcon() {
        show()

        SettingsPage.entries.forEach { page ->
            val row = row(page)
            assertThat(row.config.getOrNull(SemanticsProperties.Role)).isEqualTo(Role.Button)
            assertThat(row.config.getOrNull(SemanticsProperties.ContentDescription))
                .`as`("what %s's icon reads", page).isNull()
            assertThat(row.config[SemanticsProperties.Text].map { it.text }).hasSize(2).first()
                .isEqualTo(text(page.title()))
        }
    }

    @Test
    fun eachRowIsAtLeast56DpHigh() {
        show()

        (SettingsPage.entries.map { it to row(it) } + ("notifications" to notificationsRow()) +
            ("licences" to licencesRow())).forEach { (page, row) ->
            assertThat(row.size.height / row.layoutInfo.density.density).`as`("the height of %s's row", page)
                .isGreaterThanOrEqualTo(56f)
        }
    }

    /** The notifications' row, after the pages', scrolled to. */
    private fun notificationsRow() = compose.onNode(hasClickAction() and hasText(text(R.string.settings_notifications)))
        .performScrollTo().fetchSemanticsNode()

    @Test
    fun theNotificationsRowIsOneButtonBetweenThePagesAndTheLicences() {
        show()

        val row = notificationsRow()
        assertThat(row.config.getOrNull(SemanticsProperties.Role)).isEqualTo(Role.Button)
        assertThat(row.config.getOrNull(SemanticsProperties.ContentDescription)).`as`("what its icon reads").isNull()
        assertThat(row.config[SemanticsProperties.Text].map { it.text })
            .containsExactly(text(R.string.settings_notifications), text(R.string.settings_notifications_summary))
        assertThat(row.size.height / row.layoutInfo.density.density).isGreaterThanOrEqualTo(56f)
        val last = row(SettingsPage.entries.last()).positionInRoot.y
        assertThat(notificationsRow().positionInRoot.y).isGreaterThan(last)
            .isLessThan(licencesRow().positionInRoot.y)
    }

    @Test
    fun theNotificationsRowOpensAndroidsSettingsOfTheAppsNotifications() {
        show()

        click(R.string.settings_notifications)

        val started = shadowOf(compose.activity).nextStartedActivity
        assertThat(started.action).isEqualTo(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        assertThat(started.getStringExtra(Settings.EXTRA_APP_PACKAGE)).isEqualTo(compose.activity.packageName)
    }

    /** The "Wi-Fi only" switch's row, scrolled to. */
    private fun wifiRow() = compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        .performScrollTo()

    @Test
    fun theForecastPageEndsWithTheWifiOnlySwitchAsOneRowReadWithItsLabelAndHint() {
        show()
        open(SettingsPage.FORECAST)

        val row = wifiRow().fetchSemanticsNode()
        assertThat(row.config[SemanticsProperties.Text].map { it.text })
            .containsExactly(text(R.string.forecast_wifi_only), text(R.string.forecast_wifi_only_hint))
        assertThat(row.config[SemanticsProperties.ToggleableState]).isEqualTo(ToggleableState.Off)
        assertThat(row.size.height / row.layoutInfo.density.density).isGreaterThanOrEqualTo(48f)
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)).assertCountEquals(1)
    }

    @Test
    fun theWifiOnlySwitchAppliesAtOnceAndLeavesNothingToSaveOrDiscard() {
        show()
        open(SettingsPage.FORECAST)

        wifiRow().performClick()
        compose.waitForIdle()

        assertThat(wifiOnly.asked).containsExactly(true)
        assertThat(wifiRow().fetchSemanticsNode().config[SemanticsProperties.ToggleableState])
            .isEqualTo(ToggleableState.On)
        up()
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.settings_discard_text)).assertDoesNotExist()
        compose.onNode(isHeading() and hasText(text(R.string.settings_title))).assertExists()
        assertThat(access.written).isEmpty()
    }

    @Test
    @Config(qualifiers = "nl")
    fun theNotificationsRowAndTheWifiOnlySwitchAreInDutch() {
        show()

        assertThat(notificationsRow().config[SemanticsProperties.Text].map { it.text })
            .containsExactly("Meldingen", "Opent de instellingen van Android voor deze app")
        open(SettingsPage.FORECAST)
        assertThat(wifiRow().fetchSemanticsNode().config[SemanticsProperties.Text].map { it.text })
            .containsExactly("Alleen wifi", "De update elk uur terwijl de app dicht is wacht op wifi, of een ander " +
                "netwerk zonder datalimiet. Uit: elk netwerk.")
    }

    @Test
    fun theWifiOnlySwitchShowsWhatIsStoredAndSwitchesItOff() {
        wifiOnly.now.value = true
        show()
        open(SettingsPage.FORECAST)

        wifiRow().performClick()
        compose.waitForIdle()

        assertThat(wifiOnly.asked).containsExactly(false)
    }

    /** The open source licences' row, after the pages', scrolled to. */
    private fun licencesRow() = compose.onNode(hasClickAction() and hasText(text(R.string.settings_licences)))
        .performScrollTo().fetchSemanticsNode()

    @Test
    fun theLastRowIsOneButtonThatOpensTheLicencesWhoseBackAndUpReturnToTheList() {
        val session = show()
        val row = licencesRow()
        assertThat(row.config.getOrNull(SemanticsProperties.Role)).isEqualTo(Role.Button)
        assertThat(row.config.getOrNull(SemanticsProperties.ContentDescription)).`as`("what its icon reads").isNull()
        assertThat(row.positionInRoot.y).isGreaterThan(row(SettingsPage.entries.last()).positionInRoot.y)

        click(R.string.settings_licences)
        compose.waitForIdle()

        compose.onNode(isHeading() and hasText(text(R.string.settings_licences))).assertIsFocused()
        assertThat(session.page.value).isNull()
        // the app's own assets, as the build made them: the app's licence comes first
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasClickAction() and hasText("Car charge planner (GPL-3.0-or-later)"))
                .fetchSemanticsNodes().isNotEmpty()
        }

        compose.activity.onBackPressedDispatcher.onBackPressed()
        compose.waitForIdle()
        compose.onNode(isHeading() and hasText(text(R.string.settings_title))).assertExists()
        assertThat(closed).isZero()

        click(R.string.settings_licences)
        compose.waitForIdle()
        up()
        compose.waitForIdle()
        compose.onNode(isHeading() and hasText(text(R.string.settings_title))).assertExists()
        assertThat(closed).isZero()
    }

    @Test
    fun theLicencesStayOpenAfterTheSystemEndedTheApp() {
        val restoration = StateRestorationTester(compose)
        val session = session()
        restoration.setContent {
            AppTheme {
                val page by session.page.collectAsState()
                SettingsScreen(session, page, OffWifiOnly)
            }
        }
        compose.waitForIdle()
        click(R.string.settings_licences)
        compose.waitForIdle()

        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        compose.onNode(isHeading() and hasText(text(R.string.settings_licences))).assertExists()
    }

    @Test
    fun eachRowDrawsAnIconInTheThemesColourBeforeItsName() {
        show()

        SettingsPage.entries.forEach { page ->
            // exact colour: a filled shape has opaque pixels inside, only its edges are blended with the ground
            assertThat(drawnBefore(page).count { Color(it) == onSurfaceVariant })
                .`as`("%s's icon in onSurfaceVariant", page).isPositive()
        }
    }

    /** The row of [page] in the list, scrolled to. */
    private fun row(page: SettingsPage) =
        compose.onNode(hasClickAction() and hasText(text(page.title()))).performScrollTo().fetchSemanticsNode()

    /** The pixels at the start of [page]'s row, up to its name. */
    private fun drawnBefore(page: SettingsPage): IntArray {
        val row = row(page)
        val name = compose.onNode(hasText(text(page.title())), useUnmergedTree = true).fetchSemanticsNode()
        val start = compose.picture(Rect(row.boundsInRoot.left, row.boundsInRoot.top, name.boundsInRoot.left,
            row.boundsInRoot.bottom))
        val pixels = IntArray(start.width * start.height)
        start.getPixels(pixels, 0, start.width, 0, 0, start.width, start.height)
        return pixels
    }

    @Test
    fun theGapBetweenTwoRowsIsOneDividerThick() {
        show()

        val rows = SettingsPage.entries.map { row(it) } + notificationsRow() + licencesRow()
        val divider = DividerDefaults.Thickness.value * rows.first().layoutInfo.density.density
        val gaps = rows.zipWithNext { above, below ->
            below.positionInRoot.y - (above.positionInRoot.y + above.size.height)
        }
        assertThat(gaps).allSatisfy { assertThat(it).isCloseTo(divider, within(0.5f)) }
    }

    @Test
    fun noDividerAboveTheFirstRowOrUnderTheLast() {
        // at twice the font size the list is longer than the screen, so where it ends shows in its scroll range
        show(fontScaleTwo = true)

        val list = compose.onNode(hasScrollAction()).fetchSemanticsNode()
        val first = compose.onNode(hasClickAction() and hasText(text(SettingsPage.entries.first().title())))
            .fetchSemanticsNode()
        val last = compose.onNode(hasClickAction() and hasText(text(R.string.settings_licences))).fetchSemanticsNode()
        // only the list's own padding, 8 dp, lies between a row and the list's edge
        val padding = 8f * list.layoutInfo.density.density
        assertThat(first.positionInRoot.y - list.positionInRoot.y).isCloseTo(padding, within(0.5f))
        val range = list.config[SemanticsProperties.VerticalScrollAxisRange]
        assertThat(range.maxValue()).isPositive()
        // the list's content, from its top: what it shows plus how far it scrolls
        val listEnd = list.size.height + range.maxValue()
        assertThat(last.positionInRoot.y + last.size.height + padding - list.positionInRoot.y)
            .isCloseTo(listEnd, within(0.5f))
    }

    /**
     * Checks that every row's values, with Location's at their longest (a long place name), show whole at twice the
     * font size: on every line they need, none cut short, within their row.
     *
     * @return the number of lines Location's values take
     */
    private fun assertEveryRowsValuesShowWhole(): Int {
        val name = PlaceName("Zonnedorpenzonneveldenzonnebergenzonnewoudenzonnemeerzonnehoeve, Zonnegouw, Zonneland")
        show(session(config = stored.withSystem(stored.system().withPlace(Coordinates(52.0907, 5.1214), amsterdam,
            name), stored.typical())), fontScaleTwo = true)
        return SettingsPage.entries.map { page ->
            val row = row(page)
            val summary = row.config[SemanticsProperties.Text].last().text
            val words = compose.onNode(hasText(summary), useUnmergedTree = true).fetchSemanticsNode()
            val layouts = mutableListOf<TextLayoutResult>()
            words.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
            val layout = layouts.single()
            assertThat(layout.multiParagraph.didExceedMaxLines).`as`("%s's values need more lines", page).isFalse()
            assertThat((0 until layout.lineCount).filter { layout.isLineEllipsized(it) }).`as`("%s's cut lines", page)
                .isEmpty()
            assertThat(row.boundsInRoot.bottom).`as`("the bottom of %s's row", page)
                .isGreaterThanOrEqualTo(words.boundsInRoot.bottom)
            layout.lineCount
        }[SettingsPage.LOCATION.ordinal]
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp")
    fun aRowsValuesWrapWithoutBeingCutAtTwiceTheFontSizeOnANarrowPhone() {
        assertThat(assertEveryRowsValuesShowWhole()).isGreaterThan(2)
    }

    @Test
    @Config(qualifiers = "nl-w320dp-h640dp")
    fun aRowsValuesWrapWithoutBeingCutAtTwiceTheFontSizeOnANarrowPhoneInDutch() {
        assertThat(assertEveryRowsValuesShowWhole()).isGreaterThan(2)
    }

    @Test
    @Config(qualifiers = "w640dp-h320dp-land")
    fun aRowsValuesWrapWithoutBeingCutAtTwiceTheFontSizeOnAPhoneOnItsSide() {
        assertThat(assertEveryRowsValuesShowWhole()).isGreaterThan(1)
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

        compose.onNodeWithText(text(R.string.saving)).assertIsNotEnabled()
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

        click(R.string.discard)
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
                SettingsScreen(session, page, OffWifiOnly)
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

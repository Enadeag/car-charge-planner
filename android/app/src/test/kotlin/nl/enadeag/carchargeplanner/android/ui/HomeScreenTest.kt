package nl.enadeag.carchargeplanner.android.ui

import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import nl.enadeag.carchargeplanner.android.R
import nl.enadeag.carchargeplanner.android.home.HomeOutlook
import nl.enadeag.carchargeplanner.android.home.Plans
import nl.enadeag.carchargeplanner.android.store.FakeEngineAccess
import nl.enadeag.carchargeplanner.android.ui.home.HomeScreen
import nl.enadeag.carchargeplanner.engine.Outlook
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The plain home on Robolectric, on a plan made up here. */
@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val access = FakeEngineAccess()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val amsterdam = ZoneId.of("Europe/Amsterdam")
    private val lisbon = ZoneId.of("Europe/Lisbon")
    private val today = LocalDate.of(2026, 6, 1)

    @After
    fun stop() {
        scope.cancel()
    }

    private fun clock(hours: String) {
        Settings.System.putString(compose.activity.contentResolver, Settings.System.TIME_12_24, hours)
    }

    private fun show(answer: Outlook, phoneZone: ZoneId = amsterdam, fontScaleTwo: Boolean = false): HomeOutlook {
        val outlook = HomeOutlook(access, scope) { }
        compose.setContent {
            AppTheme {
                if (fontScaleTwo) AtFontScaleTwo { HomeScreen(outlook, phoneZone) } else HomeScreen(outlook, phoneZone)
            }
        }
        compose.waitForIdle()
        access.outlooks.last().complete(answer)
        compose.waitForIdle()
        return outlook
    }

    private fun lisbonAfternoon() =
        Plans.planned(today.atTime(9, 0).atZone(lisbon), listOf(listOf(Plans.window(lisbon, today, 13, 15))))

    @Test
    fun todaysWindowsShowInTheHomesZoneOnA24HourClockWhichIsNamedWhenNotThePhones() {
        clock("24")

        show(lisbonAfternoon())

        compose.onNode(isHeading() and hasText(text(R.string.home_title))).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.home_window, "13:00", "15:00", 6, "1.4")).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.home_zone, "Europe/Lisbon")).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.home_assumed, "40")).assertIsDisplayed()
    }

    @Test
    fun onA12HourClockTheTimesFollowIt() {
        clock("12")

        show(lisbonAfternoon(), phoneZone = lisbon)

        val shown = compose.shownTexts()
        assertThat(shown).noneMatch { it.contains("13:00") }.anyMatch { it.contains("1:00") }
        assertThat(shown).doesNotContain(text(R.string.home_zone, "Europe/Lisbon"))
    }

    @Test
    fun withoutAWindowTodayTheNextOneShowsWithItsDay() {
        clock("24")
        val tuesday = today.plusDays(1)

        show(Plans.planned(today.atTime(9, 0).atZone(amsterdam),
            listOf(listOf(), listOf(Plans.window(amsterdam, tuesday, 11, 12)))))

        compose.onNodeWithText(text(R.string.home_next_window, "Tuesday", "11:00", "12:00", 6, "1.4"))
            .assertIsDisplayed()
    }

    @Test
    fun withoutAnyWindowTheHomeSaysSo() {
        show(Plans.planned(today.atTime(9, 0).atZone(amsterdam), listOf(listOf())))

        compose.onNodeWithText(text(R.string.home_no_window)).assertIsDisplayed()
    }

    @Test
    fun noForecastShowsItsEnglishDetailsAndTryAgainAsksAgain() {
        show(Outlook.NoForecast("Open-Meteo: no network"))

        compose.onNodeWithText(text(R.string.home_no_forecast)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.details_in_english, "Open-Meteo: no network")).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.action_try_again)).performScrollTo().performClick()
        compose.waitForIdle()

        assertThat(access.outlooks).hasSize(2)
    }

    @Test
    fun settingsIsShownButOffWithItsReason() {
        show(lisbonAfternoon())

        compose.onNodeWithText(text(R.string.home_settings)).assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,
                text(R.string.home_settings_later)))
    }

    @Test
    @Config(qualifiers = "nl")
    fun theHomeIsInDutch() {
        clock("24")
        show(lisbonAfternoon())

        compose.onNodeWithText("Vandaag").assertIsDisplayed()
        compose.assertShowsNoneOf(englishOnlyTexts())
    }

    @Test
    fun theHomeScrollsAndItsControlsAreNamedAndLargeEnoughAtTwiceTheFontSize() {
        show(lisbonAfternoon(), fontScaleTwo = true)

        compose.assertScrolls()
        compose.assertEveryControlIsNamedAndLargeEnough()
        compose.onNodeWithText(text(R.string.home_refresh)).performScrollTo().performClick()
    }
}

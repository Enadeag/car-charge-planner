package nl.enadeag.carchargeplanner.android

import android.app.NotificationManager
import android.os.Build
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import java.time.ZoneId
import nl.enadeag.carchargeplanner.android.home.Plans
import nl.enadeag.carchargeplanner.android.store.FakeEngineAccess
import nl.enadeag.carchargeplanner.android.store.Saves
import nl.enadeag.carchargeplanner.android.store.StartState
import nl.enadeag.carchargeplanner.android.ui.NOT_READ_APART
import nl.enadeag.carchargeplanner.android.ui.text
import nl.enadeag.carchargeplanner.engine.config.EngineConfig
import nl.enadeag.carchargeplanner.engine.config.ProviderId
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.util.ReflectionHelpers

/**
 * The app's activity on a phone before Android 13, which has no permission to ask for notifications, with its
 * notifications blocked and an engine in memory. The tests run on one SDK only, so the phone's API level is set for
 * the app's own checks; Robolectric's Android stays the newer one.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityBeforeAndroid13Test {

    private val zone = ZoneId.of("Europe/Amsterdam")
    private val access = FakeEngineAccess(StartState.Ready(EngineConfig.setUpDraft(zone, ProviderId("open-meteo"))))
    private val app = ApplicationProvider.getApplicationContext<CarChargePlannerApp>()

    /** Makes the phone Android 12L with its notifications blocked, and puts the engine in memory into the app. */
    @get:Rule(order = 0)
    val olderPhone = object : ExternalResource() {
        private var sdk = 0

        override fun before() {
            app.engine = access
            app.saves = Saves(access, app.scope)
            shadowOf(app.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(false)
            sdk = Build.VERSION.SDK_INT
            ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", Build.VERSION_CODES.S_V2)
        }

        override fun after() {
            ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", sdk)
        }
    }

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun theHomesCardLeadsToTheNotificationSettingsAtOnceAsAndroidCannotBeAsked() {
        compose.waitUntil(10_000) { access.outlooks.isNotEmpty() }
        access.outlooks.single().complete(Plans.planned(LocalDate.of(2026, 6, 1).atTime(9, 0).atZone(zone),
            listOf(listOf(Plans.window(zone, LocalDate.of(2026, 6, 1), 13, 15)))))
        compose.waitForIdle()
        compose.onNode(hasText(text(R.string.day_window)) and !NOT_READ_APART).assertExists()

        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(text(R.string.notification_card_title)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(text(R.string.notification_card_no_ask)).assertExists()
        compose.onNodeWithText(text(R.string.notification_card_open_settings)).assertExists()
        compose.onNodeWithText(text(R.string.notification_card_ask)).assertDoesNotExist()
    }
}

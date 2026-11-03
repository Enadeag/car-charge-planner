package nl.enadeag.carchargeplanner.android.ui

import android.content.Context
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The app's colours: the wallpaper's from Android 12 on, Material's fixed ones before. */
@RunWith(AndroidJUnit4::class)
class AppThemeTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun onAndroid12AndLaterTheAppTakesTheWallpapersColours() {
        assertThat(dynamicLightColorScheme(context).primary).isNotEqualTo(lightColorScheme().primary)
        assertThat(appColorScheme(Build.VERSION_CODES.S, false, context).primary)
            .isEqualTo(dynamicLightColorScheme(context).primary)
        assertThat(appColorScheme(Build.VERSION_CODES.S, true, context).primary)
            .isEqualTo(dynamicDarkColorScheme(context).primary)
    }

    @Test
    fun onAndroid10And11TheAppHasMaterialsFixedColours() {
        assertThat(appColorScheme(Build.VERSION_CODES.R, false, context).primary)
            .isEqualTo(lightColorScheme().primary)
        assertThat(appColorScheme(Build.VERSION_CODES.Q, true, context).primary)
            .isEqualTo(darkColorScheme().primary)
    }

    @Test
    fun everyScreenOnThisPhoneHasTheWallpapersColoursAndTheLightSourceColours() {
        lateinit var shown: ColorScheme
        lateinit var sources: SourceColours
        compose.setContent {
            AppTheme {
                shown = MaterialTheme.colorScheme
                sources = LocalSourceColours.current
            }
        }
        compose.waitForIdle()

        assertThat(shown.primary).isEqualTo(dynamicLightColorScheme(context).primary)
        assertThat(sources).isEqualTo(SourceColours.LIGHT)
    }

    @Test
    @Config(qualifiers = "night")
    fun inTheDarkEveryScreenHasTheDarkColours() {
        lateinit var shown: ColorScheme
        lateinit var sources: SourceColours
        compose.setContent {
            AppTheme {
                shown = MaterialTheme.colorScheme
                sources = LocalSourceColours.current
            }
        }
        compose.waitForIdle()

        assertThat(shown.primary).isEqualTo(dynamicDarkColorScheme(context).primary)
        assertThat(sources).isEqualTo(SourceColours.DARK)
    }
}

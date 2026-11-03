package nl.enadeag.carchargeplanner.android.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Material 3 in the system's light or dark, for every screen of the app: with the wallpaper's dynamic colours from
 * Android 12 on, and Material's own fixed colours on Android 10 and 11, which have none. The colours of the energy
 * sources stay the same everywhere ([SourceColours]).
 *
 * @param content what the theme applies to
 */
@Composable
internal fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = remember(dark, context) { appColorScheme(Build.VERSION.SDK_INT, dark, context) }
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalSourceColours provides SourceColours.of(dark), content = content)
    }
}

/**
 * The app's colours on an Android version.
 *
 * @param sdk the phone's API level
 * @param dark whether the system is dark
 * @param context where the wallpaper's colours are read from, on Android 12 and later
 * @return the wallpaper's dynamic colours on API 31 and later, else Material's fixed ones
 */
// lint cannot see that sdk is the phone's API level, which the first two branches check
@SuppressLint("NewApi")
internal fun appColorScheme(sdk: Int, dark: Boolean, context: Context): ColorScheme = when {
    sdk >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
    sdk >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
    dark -> darkColorScheme()
    else -> lightColorScheme()
}

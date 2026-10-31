package nl.enadeag.carchargeplanner.android

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Material 3 in the system's light or dark, without the wallpaper's dynamic colours, so the app looks the same on
 * every phone.
 *
 * @param content what the theme applies to
 */
@Composable
internal fun SkeletonTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}

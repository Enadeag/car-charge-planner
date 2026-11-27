package nl.enadeag.carchargeplanner.android.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The screens' own checks, on Robolectric: they fail where they should. */
@RunWith(AndroidJUnit4::class)
class ScreenChecksTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun aControlDrawnAt40DpFailsThoughMaterialPadsItsTouchAreaTo48() {
        compose.setContent {
            AppTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    // Material's own icon button, drawn at 40 dp with 48 dp to touch
                    IconButton(onClick = { }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            }
        }

        assertThatThrownBy { compose.assertEveryControlIsNamedAndLargeEnough() }
            .isInstanceOf(AssertionError::class.java).hasMessageContaining("the size of Settings")
    }

    @Test
    fun aRowsControlIsTheFirstOfThoseThatMatchBesideOrBelowItsLine() {
        var pressed = ""
        compose.setContent {
            AppTheme {
                Column {
                    listOf("Row 1", "Row 2").forEach { row ->
                        Text(row)
                        TextButton(onClick = { pressed = row }) { Text("Remove") }
                    }
                }
            }
        }

        compose.controlFor("Row 2", hasText("Remove") and hasClickAction()).performClick()

        assertThat(pressed).isEqualTo("Row 2")
        compose.controlFor("Row 3", hasText("Remove") and hasClickAction()).assertDoesNotExist()
    }
}

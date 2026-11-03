package nl.enadeag.carchargeplanner.android.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.assertj.core.api.Assertions.assertThatCode
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

    private fun show(content: @Composable () -> Unit) {
        compose.setContent { AppTheme { Column(Modifier.verticalScroll(rememberScrollState())) { content() } } }
    }

    @Composable
    private fun Drawing(said: String, role: Role?, inside: @Composable () -> Unit = {}) {
        Box(Modifier.size(40.dp).semantics {
            contentDescription = said
            if (role != null) {
                this.role = role
            }
        }) { inside() }
    }

    @Test
    fun aDrawingReadAsOneWithItsSentenceAndRolePasses() {
        show { Drawing("Chart", Role.Image) }

        assertThatCode { compose.assertReadAsOne("Chart", Role.Image) }.doesNotThrowAnyException()
    }

    @Test
    fun aDrawingWithoutARoleOrWithAnotherFails() {
        show {
            Drawing("Without", null)
            Drawing("Button", Role.Button)
        }

        assertThatThrownBy { compose.assertReadAsOne("Without", Role.Image) }
            .isInstanceOf(AssertionError::class.java).hasMessageContaining("the role of Without")
        assertThatThrownBy { compose.assertReadAsOne("Button", Role.Image) }
            .isInstanceOf(AssertionError::class.java).hasMessageContaining("the role of Button")
    }

    @Test
    fun twoPartsThatSayTheSameFail() {
        show {
            Drawing("Chart", Role.Image)
            Drawing("Chart", Role.Image)
        }

        assertThatThrownBy { compose.assertReadAsOne("Chart", Role.Image) }
            .isInstanceOf(AssertionError::class.java).hasMessageContaining("the parts that say Chart")
    }

    @Test
    fun aDrawingWithAPartInsideItFails() {
        show { Drawing("Chart", Role.Image) { Text("09:00") } }

        assertThatThrownBy { compose.assertReadAsOne("Chart", Role.Image) }
            .isInstanceOf(AssertionError::class.java).hasMessageContaining("what is inside Chart")
    }

    @Test
    fun aTextShownButNotReadApartPasses() {
        show { Text("Legend", Modifier.semantics { hideFromAccessibility() }) }

        assertThatCode { compose.assertShownButNotReadApart("Legend") }.doesNotThrowAnyException()
    }

    @Test
    fun aTextThatIsReadFails() {
        show { Text("Legend") }

        assertThatThrownBy { compose.assertShownButNotReadApart("Legend") }.isInstanceOf(AssertionError::class.java)
    }

    @Test
    fun aTextNotReadApartThatAnotherPartShowsToBeReadFails() {
        show {
            Text("Legend", Modifier.semantics { hideFromAccessibility() })
            Text("Legend")
        }

        assertThatThrownBy { compose.assertShownButNotReadApart("Legend") }
            .isInstanceOf(AssertionError::class.java).hasMessageContaining("the parts that show Legend to be read")
    }

    @Test
    fun aTextNotReadApartThatCannotBeScrolledToFails() {
        compose.setContent { AppTheme { Text("Legend", Modifier.semantics { hideFromAccessibility() }) } }

        assertThatThrownBy { compose.assertShownButNotReadApart("Legend") }.isInstanceOf(AssertionError::class.java)
    }
}

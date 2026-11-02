package nl.enadeag.carchargeplanner.android.ui

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import java.util.Locale
import nl.enadeag.carchargeplanner.android.R
import org.assertj.core.api.Assertions.assertThat

/**
 * A text of the app in the language the test runs in.
 *
 * @param id the text
 * @param args what its places hold
 * @return the text as the screen shows it
 */
internal fun text(@StringRes id: Int, vararg args: Any): String =
    ApplicationProvider.getApplicationContext<Context>().getString(id, *args)

/**
 * The app's English texts that the language the test runs in writes otherwise, so a screen in that language shows
 * none of them unless it has a text written into the code.
 *
 * @return those English texts, without their places
 */
internal fun englishOnlyTexts(): Set<String> {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val english = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
        setLocale(Locale.ENGLISH)
    })
    return R.string::class.java.fields.map { it.getInt(null) }
        .map { english.getString(it) to context.getString(it) }
        .filter { (en, here) -> en != here && !en.contains('%') }
        .map { it.first }.toSet()
}

/**
 * Every text the screen shows or gives a screen reader, from every node.
 *
 * @return the texts, labels, descriptions and states
 */
internal fun SemanticsNodeInteractionsProvider.shownTexts(): List<String> =
    onAllNodes(androidx.compose.ui.test.SemanticsMatcher("any node") { true }, useUnmergedTree = true)
        .fetchSemanticsNodes().flatMap { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
                node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
                listOfNotNull(node.config.getOrNull(SemanticsProperties.StateDescription),
                    node.config.getOrNull(SemanticsProperties.EditableText)?.text)
        }

/**
 * Checks what a screen reader needs of every control on the screen: each one that can be clicked has a name and a
 * role, each field has a label, and each is drawn at least 48 dp in both directions. A control on a page that scrolls
 * is scrolled to first, so one that cannot be reached fails.
 */
internal fun SemanticsNodeInteractionsProvider.assertEveryControlIsNamedAndLargeEnough() {
    val controls = onAllNodes(hasClickAction())
    val count = controls.fetchSemanticsNodes().size
    assertThat(count).isPositive()
    (0 until count).forEach { index ->
        // a dialog's buttons have no page to scroll and are on screen already
        if (ON_A_SCROLLING_PAGE.matches(controls[index].fetchSemanticsNode())) {
            controls[index].performScrollTo().assertIsDisplayed()
        }
        val node = controls[index].fetchSemanticsNode()
        assertThat(nameOf(node)).`as`("the name of %s", node.config).isNotBlank()
        val editable = node.config.contains(SemanticsActions.SetText)
        if (!editable) {
            assertThat(node.config.getOrNull(SemanticsProperties.Role)).`as`("the role of %s", nameOf(node)).isNotNull()
        }
        // the size it is drawn at: Compose widens where any clickable node can be touched to 48 dp on its own
        val dp = node.layoutInfo.density.density
        assertThat(minOf(node.size.width, node.size.height) / dp).`as`("the size of %s", nameOf(node))
            .isGreaterThanOrEqualTo(47.5f)
    }
}

/**
 * Checks that the screen's page scrolls far enough to show its last control whole, so nothing is out of reach at a
 * large font size.
 */
internal fun SemanticsNodeInteractionsProvider.assertScrolls() {
    val page = onNode(hasScrollAction() and hasAnyDescendant(hasClickAction()))
    val controls = onAllNodes(hasClickAction() and ON_A_SCROLLING_PAGE)
    val nodes = controls.fetchSemanticsNodes()
    assertThat(nodes).isNotEmpty()
    val last = nodes.indices.maxBy { nodes[it].boundsInRoot.bottom }

    controls[last].performScrollTo()

    val shown = page.fetchSemanticsNode().boundsInRoot
    val control = controls[last].fetchSemanticsNode().boundsInRoot
    assertThat(control.top).`as`("the top of %s", nameOf(nodes[last])).isGreaterThanOrEqualTo(shown.top - 0.5f)
    assertThat(control.bottom).`as`("the bottom of %s", nameOf(nodes[last])).isLessThanOrEqualTo(shown.bottom + 0.5f)
}

/**
 * Checks that the screen shows none of these texts.
 *
 * @param texts the texts it must not show, such as the English ones on a Dutch screen
 */
internal fun SemanticsNodeInteractionsProvider.assertShowsNoneOf(texts: Set<String>) {
    val shown = shownTexts()
    assertThat(shown).isNotEmpty()
    assertThat(shown.filter { it in texts }).isEmpty()
}

private val ON_A_SCROLLING_PAGE = hasAnyAncestor(hasScrollAction())

private fun nameOf(node: SemanticsNode): String =
    (node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }).joinToString(" ")

/**
 * The content at twice the normal font size, as Android's largest font setting gives it.
 *
 * @param content the screen
 */
@Composable
internal fun AtFontScaleTwo(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f),
        content = content)
}

package nl.enadeag.carchargeplanner.android.ui

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.text.input.ImeAction
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
 * Every text the screen shows or names a control with, from every node.
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
 * Checks every control on the screen: each one that can be clicked has a name, its words or an icon's description,
 * and a role, each field has a label, and each is drawn at least 48 dp in both directions, not only touched there. A
 * control on a page that scrolls is scrolled to first, so one that cannot be reached fails.
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
        val dp = node.layoutInfo.density.density
        // the node's own size, which is what is drawn: Material widens a control drawn smaller to 48 dp of touch
        // area outside it (minimumInteractiveComponentSize), which ScreenChecksTest shows this does not count
        val drawn = node.size
        assertThat(minOf(drawn.width, drawn.height) / dp).`as`("the size of %s", nameOf(node))
            .isGreaterThanOrEqualTo(47.5f)
    }
}

/**
 * Checks that the screen's page scrolls far enough to show its last control whole, so nothing is out of reach at a
 * large font size; the page has room of its own, as a page squeezed to nothing by what is pinned above it would show
 * nothing and pass the rest.
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
    assertThat(shown.height).`as`("the height of the page that scrolls").isPositive()
    assertThat(control.width).`as`("the width of %s", nameOf(nodes[last])).isPositive()
    assertThat(control.height).`as`("the height of %s", nameOf(nodes[last])).isPositive()
    assertThat(control.top).`as`("the top of %s", nameOf(nodes[last])).isGreaterThanOrEqualTo(shown.top - 0.5f)
    assertThat(control.bottom).`as`("the bottom of %s", nameOf(nodes[last])).isLessThanOrEqualTo(shown.bottom + 0.5f)
}

/**
 * Checks that the page that scrolls up and down, the one shown of a pager's, has room of its own and scrolls far
 * enough to show this part of it whole, such as the last line of a page without a control at its end.
 *
 * @param last what the page shows last
 */
internal fun SemanticsNodeInteractionsProvider.assertScrollsToShow(last: SemanticsMatcher) {
    val part = onNode(last and hasAnyAncestor(SCROLLS_UP_AND_DOWN), useUnmergedTree = true)

    part.performScrollTo()

    val page = onNode(SCROLLS_UP_AND_DOWN and hasAnyDescendant(last), useUnmergedTree = true)
        .fetchSemanticsNode().boundsInRoot
    val shown = part.fetchSemanticsNode().boundsInRoot
    assertThat(page.height).`as`("the height of the page that scrolls").isPositive()
    assertThat(shown.height).`as`("the height of the last part").isPositive()
    assertThat(shown.top).`as`("the top of the last part").isGreaterThanOrEqualTo(page.top - 0.5f)
    assertThat(shown.bottom).`as`("the bottom of the last part").isLessThanOrEqualTo(page.bottom + 0.5f)
}

private val SCROLLS_UP_AND_DOWN = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)

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

/**
 * Checks the keyboard's action key of every field on the screen, from the top: Done on the last, so the keyboard
 * closes, Next on each other. A field whose key does something of its own, such as the place search's Search, is
 * left out.
 */
internal fun SemanticsNodeInteractionsProvider.assertOnlyTheLastFieldIsDone() {
    val actions = onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
        .map { (it.config.getOrNull(SemanticsProperties.ImeAction) ?: ImeAction.Default) to nameOf(it) }
        .filter { (action, _) -> action != ImeAction.Search }
    assertThat(actions).isNotEmpty()
    actions.forEachIndexed { index, (action, name) ->
        val expected = if (index == actions.lastIndex) ImeAction.Done else ImeAction.Next
        assertThat(action).`as`("the keyboard's action on %s", name).isEqualTo(expected)
    }
}

private val ON_A_SCROLLING_PAGE = hasAnyAncestor(hasScrollAction())

private fun nameOf(node: SemanticsNode): String =
    (node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }).joinToString(" ")

/** The place of each day's tab, from a plan made on Monday 1 June 2026, as the tests' plans are. */
private val TAB_PLACES = mapOf("Today" to 0, "Vandaag" to 0, "Tomorrow" to 1, "Morgen" to 1, "Wednesday" to 2,
    "Thursday" to 3)

/**
 * The dashboard's tab of a day, which shows its short name, such as "Tue".
 *
 * @param day the day's whole name, such as "Tomorrow" or "Wednesday"
 */
internal fun SemanticsNodeInteractionsProvider.dayTab(day: String): SemanticsNodeInteraction =
    onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))[TAB_PLACES.getValue(day)]

/**
 * A row's control, such as a trip's Remove or one of its choices: of those that match, the first beside or below the
 * row's line, outside any dialog. Matches nothing when the line is not shown.
 *
 * @param line the row's line, whole, such as a trip as the Trips card lists it
 * @param control which controls to look among
 */
internal fun SemanticsNodeInteractionsProvider.controlFor(line: String, control: SemanticsMatcher):
    SemanticsNodeInteraction {
    val shown = onAllNodes(hasText(line) and !hasAnyAncestor(isDialog())).fetchSemanticsNodes().singleOrNull()
    // where each is laid out on the page, also out of view, where its bounds in the root are clipped to nothing
    val middle = { node: SemanticsNode -> node.positionInRoot.y + node.size.height / 2f }
    val id = shown?.let { row ->
        onAllNodes(control and !hasAnyAncestor(isDialog())).fetchSemanticsNodes()
            .filter { middle(it) >= row.positionInRoot.y }.minByOrNull(middle)?.id
    }
    return onNode(SemanticsMatcher("$control of $line") { it.id == id })
}

/** Not a tab of the dashboard's days, nor inside one. */
private val NOT_A_TAB = !SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab) and
    !hasAnyAncestor(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))

/**
 * The title of a day's card, apart from its tab, which may show the same word.
 *
 * @param day the day's whole name, such as "Today"
 */
internal fun dayTitle(day: String): SemanticsMatcher = hasText(day) and NOT_A_TAB

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

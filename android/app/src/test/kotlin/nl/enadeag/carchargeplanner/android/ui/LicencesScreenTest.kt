package nl.enadeag.carchargeplanner.android.ui

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.FileNotFoundException
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import nl.enadeag.carchargeplanner.android.R
import nl.enadeag.carchargeplanner.android.licences.LicenceFiles
import nl.enadeag.carchargeplanner.android.ui.settings.LicencesScreen
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The screen of the open source licences on Robolectric, on a small fixture of texts instead of the app's assets. */
@RunWith(AndroidJUnit4::class)
class LicencesScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val gpl = "GNU GENERAL PUBLIC LICENSE\nVersion 3, 29 June 2007\n\nEveryone is permitted to copy and " +
        "distribute verbatim copies of this license document, but changing it is not allowed.\n"

    /** An indented paragraph that takes many lines on a narrow phone at twice the font size. */
    private val apache = "Apache License 2.0\nArtifact: androidx.compose.material3:material3-android\n\n" +
        "   TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION " + "and so on ".repeat(30) + "\n"

    private val fixture = mapOf(
        "index.txt" to "copyright\tCopyright (C) 2026 Someone\n" +
            "part\tLICENSE\tCar charge planner (GPL-3.0-or-later)\n" +
            "part\tapache-2.0.txt\tApache License 2.0\n" +
            "part\tgone.txt\tGone\n" +
            "part\tparagraphs.txt\tParagraphs\n",
        "LICENSE" to gpl,
        "apache-2.0.txt" to apache,
        "paragraphs.txt" to "\n\nA  \n  \nB\n\n\nC\n\n",
    )

    /** The threads the texts were read on. */
    private val readOn = Collections.synchronizedList(mutableListOf<Thread>())

    private val files = LicenceFiles { name ->
        readOn += Thread.currentThread()
        fixture[name] ?: throw FileNotFoundException(name)
    }

    private var closed = 0

    /** Holds a read until the test lets go; a read on the main thread, which is the test's, fails after 5 seconds. */
    private fun CountDownLatch.holdRead() =
        check(await(5, TimeUnit.SECONDS)) { "the read waits for the test, so it runs on the main thread" }

    private fun show(files: LicenceFiles = this.files, fontScaleTwo: Boolean = false) {
        compose.setContent {
            AppTheme {
                if (fontScaleTwo) {
                    AtFontScaleTwo { LicencesScreen(files) { closed++ } }
                } else {
                    LicencesScreen(files) { closed++ }
                }
            }
        }
        waitFor("Copyright (C) 2026 Someone")
    }

    /** Waits until the screen shows a text that starts so, as the texts are read off the main thread. */
    private fun waitFor(start: String) {
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText(start, substring = true), useUnmergedTree = true).fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun row(title: String) = compose.onNode(hasClickAction() and hasText(title))

    /** Clicks a part's row, scrolled to first, as at a large font it may be below the screen. */
    private fun click(title: String) = row(title).performScrollTo().performClick()

    /** How many nodes the screen reader reads as one live region that holds [shown], as the merged tree shows. */
    private fun SemanticsNodeInteractionsProvider.mergedLiveRegions(shown: String) =
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion) and hasText(shown))
            .fetchSemanticsNodes().size

    /** The colour a text is drawn in, from its layout. */
    private fun colourOf(shown: String): Color {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNode(hasText(shown), useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        return layouts.single().layoutInput.style.color
    }

    private fun stateOf(title: String) =
        row(title).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)

    @Test
    fun theTitleIsTheHeadingAndTheAppItsCopyrightAndWhereItsSourceIsComeFirst() {
        show()

        compose.onNode(isHeading() and hasText(text(R.string.settings_licences))).assertExists()
        compose.onNode(isHeading() and hasText(text(R.string.app_name))).assertExists()
        compose.onNodeWithText("Copyright (C) 2026 Someone").assertExists()
        compose.onNodeWithText(text(R.string.licences_source, "github.com/Enadeag/car-charge-planner")).assertExists()
        assertThat(text(R.string.licences_source, "x")).isEqualTo("Source code: x")
        // plain text, not a link
        compose.onNode(hasText("github.com", substring = true) and hasClickAction()).assertDoesNotExist()
    }

    @Test
    fun eachPartIsARowByItsTitleInTheIndexsOrderAndNoTextIsDrawnUntilARowIsExpanded() {
        show()

        val rows = listOf("Car charge planner (GPL-3.0-or-later)", "Apache License 2.0", "Gone").map {
            row(it).fetchSemanticsNode()
        }
        assertThat(rows.map { it.positionInRoot.y }).isSorted()
        rows.forEach { assertThat(it.config.getOrNull(SemanticsProperties.Role)).isEqualTo(Role.Button) }
        compose.onNode(hasText("GNU GENERAL PUBLIC LICENSE", substring = true)).assertDoesNotExist()
        compose.onNode(hasText("TERMS AND CONDITIONS", substring = true)).assertDoesNotExist()
        assertThat(readOn).hasSize(1)
    }

    @Test
    fun theGapBetweenTwoRowsIsOneDividerThick() {
        show()

        val rows = listOf("Car charge planner (GPL-3.0-or-later)", "Apache License 2.0", "Gone").map {
            row(it).fetchSemanticsNode()
        }
        val divider = DividerDefaults.Thickness.value * rows.first().layoutInfo.density.density
        val gaps = rows.zipWithNext { above, below ->
            below.positionInRoot.y - (above.positionInRoot.y + above.size.height)
        }
        assertThat(gaps).allSatisfy { assertThat(it).isCloseTo(divider, within(0.5f)) }
    }

    @Test
    fun whileTheListIsReadTheScreenSaysSoInAPoliteLiveRegion() {
        val gate = CountDownLatch(1)
        try {
            compose.setContent {
                AppTheme {
                    LicencesScreen({ gate.holdRead(); files.read(it) }) { closed++ }
                }
            }
            val reading = text(R.string.licences_reading)

            compose.onNodeWithText(reading).assertExists()
            compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertExists()
            assertThat(compose.liveRegionOf(reading)).isEqualTo(LiveRegionMode.Polite)
            assertThat(compose.mergedLiveRegions(reading)).isEqualTo(1)
            gate.countDown()
            waitFor("Copyright (C) 2026 Someone")
            compose.onNodeWithText(reading).assertDoesNotExist()
        } finally {
            gate.countDown()
        }
    }

    @Test
    fun whileATextIsReadItsPlaceSaysSoInAPoliteLiveRegion() {
        val gate = CountDownLatch(1)
        try {
            show(files = { name -> if (name != "index.txt") gate.holdRead(); files.read(name) })
            click("Apache License 2.0")
            val reading = text(R.string.licences_reading)

            assertThat(compose.liveRegionOf(reading)).isEqualTo(LiveRegionMode.Polite)
            assertThat(compose.mergedLiveRegions(reading)).isEqualTo(1)
            gate.countDown()
            waitFor("TERMS AND CONDITIONS")
            compose.onNodeWithText(reading).assertDoesNotExist()
        } finally {
            gate.countDown()
        }
    }

    @Test
    fun aRowExpandsAndCollapsesItsTextAndSaysWhichItIs() {
        show()
        val title = "Car charge planner (GPL-3.0-or-later)"
        assertThat(stateOf(title)).isEqualTo(text(R.string.licences_collapsed))
        assertThat(row(title).fetchSemanticsNode().config.contains(SemanticsActions.Expand)).isTrue()

        click(title)
        waitFor("GNU GENERAL PUBLIC LICENSE")

        assertThat(stateOf(title)).isEqualTo(text(R.string.licences_expanded))
        assertThat(row(title).fetchSemanticsNode().config.contains(SemanticsActions.Collapse)).isTrue()
        compose.onNode(hasText("Everyone is permitted", substring = true)).assertExists()
        compose.onNode(hasText("TERMS AND CONDITIONS", substring = true)).assertDoesNotExist()

        click(title)
        compose.waitForIdle()

        assertThat(stateOf(title)).isEqualTo(text(R.string.licences_collapsed))
        compose.onNode(hasText("GNU GENERAL PUBLIC LICENSE", substring = true)).assertDoesNotExist()
    }

    @Test
    fun theTextsAreReadOffTheMainThread() {
        show()
        click("Apache License 2.0")
        waitFor("TERMS AND CONDITIONS")

        assertThat(readOn).hasSize(2).allSatisfy { assertThat(it).isNotEqualTo(Looper.getMainLooper().thread) }
    }

    @Test
    fun aTextThatIsMissingSaysItCouldNotBeReadInTheErrorColourInAPoliteLiveRegion() {
        var error = Color.Unspecified
        compose.setContent { AppTheme { error = MaterialTheme.colorScheme.error; LicencesScreen(files) { closed++ } } }
        waitFor("Copyright (C) 2026 Someone")
        click("Gone")
        waitFor(text(R.string.licences_not_read))

        assertThat(compose.liveRegionOf(text(R.string.licences_not_read))).isEqualTo(LiveRegionMode.Polite)
        assertThat(compose.mergedLiveRegions(text(R.string.licences_not_read))).isEqualTo(1)
        assertThat(colourOf(text(R.string.licences_not_read))).isEqualTo(error)
        assertThat(error).isNotEqualTo(colourOf("Copyright (C) 2026 Someone"))
    }

    @Test
    fun aTextIsDrawnAsOneNodePerParagraphWithoutBlankOnesOrTrailingSpaces() {
        show()
        click("Paragraphs")
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("C"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        val paragraphs = compose.onAllNodes(hasText("A") or hasText("B") or hasText("C"), useUnmergedTree = true)
        assertThat(paragraphs.fetchSemanticsNodes().map { it.config[SemanticsProperties.Text].single().text })
            .containsExactly("A", "B", "C")
        compose.onAllNodes(hasText(""), useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun anIndexThatIsMissingSaysThatTheLicencesCouldNotBeRead() {
        compose.setContent { AppTheme { LicencesScreen({ throw FileNotFoundException(it) }) { closed++ } } }
        waitFor(text(R.string.licences_list_not_read))

        compose.onNodeWithText(text(R.string.licences_source, "github.com/Enadeag/car-charge-planner")).assertExists()
        compose.onNode(hasClickAction() and hasText("Gone")).assertDoesNotExist()
    }

    @Test
    fun backAndUpClose() {
        show()

        compose.activity.onBackPressedDispatcher.onBackPressed()
        compose.onNodeWithContentDescription(text(R.string.action_up)).performClick()

        assertThat(closed).isEqualTo(2)
    }

    @Test
    fun anExpandedTextStaysExpandedAfterTurningThePhone() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { AppTheme { LicencesScreen(files) { closed++ } } }
        waitFor("Copyright (C) 2026 Someone")
        click("Apache License 2.0")
        waitFor("TERMS AND CONDITIONS")

        restoration.emulateSavedInstanceStateRestore()
        waitFor("TERMS AND CONDITIONS")

        assertThat(stateOf("Apache License 2.0")).isEqualTo(text(R.string.licences_expanded))
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp")
    fun atTwiceTheFontSizeOnANarrowPhoneNothingIsCutAndEveryRowIsLargeEnough() {
        show(fontScaleTwo = true)
        click("Apache License 2.0")
        waitFor("TERMS AND CONDITIONS")
        click("Car charge planner (GPL-3.0-or-later)")
        waitFor("GNU GENERAL PUBLIC LICENSE")

        compose.assertScrolls()
        compose.assertEveryControlIsNamedAndLargeEnough()
        val screen = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .fetchSemanticsNode().boundsInRoot
        val texts = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult) and
            hasAnyAncestor(hasScrollAction()), useUnmergedTree = true).fetchSemanticsNodes().map { it.id }
        assertThat(texts).hasSizeGreaterThan(6)
        texts.forEach { id ->
            val node = compose.onNode(SemanticsMatcher("node $id") { it.id == id }, useUnmergedTree = true)
                .performScrollTo().fetchSemanticsNode()
            val shown = node.config[SemanticsProperties.Text].joinToString { it.text }
            val layouts = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
            val layout = layouts.single()
            assertThat(layout.multiParagraph.didExceedMaxLines).`as`("%s needs more lines", shown).isFalse()
            assertThat((0 until layout.lineCount).filter { layout.isLineEllipsized(it) }).`as`("%s's cut lines", shown)
                .isEmpty()
            // the Text is as wide as its longest line: a line wider than the Text would be cut
            assertThat((0 until layout.lineCount).maxOf { layout.getLineRight(it) }).`as`("the longest line of %s",
                shown).isLessThanOrEqualTo(node.size.width + 0.5f)
            assertThat(node.size.height.toFloat()).`as`("the height of %s", shown)
                .isGreaterThanOrEqualTo(layout.multiParagraph.height - 0.5f)
            assertThat(node.boundsInRoot.right).`as`("the end of %s", shown).isLessThanOrEqualTo(screen.right + 0.5f)
        }
    }

    @Test
    @Config(qualifiers = "nl")
    fun theScreensOwnTextsAreInDutch() {
        show()
        click("Apache License 2.0")
        waitFor("TERMS AND CONDITIONS")

        compose.onNodeWithText("Broncode: github.com/Enadeag/car-charge-planner").assertExists()
        assertThat(stateOf("Apache License 2.0")).isEqualTo("Uitgevouwen")
        compose.assertShowsNoneOf(englishOnlyTexts())
    }
}

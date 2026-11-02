package nl.enadeag.carchargeplanner.android

import java.io.File
import java.io.StringReader
import java.util.Locale
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.w3c.dom.Element
import org.xml.sax.InputSource

/**
 * Every language's texts are complete and fit the English ones, so no text falls back or breaks in any language;
 * the Android counterpart of the desktop's `MessagesBundleTest`. Lint checks much of the same, but only on what the
 * build sees; this also holds the rules of `docs/translating.md` (positional places, escaping, the language list).
 */
class StringsResourcesTest {

    /** What a text is: its kind and the quantities of a plural, so a string in one language and a plural in another differ. */
    private data class Entry(val kind: String, val items: Map<String, String>)

    private companion object {
        val RES = File("src/main/res")

        /** A place such as %1$s or %2$.1f, by its number and its conversion; %% and %n are no places. */
        val PLACE = Regex("%(?:(\\d+)\\$)?[-#+ 0,(]*\\d*(?:\\.\\d+)?([a-zA-Z%])")

        val UNPOSITIONED = Regex("%(?!\\d+\\$)[-#+ 0,(]*\\d*(?:\\.\\d+)?[a-zA-Z]")

        /** A quote or an apostrophe without its backslash, which aapt would take as the start of a quoted run or reject. */
        val UNESCAPED_QUOTE = Regex("(?<!\\\\)['\"]")

        val UNESCAPED_START = Regex("^[@?]")

        @JvmStatic
        fun languages(): List<String> = languageFolders()

        /** The same rule as `languageFolders` in app/build.gradle.kts, which the build uses for the language list. */
        fun languageFolders(): List<String> = RES.listFiles().orEmpty().map { it.name }
            .filter { it.startsWith("values-") && it.removePrefix("values-") in Locale.getISOLanguages() }
            .map { it.removePrefix("values-") }.sorted()

        fun parse(input: InputSource): Element =
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(input).documentElement

        fun file(path: File): Element = parse(InputSource(path.toURI().toString()))

        fun text(xml: String): Element = parse(InputSource(StringReader(xml)))

        fun children(parent: Element, tag: String): List<Element> =
            (0 until parent.childNodes.length).map { parent.childNodes.item(it) }.filterIsInstance<Element>()
                .filter { it.tagName == tag }

        /** The translatable texts of a resources file, by name. */
        fun entries(resources: Element): Map<String, Entry> {
            val result = linkedMapOf<String, Entry>()
            for (string in children(resources, "string").filter { it.getAttribute("translatable") != "false" }) {
                result[string.getAttribute("name")] = Entry("string", mapOf("" to string.textContent.trim()))
            }
            for (plural in children(resources, "plurals")) {
                result[plural.getAttribute("name")] = Entry("plurals",
                    children(plural, "item").associate { it.getAttribute("quantity") to it.textContent.trim() })
            }
            return result
        }

        fun places(text: String): Set<String> = PLACE.findAll(text).map { it.groupValues }
            .filter { it[2] != "%" && it[2] != "n" }.map { "${it[1]}:${it[2]}" }.toSortedSet()

        fun placesOf(entry: Entry): Set<String> = entry.items.values.flatMap { places(it) }.toSortedSet()

        fun quoted(text: String) = text.length > 1 && text.startsWith("\"") && text.endsWith("\"")

        /** What is wrong with a language's texts against the English ones, one line per problem. */
        fun problems(english: Map<String, Entry>, translated: Map<String, Entry>): List<String> {
            val found = mutableListOf<String>()
            for (name in english.keys - translated.keys) found += "$name is missing"
            for (name in translated.keys - english.keys) found += "$name is not in English"
            for ((name, entry) in translated) {
                val source = english[name] ?: continue
                if (entry.kind != source.kind) {
                    found += "$name is a ${entry.kind}, in English a ${source.kind}"
                    continue
                }
                if (entry.items.keys != source.items.keys) {
                    found += "$name has the quantities ${entry.items.keys}, in English ${source.items.keys}"
                }
                if (placesOf(entry) != placesOf(source)) {
                    found += "$name has the places ${placesOf(entry)}, in English ${placesOf(source)}"
                }
            }
            for ((name, entry) in translated) {
                for ((quantity, text) in entry.items) {
                    val label = "$name $quantity".trim()
                    if (text.isBlank()) found += "$label is empty"
                    if (UNPOSITIONED.containsMatchIn(text.replace("%%", ""))) found += "$label has a place without its number"
                    if (!quoted(text) && UNESCAPED_QUOTE.containsMatchIn(text)) found += "$label has an unescaped quote"
                    if (UNESCAPED_START.containsMatchIn(text)) found += "$label starts with an unescaped @ or ?"
                }
            }
            return found
        }
    }

    private val english = entries(file(File(RES, "values/strings.xml")))

    private fun language(code: String) = entries(file(File(RES, "values-$code/strings.xml")))

    @ParameterizedTest
    @MethodSource("languages")
    fun eachLanguageFitsTheEnglishTexts(code: String) {
        assertThat(problems(english, language(code))).isEmpty()
    }

    @Test
    fun theEnglishTextsAreWrittenByTheRulesToo() {
        assertThat(problems(english, english)).isEmpty()
    }

    @Test
    fun onlyTheAppsNameIsLeftUntranslated() {
        val untranslatable = children(file(File(RES, "values/strings.xml")), "string")
            .filter { it.getAttribute("translatable") == "false" }.map { it.getAttribute("name") }

        assertThat(untranslatable).containsExactly("app_name")
    }

    @Test
    fun everyLanguageFolderHoldsTheTexts() {
        val folders = languageFolders().map { File(RES, "values-$it") }

        assertThat(folders).isNotEmpty()
        assertThat(folders.filter { !File(it, "strings.xml").isFile }).isEmpty()
    }

    @Test
    fun theLanguagesTheReleaseOffersAreEnglishAndTheLanguageFolders() {
        val dir = File(requireNotNull(System.getProperty("release.locale.config.dir")), "xml")
        val config = file(requireNotNull(dir.listFiles()).single { it.extension == "xml" })
        val offered = children(config, "locale").map { it.getAttribute("android:name") }
        val defaults = Properties().apply { File(RES, "resources.properties").reader().use { load(it) } }

        assertThat(offered).containsExactlyInAnyOrderElementsOf(listOf("en") + languageFolders())
        assertThat(config.getAttribute("android:defaultLocale")).isEqualTo("en")
        assertThat(defaults.getProperty("unqualifiedResLocale")).isEqualTo("en")
    }

    @Test
    fun aMissingOrSurplusTextIsFound() {
        val source = entries(text("<resources><string name=\"a\">A</string><string name=\"b\">B</string></resources>"))
        val translated = entries(text("<resources><string name=\"a\">A</string><string name=\"c\">C</string></resources>"))

        assertThat(problems(source, translated)).containsExactly("b is missing", "c is not in English")
    }

    @Test
    fun aPlaceThatChangedOrWentIsFound() {
        val source = entries(text("<resources><string name=\"a\">%1\$s: %2\$d%%</string></resources>"))
        val translated = entries(text("<resources><string name=\"a\">%1\$s: %2\$s%%</string></resources>"))
        val dropped = entries(text("<resources><string name=\"a\">%1\$s</string></resources>"))

        assertThat(problems(source, translated)).hasSize(1).first().asString().contains("2:s")
        assertThat(problems(source, dropped)).hasSize(1)
    }

    @Test
    fun aPluralWithOtherQuantitiesIsFound() {
        val source = entries(text("<resources><plurals name=\"p\"><item quantity=\"one\">%1\$d day</item>" +
            "<item quantity=\"other\">%1\$d days</item></plurals></resources>"))
        val translated = entries(text("<resources><plurals name=\"p\"><item quantity=\"other\">%1\$d dagen</item>" +
            "</plurals></resources>"))

        assertThat(problems(source, translated)).hasSize(1).first().asString().contains("quantities")
    }

    @Test
    fun aPlaceWithoutItsNumberAndAnUnescapedQuoteAreFound() {
        val unpositioned = entries(text("<resources><string name=\"a\">%s and %d</string></resources>"))
        val quote = entries(text("<resources><string name=\"a\">Today's</string></resources>"))
        val escaped = entries(text("<resources><string name=\"a\">Today\\'s \\@home 100%%</string></resources>"))
        val leading = entries(text("<resources><string name=\"a\">@home</string></resources>"))

        assertThat(problems(unpositioned, unpositioned)).isNotEmpty()
        assertThat(problems(quote, quote)).isNotEmpty()
        assertThat(problems(leading, leading)).isNotEmpty()
        assertThat(problems(escaped, escaped)).isEmpty()
    }
}

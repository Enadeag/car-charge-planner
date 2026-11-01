package nl.enadeag.carchargeplanner.android.logging

import android.util.Log
import java.io.IOException
import java.util.ServiceLoader
import nl.enadeag.carchargeplanner.android.BuildConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.slf4j.event.Level
import org.slf4j.spi.SLF4JServiceProvider

/** How slf4j's records become Logcat lines, with a sink that keeps them instead of Logcat. */
class LogcatLoggingTest {

    private data class Line(val level: Level, val tag: String, val message: String)

    private val written = mutableListOf<Line>()
    private val sink = LogSink { level, tag, message -> written += Line(level, tag, message) }

    private fun logger(threshold: Level) =
        LogcatServiceProvider.loggers(sink, threshold).getLogger("nl.enadeag.carchargeplanner.engine.secrets.ApiKeys")

    @Test
    fun theReleaseBuildWritesFromInfo() {
        val log = LogcatServiceProvider.forBuild(false, sink).getLogger("Release")

        log.debug("debug")
        log.info("info")

        assertThat(written.map { it.level }).containsExactly(Level.INFO)
    }

    @Test
    fun theDebugBuildWritesFromDebug() {
        val log = LogcatServiceProvider.forBuild(true, sink).getLogger("Debug")

        log.trace("trace")
        log.debug("debug")

        assertThat(written.map { it.level }).containsExactly(Level.DEBUG)
    }

    /** The local tests run against the debug build, so the provider slf4j would use writes from DEBUG. */
    @Test
    fun theProviderTakesItsLevelFromTheBuild() {
        val provider = LogcatServiceProvider()
        provider.initialize()

        val log = provider.loggerFactory.getLogger("Initialized")

        assertThat(BuildConfig.DEBUG).isTrue()
        assertThat(log.isDebugEnabled).isTrue()
        assertThat(log.isTraceEnabled).isFalse()
    }

    @Test
    fun recordsBelowTheThresholdAreLeftOut() {
        val log = logger(Level.INFO)

        log.trace("trace")
        log.debug("debug")
        log.info("info")
        log.warn("warn")
        log.error("error")

        assertThat(written.map { it.level }).containsExactly(Level.INFO, Level.WARN, Level.ERROR)
        assertThat(log.isDebugEnabled).isFalse()
        assertThat(log.isInfoEnabled).isTrue()
    }

    @Test
    fun atDebugOnlyTraceIsLeftOut() {
        val log = logger(Level.DEBUG)

        log.trace("trace")
        log.debug("debug")

        assertThat(written.map { it.level }).containsExactly(Level.DEBUG)
        assertThat(log.isTraceEnabled).isFalse()
    }

    @Test
    fun theTagIsTheLoggingClassesSimpleName() {
        logger(Level.INFO).info("the API keys are kept in the keychain")

        assertThat(written).containsExactly(Line(Level.INFO, "ApiKeys", "the API keys are kept in the keychain"))
    }

    @Test
    fun theArgumentsFillThePlaceholders() {
        logger(Level.INFO).warn("{} of {} answers", 2, 3)

        assertThat(written.single().message).isEqualTo("2 of 3 answers")
    }

    @Test
    fun anExceptionsStackTraceFollowsTheMessage() {
        logger(Level.INFO).error("the forecast failed", IOException("no network", IllegalStateException("cause")))

        val lines = written.single().message.lines()
        assertThat(lines[0]).isEqualTo("the forecast failed")
        assertThat(lines[1]).isEqualTo("java.io.IOException: no network")
        assertThat(lines[2]).startsWith("\tat ")
        assertThat(lines).anyMatch { it == "Caused by: java.lang.IllegalStateException: cause" }
    }

    @Test
    fun theStackTraceEndsWithoutAnEmptyLine() {
        logger(Level.INFO).error("failed", IOException("disk full"))

        assertThat(written.single().message).doesNotEndWith("\n").endsWith(")")
    }

    @Test
    fun aMissingMessageIsWrittenAsNull() {
        logger(Level.INFO).info(null)

        assertThat(written.single().message).isEqualTo("null")
    }

    @Test
    fun aShortMessageIsOneLogcatEntry() {
        assertThat(LogcatSink.parts("one\ntwo")).containsExactly("one\ntwo")
    }

    @Test
    fun aLongMessageIsCutAtLineEndsIntoEntriesLogcatTakesWhole() {
        val line = "\tat nl.enadeag.Some.method(Some.java:1)" // 39 characters
        val message = List(100) { line }.joinToString("\n")

        val parts = LogcatSink.parts(message)

        assertThat(parts).hasSizeGreaterThan(1).allMatch { it.length <= LogcatSink.MOST_PER_ENTRY }
        assertThat(parts).allMatch { it.startsWith(line) && it.endsWith(")") }
        assertThat(parts.joinToString("\n")).isEqualTo(message)
    }

    @Test
    fun aLongLineIsCutAtTheMostAnEntryTakesButNotInsideACharacter() {
        val message = "a".repeat(LogcatSink.MOST_PER_ENTRY - 1) + "\uD83D\uDE97" + "b".repeat(10)

        val parts = LogcatSink.parts(message)

        assertThat(parts).containsExactly("a".repeat(LogcatSink.MOST_PER_ENTRY - 1), "\uD83D\uDE97" + "b".repeat(10))
    }

    @Test
    fun anExceptionAfterThePlaceholdersArgumentsIsTheRecordsException() {
        logger(Level.INFO).warn("attempt {} failed", 2, IOException("refused"))

        val lines = written.single().message.lines()
        assertThat(lines[0]).isEqualTo("attempt 2 failed")
        assertThat(lines[1]).isEqualTo("java.io.IOException: refused")
    }

    @ParameterizedTest
    @EnumSource(Level::class)
    fun eachLevelHasItsLogcatPriority(level: Level) {
        val expected = when (level) {
            Level.ERROR -> Log.ERROR
            Level.WARN -> Log.WARN
            Level.INFO -> Log.INFO
            Level.DEBUG -> Log.DEBUG
            Level.TRACE -> Log.VERBOSE
        }

        assertThat(LogcatSink.priority(level)).isEqualTo(expected)
    }

    @Test
    fun slf4jFindsTheProvider() {
        val providers = ServiceLoader.load(SLF4JServiceProvider::class.java).map { it.javaClass.name }

        assertThat(providers).containsExactly(LogcatServiceProvider::class.java.name)
    }
}

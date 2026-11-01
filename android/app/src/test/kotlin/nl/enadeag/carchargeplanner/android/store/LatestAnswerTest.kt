package nl.enadeag.carchargeplanner.android.store

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class LatestAnswerTest {

    @Test
    fun onlyTheLatestQuestionsAnswerCountsEvenWhenAnEarlierOneAnswersLater() = runTest {
        val latest = LatestAnswer<String>(backgroundScope)
        val first = CompletableDeferred<String>()
        val second = CompletableDeferred<String>()
        val answers = mutableListOf<String>()

        latest.ask({ first.await() }) { answers += it }
        runCurrent()
        latest.ask({ second.await() }) { answers += it }
        runCurrent()
        second.complete("Amsterdam")
        first.complete("Amersfoort")
        runCurrent()

        assertThat(answers).containsExactly("Amsterdam")
    }

    @Test
    fun aQuestionThatNeverStopsIsDroppedWhenCancelled() = runTest {
        val latest = LatestAnswer<String>(backgroundScope)
        val answer = CompletableDeferred<String>()
        val answers = mutableListOf<String>()

        val asked = latest.ask({ answer.await() }) { answers += it }
        runCurrent()
        latest.cancel()
        answer.complete("Lisbon")
        runCurrent()

        assertThat(asked.isCancelled).isTrue()
        assertThat(answers).isEmpty()
    }

    @Test
    fun aQuestionAskedAloneIsAnswered() = runTest {
        val latest = LatestAnswer<Int>(backgroundScope)
        val answers = mutableListOf<Int>()

        latest.ask({ 4 }) { answers += it }
        runCurrent()

        assertThat(answers).containsExactly(4)
    }
}

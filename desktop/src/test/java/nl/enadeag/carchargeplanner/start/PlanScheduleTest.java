package nl.enadeag.carchargeplanner.start;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import nl.enadeag.carchargeplanner.LogCapture;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** When the desktop plans: at the start, every minute, after a change, and never more than once more for many. */
class PlanScheduleTest {

    private final ManualScheduler planner = new ManualScheduler();
    private final PlanSchedule schedule = new PlanSchedule(planner);
    private final AtomicInteger rounds = new AtomicInteger();

    private void started() {
        schedule.start(rounds::incrementAndGet);
        planner.runPending();
    }

    @Test
    void itPlansAtTheStartAndThenEveryMinute() {
        started();
        assertThat(rounds).hasValue(1);

        planner.advance(Duration.ofSeconds(59));
        assertThat(rounds).hasValue(1);

        planner.advance(Duration.ofSeconds(1));
        assertThat(rounds).hasValue(2);
    }

    @Test
    void itPlansAgainSoonAfterAChange() {
        started();

        schedule.runSoon();
        planner.runPending();

        assertThat(rounds).hasValue(2);
    }

    @ParameterizedTest(name = "{0} changes")
    @ValueSource(ints = {1, 2, 5})
    void changesDuringARoundGetExactlyOneMoreRound(int changes) {
        schedule.start(() -> {
            if (rounds.incrementAndGet() == 1) {
                for (int change = 0; change < changes; change++) {
                    schedule.runSoon();
                }
            }
        });

        planner.runPending();

        assertThat(rounds).hasValue(2);
    }

    @Test
    void changesWhileARoundWaitsToRunGetThatOneRound() {
        started();

        schedule.runSoon();
        schedule.runSoon();
        schedule.runSoon();
        planner.runPending();

        assertThat(rounds).hasValue(2);
    }

    @Test
    void aChangeDuringTheRoundAfterAChangeGetsOneMore() {
        schedule.start(() -> {
            if (rounds.incrementAndGet() <= 2) {
                schedule.runSoon();
            }
        });

        planner.runPending();

        assertThat(rounds).hasValue(3);
    }

    @Test
    void aChangeBeforeTheStartWaitsForTheFirstRound() {
        schedule.runSoon();
        planner.runPending();
        assertThat(rounds).hasValue(0);

        started();

        assertThat(rounds).hasValue(1);
    }

    @Test
    void thePlanningKeepsGoingAfterAnError() {
        var first = new boolean[] {true};
        try (var log = LogCapture.of(PlanSchedule.class)) {
            schedule.start(() -> {
                if (first[0]) {
                    first[0] = false;
                    throw new StackOverflowError("a round that fails once");
                }
                rounds.incrementAndGet();
            });
            planner.runPending();

            planner.advance(PlanSchedule.EVERY);

            assertThat(rounds).hasValue(1);
            assertThat(log.records()).singleElement().satisfies(record -> {
                assertThat(record.getMessage()).isEqualTo("planning failed; trying again at the next round");
                assertThat(record.getLevel()).isEqualTo(java.util.logging.Level.SEVERE);
                assertThat(record.getThrown()).isInstanceOf(StackOverflowError.class);
            });
        }
    }

    @Test
    void afterTheStopNothingIsPlanned() {
        started();

        schedule.stop();
        schedule.runSoon();
        planner.advance(Duration.ofMinutes(5));

        assertThat(rounds).hasValue(1);
    }

    @Test
    void itStartsOnce() {
        started();

        assertThatThrownBy(() -> schedule.start(rounds::incrementAndGet)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theStopEndsItsThread() throws InterruptedException {
        var thread = Executors.newSingleThreadScheduledExecutor();
        var onThread = new PlanSchedule(thread);
        var ran = new CountDownLatch(1);
        onThread.start(ran::countDown);
        assertThat(ran.await(10, TimeUnit.SECONDS)).isTrue();

        onThread.stop();

        assertThat(thread.isTerminated()).isTrue();
    }

    @Test
    void stoppingAScheduleWithNoRoundRunningEndsAtOnceAndLogsNothing() {
        var executor = Executors.newSingleThreadScheduledExecutor();
        var onThread = new PlanSchedule(executor);
        onThread.start(() -> { });

        try (var log = LogCapture.of(PlanSchedule.class)) {
            assertTimeoutPreemptively(PlanSchedule.STOP_WAIT.minusSeconds(1), onThread::stop);

            assertThat(executor.isShutdown()).isTrue();
            assertThat(log.lines()).isEmpty();
        }
    }

    @Test
    void stoppingWhileTheThreadIsInterruptedStopsTheRoundAtOnceAndKeepsTheInterrupt() throws InterruptedException {
        var executor = Executors.newSingleThreadScheduledExecutor();
        var onThread = new PlanSchedule(executor);
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        onThread.start(() -> {
            started.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException _) {
                // stopped
            }
        });
        assertThat(started.await(1, TimeUnit.SECONDS)).as("the round runs").isTrue();
        Thread.currentThread().interrupt();
        try {
            onThread.stop();

            assertThat(Thread.currentThread().isInterrupted()).as("the interrupt is kept").isTrue();
            assertThat(executor.isShutdown()).isTrue();
        } finally {
            Thread.interrupted();
            release.countDown();
            executor.shutdownNow();
        }
    }
}

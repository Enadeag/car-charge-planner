package nl.enadeag.carchargeplanner.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import nl.enadeag.carchargeplanner.LogCapture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class PlanScheduleTest {

    @Test
    void stoppingAScheduleWithNoRoundRunningEndsAtOnceAndLogsNothing() {
        var executor = Executors.newSingleThreadScheduledExecutor();
        var schedule = new PlanSchedule(executor);
        schedule.start(() -> { });

        try (var log = LogCapture.of(PlanSchedule.class)) {
            assertTimeoutPreemptively(PlanSchedule.STOP_WAIT.minusSeconds(1), schedule::stop);

            assertThat(executor.isShutdown()).isTrue();
            assertThat(log.lines()).isEmpty();
        }
    }

    @Test
    void stoppingWhileTheThreadIsInterruptedStopsTheRoundAtOnceAndKeepsTheInterrupt() throws InterruptedException {
        var executor = Executors.newSingleThreadScheduledExecutor();
        var schedule = new PlanSchedule(executor);
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        schedule.start(() -> {
            started.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                // stopped
            }
        });
        assertThat(started.await(1, TimeUnit.SECONDS)).as("the round runs").isTrue();
        Thread.currentThread().interrupt();
        try {
            schedule.stop();

            assertThat(Thread.currentThread().isInterrupted()).as("the interrupt is kept").isTrue();
            assertThat(executor.isShutdown()).isTrue();
        } finally {
            Thread.interrupted();
            release.countDown();
            executor.shutdownNow();
        }
    }
}

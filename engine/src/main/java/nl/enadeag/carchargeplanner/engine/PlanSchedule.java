package nl.enadeag.carchargeplanner.engine;

import java.time.Duration;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs the engine's planning round on the engine's own thread: once at the start, then every {@link #EVERY}, and once
 * more soon after each change. A round that throws, even an {@link Error}, is logged and the next one runs as planned.
 *
 * <p>Its methods may be called from any thread.
 */
final class PlanSchedule {

    /**
     * Often enough to announce a window within its notification lead time, and to keep "now" on the dashboard current.
     * The forecast cache answers in between, and a provider that could not be reached waits its own time before it is
     * asked again, so this is not how often a provider is asked.
     */
    static final Duration EVERY = Duration.ofMinutes(1);
    /** How long {@link #stop()} lets a round that is running finish, such as one waiting for a provider. */
    static final Duration STOP_WAIT = Duration.ofSeconds(3);

    private static final Logger LOG = LoggerFactory.getLogger(PlanSchedule.class);

    private final ScheduledExecutorService executor;
    private final AtomicReference<Runnable> round = new AtomicReference<>();

    /**
     * Makes the schedule; nothing runs until {@link #start(Runnable)}.
     *
     * @param executor the engine's own thread, which {@link #stop()} shuts down; never null
     */
    PlanSchedule(ScheduledExecutorService executor) {
        if (executor == null) {
            throw new IllegalArgumentException("the executor must not be null");
        }
        this.executor = executor;
    }

    /**
     * Runs the round at once, and then every {@link #EVERY} after the last one ended.
     *
     * @param round one planning round; never null
     * @throws IllegalStateException when the schedule was started before
     */
    void start(Runnable round) {
        if (round == null) {
            throw new IllegalArgumentException("the round must not be null");
        }
        Runnable guarded = () -> runGuarded(round);
        if (!this.round.compareAndSet(null, guarded)) {
            throw new IllegalStateException("the schedule was started before");
        }
        executor.scheduleWithFixedDelay(guarded, 0, EVERY.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** Runs a round soon, after a change; does nothing before {@link #start(Runnable)} or after {@link #stop()}. */
    void runSoon() {
        var guarded = round.get();
        if (guarded == null || executor.isShutdown()) {
            return;
        }
        try {
            executor.execute(guarded);
        } catch (RejectedExecutionException ignored) {
            // stopped between the check and now: the change is saved, and no one waits for a plan any more
        }
    }

    /**
     * Stops the rounds: the one that is running may finish for {@link #STOP_WAIT}, then it is interrupted. Stopping
     * again does nothing.
     */
    void stop() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(STOP_WAIT.toMillis(), TimeUnit.MILLISECONDS)) {
                LOG.warn("a planning round did not finish in " + STOP_WAIT + "; interrupting it");
                executor.shutdownNow();
            }
        } catch (InterruptedException ignored) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /** A round that throws would cancel every later one of the executor's schedule, so nothing may leave it. */
    private static void runGuarded(Runnable round) {
        try {
            round.run();
        } catch (RuntimeException | Error e) {
            LOG.error("planning failed; trying again at the next round", e);
        }
    }
}

package io.github.enadeag.carchargeplanner.engine;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * An executor whose time only moves when a test says so; tasks run on the test's thread in {@link #advance(Duration)}
 * and {@link #runPending()}. It knows {@code execute} and {@code scheduleWithFixedDelay}, which the engine uses.
 */
public final class ManualScheduler extends AbstractExecutorService implements ScheduledExecutorService {

    private final List<Task> tasks = new ArrayList<>();
    private Duration now = Duration.ZERO;
    private boolean shutdown;

    private final class Task implements ScheduledFuture<Object> {
        private final Runnable command;
        private final Duration delay;
        private Duration due;
        private boolean cancelled;

        Task(Runnable command, Duration due, Duration delay) {
            this.command = command;
            this.due = due;
            this.delay = delay;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(due.minus(now));
        }

        @Override
        public int compareTo(Delayed other) {
            return Long.compare(getDelay(TimeUnit.NANOSECONDS), other.getDelay(TimeUnit.NANOSECONDS));
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            cancelled = true;
            return tasks.remove(this);
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public boolean isDone() {
            return cancelled || !tasks.contains(this);
        }

        @Override
        public Object get() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object get(long timeout, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }
    }

    /** Runs every task that is due now, in the order they are due, and the tasks those add for now. */
    public synchronized void runPending() {
        while (true) {
            var due = tasks.stream().filter(task -> task.due.compareTo(now) <= 0)
                    .min(Comparator.comparing((Task task) -> task.due)).orElse(null);
            if (due == null) {
                return;
            }
            tasks.remove(due);
            // like the JDK's executor, a periodic task that throws is not run again
            due.command.run();
            if (due.delay != null && !shutdown) {
                due.due = now.plus(due.delay);
                tasks.add(due);
            }
        }
    }

    /** Moves the time on step by step to each task that comes due, running it. */
    public synchronized void advance(Duration time) {
        var until = now.plus(time);
        runPending();
        while (true) {
            var next = tasks.stream().map(task -> task.due).filter(due -> due.compareTo(until) <= 0)
                    .min(Comparator.naturalOrder()).orElse(null);
            if (next == null) {
                now = until;
                return;
            }
            now = next;
            runPending();
        }
    }

    @Override
    public synchronized void execute(Runnable command) {
        if (shutdown) {
            throw new RejectedExecutionException("shut down");
        }
        tasks.add(new Task(command, now, null));
    }

    @Override
    public synchronized ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay,
            TimeUnit unit) {
        if (shutdown) {
            throw new RejectedExecutionException("shut down");
        }
        var task = new Task(command, now.plus(Duration.of(initialDelay, unit.toChronoUnit())),
                Duration.of(delay, unit.toChronoUnit()));
        tasks.add(task);
        return task;
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        throw new UnsupportedOperationException();
    }

    /** Drops every task, as the JDK's executor drops its periodic ones at a shutdown. */
    @Override
    public synchronized void shutdown() {
        shutdown = true;
        tasks.clear();
    }

    @Override
    public synchronized List<Runnable> shutdownNow() {
        shutdown();
        return List.of();
    }

    @Override
    public synchronized boolean isShutdown() {
        return shutdown;
    }

    @Override
    public synchronized boolean isTerminated() {
        return shutdown;
    }

    @Override
    public synchronized boolean awaitTermination(long timeout, TimeUnit unit) {
        return shutdown;
    }
}

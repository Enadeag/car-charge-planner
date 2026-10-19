package nl.enadeag.carchargeplanner.desktop;

import com.sun.javafx.tk.Toolkit;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableValue;

/**
 * Runs test code on the JavaFX thread of JavaFX's headless platform, which draws in memory: no window reaches the
 * developer's desktop, and the tests run without one.
 *
 * <p>All JavaFX tests of a run share the one platform, so only a test class marked {@link FxTest} may use it: its
 * {@link FxIsolation} closes every window around each test and fails the test in which the JavaFX thread threw an
 * exception that nothing caught. Without the mark, {@link #call} refuses.
 */
public final class Fx {

    /** The system's wish for reduced motion in the tests: on, so charts show their new values at once. */
    public static final ObservableValue<Boolean> REDUCED_MOTION = new SimpleBooleanProperty(true);

    private static final AtomicBoolean STARTED = new AtomicBoolean();
    private static final long TIMEOUT_S = 10;
    /** How many test classes under {@link FxIsolation} are running now; more than one only for nested classes. */
    private static final AtomicInteger ISOLATED = new AtomicInteger();
    /** What the JavaFX thread threw and nothing caught, oldest first, until {@link #takeUncaught()} takes it. */
    private static final Queue<Throwable> UNCAUGHT = new ConcurrentLinkedQueue<>();

    private Fx() {
    }

    /**
     * Runs code on the JavaFX thread and waits for it.
     *
     * @param code what to run
     * @param <T> what the code returns
     * @return what the code returned
     * @throws IllegalStateException when the calling test class is not marked {@link FxTest}
     * @throws Exception what the code threw, wrapped in an {@link java.util.concurrent.ExecutionException}, or a
     *     timeout when the JavaFX thread did not get to it in 10 seconds
     */
    public static <T> T call(Callable<T> code) throws Exception {
        if (ISOLATED.get() == 0) {
            throw new IllegalStateException("a test class that uses JavaFX needs @FxTest, which keeps the windows "
                    + "and errors of its tests apart from those of other tests");
        }
        var task = new FutureTask<>(code);
        Platform.runLater(task);
        return task.get(TIMEOUT_S, TimeUnit.SECONDS);
    }

    /**
     * Runs code on the JavaFX thread and waits for it.
     *
     * @param code what to run
     * @throws IllegalStateException when the calling test class is not marked {@link FxTest}
     * @throws Exception what the code threw, as {@link #call} does
     */
    public static void run(Runnable code) throws Exception {
        call(() -> {
            code.run();
            return null;
        });
    }

    /**
     * Waits until the JavaFX thread has handled everything queued so far, such as the dashboard's own runLater.
     *
     * @throws Exception when the JavaFX thread did not get to it in time, as {@link #call} does
     */
    public static void settle() throws Exception {
        run(() -> { });
    }

    /**
     * Runs a pulse now, as JavaFX does some 60 times a second, rather than wait for one: windows take the sizes set
     * since the last one, scenes are laid out, and a scene without a focus owner gives the focus to its first control.
     *
     * @throws Exception what the pulse threw, wrapped as {@link #call} does
     */
    public static void pulse() throws Exception {
        run(() -> Toolkit.getToolkit().firePulse());
    }

    /**
     * Starts the headless platform, once in a run, and lets {@link #call} be used until {@link #endIsolation()}.
     *
     * @throws InterruptedException when the test run is stopped while the platform starts
     */
    static void beginIsolation() throws InterruptedException {
        start();
        ISOLATED.incrementAndGet();
    }

    /** Ends what {@link #beginIsolation()} began; {@link #call} refuses again once no isolated class runs. */
    static void endIsolation() {
        ISOLATED.decrementAndGet();
    }

    /**
     * Takes what the JavaFX thread threw and nothing caught since the last time.
     *
     * @return the exceptions, oldest first; empty when there were none
     */
    static List<Throwable> takeUncaught() {
        var taken = new ArrayList<Throwable>();
        for (var next = UNCAUGHT.poll(); next != null; next = UNCAUGHT.poll()) {
            taken.add(next);
        }
        return taken;
    }

    private static void start() throws InterruptedException {
        if (STARTED.compareAndSet(false, true)) {
            // set here rather than only in the pom, so a test started from an IDE cannot open real windows either
            System.setProperty("glass.platform", "Headless");
            System.setProperty("prism.order", "sw");
            var started = new CountDownLatch(1);
            Platform.startup(() -> {
                // JavaFX hands what an event handler or a pulse throws to the thread's handler, which only prints it
                Thread.currentThread().setUncaughtExceptionHandler((thread, thrown) -> UNCAUGHT.add(thrown));
                started.countDown();
            });
            Platform.setImplicitExit(false);
            started.await(TIMEOUT_S, TimeUnit.SECONDS);
        }
    }
}

package io.github.enadeag.carchargeplanner.desktop;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableValue;

/**
 * Runs test code on the JavaFX thread of JavaFX's headless platform, which draws in memory: no window reaches the
 * developer's desktop, and the tests run without one.
 */
final class Fx {

    /** The system's wish for reduced motion in the tests: on, so charts show their new values at once. */
    static final ObservableValue<Boolean> REDUCED_MOTION = new SimpleBooleanProperty(true);

    private static final AtomicBoolean STARTED = new AtomicBoolean();
    private static final long TIMEOUT_S = 10;

    private Fx() {
    }

    static <T> T call(Callable<T> code) throws Exception {
        start();
        var task = new FutureTask<>(code);
        Platform.runLater(task);
        return task.get(TIMEOUT_S, TimeUnit.SECONDS);
    }

    static void run(Runnable code) throws Exception {
        call(() -> {
            code.run();
            return null;
        });
    }

    /** Waits until the JavaFX thread has handled everything queued so far, such as the dashboard's own runLater. */
    static void settle() throws Exception {
        run(() -> { });
    }

    private static void start() throws InterruptedException {
        if (STARTED.compareAndSet(false, true)) {
            // set here rather than only in the pom, so a test started from an IDE cannot open real windows either
            System.setProperty("glass.platform", "Headless");
            System.setProperty("prism.order", "sw");
            var started = new CountDownLatch(1);
            Platform.startup(started::countDown);
            Platform.setImplicitExit(false);
            started.await(TIMEOUT_S, TimeUnit.SECONDS);
        }
    }
}

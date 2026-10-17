package io.github.enadeag.carchargeplanner.desktop;

import java.lang.System.Logger.Level;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.application.Platform;

/**
 * Runs slow work, such as reading the keychain, writing a file or asking a provider, away from the JavaFX thread, and
 * hands its outcome back to the JavaFX thread, so a window never freezes while it waits. Work that fails is logged
 * here, by its kind only, as its message could hold a key or an address, and then handed back as well, so the window
 * can say so. The composition root makes one and hands it to every
 * part that needs it; a test makes one that runs the work where it wants.
 */
public final class Background {

    private static final System.Logger LOG = System.getLogger(Background.class.getName());

    private final Executor work;
    private final Executor fx;

    /**
     * Makes the helper.
     *
     * @param work runs the work, never on the JavaFX thread; one that runs a task at a time keeps the work in order
     * @param fx runs the outcome on the JavaFX thread, such as {@code Platform::runLater}
     * @throws IllegalArgumentException when either is null
     */
    public Background(Executor work, Executor fx) {
        if (work == null || fx == null) {
            throw new IllegalArgumentException("the executors must not be null");
        }
        this.work = work;
        this.fx = fx;
    }

    /**
     * The helper the app uses: a virtual thread for each piece of work, and the outcome on the JavaFX thread.
     *
     * @return a new helper
     */
    public static Background onVirtualThreads() {
        return new Background(task -> Thread.ofVirtual().name("background").start(task), Platform::runLater);
    }

    /**
     * Runs work away from the JavaFX thread and hands what it gives, or how it failed, to the JavaFX thread. A failure
     * is logged before it is handed on; a window shows its kind, not its message, for the same reason the log does.
     *
     * @param what the work in a few words for the log, such as "reading the settings"; never a key, an address or the
     *     home's coordinates
     * @param work the slow part; a checked exception it cannot handle itself comes wrapped in a runtime exception
     * @param onFx gets what the work gave, on the JavaFX thread
     * @param onFxFailure gets what the work threw, on the JavaFX thread
     * @param <T> what the work gives
     */
    public <T> void run(String what, Supplier<T> work, Consumer<T> onFx, Consumer<RuntimeException> onFxFailure) {
        this.work.execute(() -> {
            T result;
            try {
                result = work.get();
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "failed in the background: " + what + " (" + e.getClass().getSimpleName() + ")");
                fx.execute(() -> onFxFailure.accept(e));
                return;
            }
            fx.execute(() -> onFx.accept(result));
        });
    }

    /**
     * What a failure handed back is, in words a window can show: its kind, never its message.
     *
     * @param failure what the work threw
     * @param messages the texts, in the language the window speaks
     * @return such as "an unexpected error (IllegalStateException)"
     */
    public static String describe(RuntimeException failure, Messages messages) {
        return messages.text(TextKey.UNEXPECTED_ERROR, failure.getClass().getSimpleName());
    }
}

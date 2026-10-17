package io.github.enadeag.carchargeplanner.desktop;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import javafx.stage.Window;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Keeps the JavaFX tests of a run apart, though they share one headless platform: each test starts without a window,
 * so a window an earlier test left cannot take the keyboard's focus; and a test fails when the JavaFX thread threw an
 * exception that nothing caught while it ran, which JavaFX itself would only print. Used through {@link FxTest}.
 */
public final class FxIsolation implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback {

    /**
     * A bug in OpenJFX itself that a test cannot avoid without changing the app: its exception does not fail a test.
     *
     * @param link where the bug is described
     * @param matches true for the exception the bug throws
     */
    private record UpstreamBug(String link, Predicate<Throwable> matches) {
    }

    /**
     * The only entries: bugs of OpenJFX, each with a link. The headless window clears what a window no longer covers
     * when it shrinks, without keeping to its 1000 px frame: a window the dashboard first sizes to a day taller than
     * the screen, as {@code Stage.sizeToScene()} does at once, throws when the next pulse caps it.
     */
    private static final List<UpstreamBug> UPSTREAM_BUGS = List.of(new UpstreamBug(
            "https://github.com/openjdk/jfx/blob/88ad997176fa/modules/javafx.graphics/src/main/java/com/sun/glass/ui/headless/"
                    + "HeadlessWindow.java#L157-L162",
            thrown -> thrown instanceof IndexOutOfBoundsException
                    && thrownIn(thrown, "com.sun.glass.ui.headless.HeadlessWindow", "clearRect")));

    /** Made by JUnit, for each class marked {@link FxTest}. */
    public FxIsolation() {
    }

    @Override
    public void beforeAll(ExtensionContext context) throws InterruptedException {
        Fx.beginIsolation();
    }

    @Override
    public void afterAll(ExtensionContext context) {
        Fx.endIsolation();
    }

    /**
     * Closes every window still showing, and fails when the JavaFX thread threw after the test before this one ended.
     */
    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        Fx.run(FxIsolation::hideAll);
        failOn(Fx.takeUncaught(), "after the previous test had ended");
    }

    /**
     * Lets what the test changed reach its windows, as the next pulse would, and fails when the JavaFX thread threw
     * during the test; closes the test's windows, and fails when one is still showing after that.
     */
    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        Fx.settle();
        var uncaught = new ArrayList<Throwable>();
        try {
            // a pulse gives a window the size the test set; what throws there belongs to this test
            Fx.pulse();
        } catch (ExecutionException e) {
            uncaught.add(e.getCause());
        }
        uncaught.addAll(0, Fx.takeUncaught());
        Fx.run(FxIsolation::hideAll);
        Fx.settle();
        var left = Fx.call(() -> Window.getWindows().stream().map(Object::toString).toList());
        failOn(uncaught, "during the test");
        if (!left.isEmpty()) {
            throw new AssertionError("windows still showing after the test closed them: " + left);
        }
    }

    private static void hideAll() {
        List.copyOf(Window.getWindows()).forEach(Window::hide);
    }

    private static void failOn(List<Throwable> uncaught, String when) {
        var unexpected = uncaught.stream()
                .filter(thrown -> UPSTREAM_BUGS.stream().noneMatch(bug -> bug.matches().test(thrown)))
                .toList();
        if (unexpected.isEmpty()) {
            return;
        }
        var failure = new AssertionError("the JavaFX thread threw " + unexpected.size() + " exception(s) that nothing "
                + "caught " + when + "; the first: " + unexpected.getFirst(), unexpected.getFirst());
        unexpected.stream().skip(1).forEach(failure::addSuppressed);
        throw failure;
    }

    /** Whether the first frame outside the JDK is a method of a class: where the exception really arose. */
    private static boolean thrownIn(Throwable thrown, String className, String method) {
        return Arrays.stream(thrown.getStackTrace())
                .filter(frame -> !"java.base".equals(frame.getModuleName()))
                .findFirst()
                .map(frame -> frame.getClassName().equals(className) && frame.getMethodName().equals(method))
                .orElse(false);
    }
}

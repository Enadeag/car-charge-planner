package io.github.enadeag.carchargeplanner.desktop;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.enadeag.carchargeplanner.LogCapture;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The work and its outcome each on their own executor, run by hand here. */
class BackgroundTest {

    private final List<Runnable> work = new ArrayList<>();
    private final List<Runnable> fx = new ArrayList<>();
    private final Background background = new Background(work::add, fx::add);
    private final List<String> happened = new ArrayList<>();

    private static void runAll(List<Runnable> tasks) {
        var now = List.copyOf(tasks);
        tasks.clear();
        now.forEach(Runnable::run);
    }

    @Test
    void theWorkRunsOnTheWorkExecutorAndItsOutcomeOnTheJavaFxOne() {
        background.run("reading", () -> {
            happened.add("work");
            return 42;
        }, value -> happened.add("shown " + value), failure -> happened.add("failed"));
        assertThat(happened).isEmpty();

        runAll(work);
        assertThat(happened).containsExactly("work");
        runAll(fx);

        assertThat(happened).containsExactly("work", "shown 42");
    }

    @Test
    void aFailureIsLoggedByItsKindOnlyAndHandedBack() {
        try (var log = LogCapture.of(Background.class)) {
            background.run("saving the settings", () -> {
                throw new IllegalStateException("secret-looking message https://example.invalid/?key=K");
            }, value -> happened.add("shown"), failure -> happened.add("failed " + failure.getMessage()));
            runAll(work);
            runAll(fx);

            assertThat(log.lines()).containsExactly(
                    "WARNING failed in the background: saving the settings (IllegalStateException)");
            assertThat(log.records().getFirst().getThrown()).isNull();
        }
        assertThat(happened).containsExactly("failed secret-looking message https://example.invalid/?key=K");
    }

    @Test
    void aWindowIsToldTheKindOfAFailureInItsLanguage() {
        assertThat(Background.describe(new IllegalStateException("x"), new Messages(Language.DUTCH)))
                .isEqualTo("een onverwachte fout (IllegalStateException)");
    }
}

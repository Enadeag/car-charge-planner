package nl.enadeag.carchargeplanner.engine;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Temporary (#286): fails exactly on Java 17, to show that the Java 17 job fails for a test that fails only there. */
class OnlyOnJava17Test {

    @Test
    void failsOnlyOnJava17() {
        assertThat(Runtime.version().feature()).isNotEqualTo(17);
    }
}

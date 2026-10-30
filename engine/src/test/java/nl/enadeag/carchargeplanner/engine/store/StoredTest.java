package nl.enadeag.carchargeplanner.engine.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class StoredTest {

    @Test
    void aFoundValueIsTheValue() {
        assertThat(new Stored.Found<>("kept").orElse("fallback")).isEqualTo("kept");
    }

    static Stream<Stored<String>> nothingUsable() {
        return Stream.of(new Stored.Absent<>(), new Stored.Unusable<>(Stored.Reason.UNREADABLE, "permission denied"),
                new Stored.Unusable<>(Stored.Reason.INVALID, "not valid JSON"));
    }

    @ParameterizedTest
    @MethodSource("nothingUsable")
    void aMissingOrUnusableFileGivesTheFallback(Stored<String> stored) {
        assertThat(stored.orElse("fallback")).isEqualTo("fallback");
    }

    /** On Java 17 the compiler no longer checks that the readers' if/else chains handle every kind; this does. */
    @Test
    void theKindsAreTheThreeTheReadersHandle() {
        assertThat(Stored.class.getPermittedSubclasses())
                .as("a new kind needs a branch in ConfigFile.read, SecretsFile.read and "
                        + "AwayFile/TripFile.storedForChange")
                .containsExactlyInAnyOrder(Stored.Absent.class, Stored.Found.class, Stored.Unusable.class);
    }
}

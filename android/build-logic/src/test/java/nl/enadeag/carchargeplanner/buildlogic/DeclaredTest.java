package nl.enadeag.carchargeplanner.buildlogic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DeclaredTest {

    @ParameterizedTest
    @CsvSource(value = {"NULL, slf4j-api", "'', slf4j-api", "' ', slf4j-api", "org.slf4j, NULL", "org.slf4j, ''",
        "org.slf4j, ' '"}, nullValues = "NULL")
    void aLibraryWithoutAGroupOrAnArtifactIsRefused(String group, String artifact) {
        assertThatIllegalArgumentException().isThrownBy(() -> new Declared(group, artifact));
    }

    @Test
    void theModuleIsGroupAndArtifact() {
        assertThat(new Declared("org.junit.jupiter", "junit-jupiter-api").module())
                .isEqualTo("org.junit.jupiter:junit-jupiter-api");
    }
}

package io.github.enadeag.carchargeplanner.engine.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.node.ObjectNode;

class FileVersionTest {

    private static final FileVersion FIRST = new FileVersion(1, List.of());

    @Test
    void aFileWithoutAVersionIsTheFirstVersion() throws FormatProblem {
        assertThat(FIRST.upToDate(StoredJson.parse("{\"a\": 1}"))).isEqualTo(StoredJson.parse("{\"a\": 1}"));
    }

    @Test
    void theVersionIsTakenOutSoTheFileReadsOnlyItsOwnValues() throws FormatProblem {
        assertThat(FIRST.upToDate(StoredJson.parse("{\"version\": 1, \"a\": 1}")))
                .isEqualTo(StoredJson.parse("{\"a\": 1}"));
    }

    @Test
    void aFileOfANewerVersionIsRefused() {
        assertThatThrownBy(() -> FIRST.upToDate(StoredJson.parse("{\"version\": 2, \"a\": 1}")))
                .isInstanceOf(FormatProblem.class)
                .hasMessageContaining("written by a newer version of the app");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"version\": 0}", "{\"version\": 1.5}", "{\"version\": \"1\"}", "{\"version\": null}",
        "[1]", "\"text\""})
    void aVersionThatIsNoWholeNumberFromOneOrAFileThatIsNoObjectIsRefused(String json) {
        assertThatThrownBy(() -> FIRST.upToDate(StoredJson.parse(json))).isInstanceOf(FormatProblem.class);
    }

    @Test
    void anOlderVersionIsBroughtUpToDateStepByStep() throws FormatProblem {
        UnaryOperator<ObjectNode> oneToTwo = file -> file.put("b", file.path("a").intValue() + 1);
        UnaryOperator<ObjectNode> twoToThree = file -> file.put("c", file.path("b").intValue() * 10);
        var third = new FileVersion(3, List.of(oneToTwo, twoToThree));

        assertThat(third.upToDate(StoredJson.parse("{\"a\": 1}")))
                .isEqualTo(StoredJson.parse("{\"a\": 1, \"b\": 2, \"c\": 20}"));
        assertThat(third.upToDate(StoredJson.parse("{\"version\": 2, \"b\": 5}")))
                .isEqualTo(StoredJson.parse("{\"b\": 5, \"c\": 50}"));
    }

    @Test
    void theVersionComesFirstInAWrittenFile() {
        assertThat(StoredJson.compact(FIRST.stamped(StoredJson.object().put("a", 1))))
                .isEqualTo("{\"version\":1,\"a\":1}");
    }

    @Test
    void aVersionWithoutAStepFromEachOlderOneIsRefused() {
        assertThatThrownBy(() -> new FileVersion(2, List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FileVersion(0, List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}

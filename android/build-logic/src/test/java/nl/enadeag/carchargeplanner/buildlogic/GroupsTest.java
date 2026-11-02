package nl.enadeag.carchargeplanner.buildlogic;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GroupsTest {

    @ParameterizedTest
    @CsvSource({
        "org.junit, org.junit, true",
        "org.junit.jupiter, org.junit, true",
        "org.junit.jupiter.api, org.junit, true",
        "org.junitx, org.junit, false",
        "org.junit, org.junit.jupiter, false",
        "x.org.junit.jupiter, org.junit, false",
        "org.junit, org.jun, false",
    })
    void aGroupIsInItselfAndInEachOfItsParentsOnly(String group, String parent, boolean in) {
        assertThat(Groups.isIn(group, parent)).isEqualTo(in);
    }
}

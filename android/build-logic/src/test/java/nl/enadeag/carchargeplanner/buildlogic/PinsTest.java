package nl.enadeag.carchargeplanner.buildlogic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PinsTest {

    @Test
    void aLibraryWithoutVersionOrBomHasNoVersion() {
        var pins = Pins.of(List.of(new Declared("org.slf4j", "slf4j-api", null, false)));

        assertThat(pins).containsExactlyEntriesOf(Map.of("org.slf4j:slf4j-api", "no version"));
    }

    @Test
    void aBomPinsALibraryOfItsOwnGroup() {
        var pins = Pins.of(List.of(new Declared("tools.jackson", "jackson-bom", "3.2.3", true),
                new Declared("tools.jackson", "jackson-annotations", null, false)));

        assertThat(pins).containsEntry("tools.jackson:jackson-annotations", "BOM tools.jackson:jackson-bom:3.2.3");
    }

    @Test
    void aBomDoesNotPinAGroupThatOnlyStartsAlike() {
        var pins = Pins.of(List.of(new Declared("tools.jackson", "jackson-bom", "3.2.3", true),
                new Declared("tools.jacksonx", "other", null, false)));

        assertThat(pins).containsEntry("tools.jacksonx:other", "no version");
    }

    @Test
    void theBomOfTheLongestGroupPinsALibrary() {
        var junit = new Declared("org.junit", "junit-bom", "6.1.3", true);
        var jupiter = new Declared("org.junit.jupiter", "jupiter-bom", "6.2.0", true);
        var library = new Declared("org.junit.jupiter.api", "some-api", null, false);

        var expected = "BOM org.junit.jupiter:jupiter-bom:6.2.0";
        assertThat(Pins.of(List.of(junit, jupiter, library))).containsEntry("org.junit.jupiter.api:some-api", expected);
        assertThat(Pins.of(List.of(jupiter, junit, library))).containsEntry("org.junit.jupiter.api:some-api", expected);
    }
}

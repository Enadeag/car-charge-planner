package nl.enadeag.carchargeplanner.engine.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PlaceNameTest {

    @Test
    void aNameIsKeptWithoutTheSpacesAroundIt() {
        assertThat(new PlaceName("  Utrecht, Netherlands \n").text()).isEqualTo("Utrecht, Netherlands");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n"})
    void aBlankNameIsRefusedAndMeansNoneInAFile(String blank) {
        assertThatThrownBy(() -> new PlaceName(blank)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("a place name must not be blank");
        assertThat(PlaceName.of(blank)).isEmpty();
        assertThat(PlaceName.fromLabel(blank)).isEmpty();
    }

    @Test
    void aHundredCharactersAreAllowedAndOneMoreIsRefused() {
        var hundred = "a".repeat(100);

        assertThat(new PlaceName(hundred).text()).hasSize(100);
        assertThat(new PlaceName(" " + hundred + " ").text()).isEqualTo(hundred);
        assertThatThrownBy(() -> new PlaceName(hundred + "b")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("a place name must be at most 100 characters");
        assertThatThrownBy(() -> PlaceName.of(hundred + "b")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aLetterOutsideTheBasicPlaneCountsAsOneCharacter() {
        var hundred = "𝐀".repeat(100);

        assertThat(new PlaceName(hundred).text()).isEqualTo(hundred);
        assertThatThrownBy(() -> new PlaceName(hundred + "𝐀")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aLongLabelIsCutAtAHundredCharactersAndAShortOneKept() {
        var label = "x".repeat(99) + "𝐀" + "tail";

        assertThat(PlaceName.fromLabel(label)).contains(new PlaceName("x".repeat(99) + "𝐀"));
        assertThat(PlaceName.fromLabel(" Lisbon, Portugal ")).contains(new PlaceName("Lisbon, Portugal"));
        assertThat(PlaceName.fromLabel("y".repeat(100))).contains(new PlaceName("y".repeat(100)));
    }

    @Test
    void aLabelCutBeforeASpaceLosesTheSpace() {
        assertThat(PlaceName.fromLabel("z".repeat(99) + " and more")).contains(new PlaceName("z".repeat(99)));
    }
}

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

    /** One of each kind the rule names: controls with line breaks and tabs, the two separators, the direction marks. */
    @ParameterizedTest
    @ValueSource(strings = {"\n", "\r", "\t", "\u0000", "\u001B", "\u007F", "\u0085", "\u2028", "\u2029", "\u200E",
        "\u200F", "\u061C", "\u202A", "\u202E", "\u2066", "\u2069"})
    void aControlSeparatorOrDirectionCharacterInANameIsRefusedWithoutRepeatingIt(String character) {
        var name = "Zonne" + character + "dorp";

        assertThatThrownBy(() -> new PlaceName(name)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("a place name must not hold a control, line break or direction character");
        assertThatThrownBy(() -> PlaceName.of(name)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\n", "\t", "\u0000", "\u0085", "\u2028", "\u2029", "\u200F", "\u061C", "\u202E",
        "\u2067"})
    void aLabelGetsASpaceForEachSuchCharacter(String character) {
        assertThat(PlaceName.fromLabel(character + "Zonne" + character + "dorp, " + character + "Belgium" + character))
                .contains(new PlaceName("Zonne dorp,  Belgium"));
    }

    @Test
    void aLabelOfOnlySuchCharactersIsNoName() {
        assertThat(PlaceName.fromLabel("\u202E\n\u2066\t")).isEmpty();
    }

    @Test
    void aLabelIsCutAtAHundredCharactersAfterItsCharactersBecameSpaces() {
        var label = "\u202E" + "x".repeat(98) + "\ny" + "tail";

        assertThat(PlaceName.fromLabel(label)).contains(new PlaceName("x".repeat(98) + " y"));
    }

    /** Persian and Indic scripts need the zero-width non-joiner and joiner to write a name right. */
    @Test
    void theZeroWidthNonJoinerAndJoinerAreKept() {
        var persian = "\u0645\u06CC\u200C\u0634\u0647\u0631";
        var indic = "\u0915\u094D\u200D\u0937";

        assertThat(new PlaceName(persian).text()).isEqualTo(persian);
        assertThat(new PlaceName(indic).text()).isEqualTo(indic);
        assertThat(PlaceName.fromLabel(persian)).contains(new PlaceName(persian));
        assertThat(PlaceName.fromLabel(indic)).contains(new PlaceName(indic));
    }
}

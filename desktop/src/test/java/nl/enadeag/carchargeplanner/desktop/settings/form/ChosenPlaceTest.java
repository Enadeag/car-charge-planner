package nl.enadeag.carchargeplanner.desktop.settings.form;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.desktop.text.Language;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.engine.config.Coordinates;
import nl.enadeag.carchargeplanner.engine.config.PlaceName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ChosenPlaceTest {

    private static final PlaceName UTRECHT = new PlaceName("Utrecht, Netherlands");
    private static final ChosenPlace CHOSEN = new ChosenPlace(UTRECHT, new Coordinates(52.09083, 5.12222));
    private final NumberText english = new NumberText(new Messages(Language.ENGLISH));
    private final NumberText dutch = new NumberText(new Messages(Language.DUTCH));

    @ParameterizedTest
    @CsvSource({"52.09083, 5.12222", "52.0908, 5.1222", "52.09080, 5.12220", "52.090849, 5.122249"})
    void theNameHoldsWhileTheFieldsShowTheSameFourDecimals(double latitude, double longitude) {
        assertThat(CHOSEN.nameAt(latitude, longitude, english)).contains(UTRECHT);
        assertThat(CHOSEN.nameAt(latitude, longitude, dutch)).contains(UTRECHT);
    }

    @ParameterizedTest
    @CsvSource({"52.0909, 5.12222", "52.09083, 5.1223", "-52.09083, 5.12222", "5.12222, 52.09083"})
    void aRealDifferenceInEitherCoordinateDropsTheName(double latitude, double longitude) {
        assertThat(CHOSEN.nameAt(latitude, longitude, english)).isEmpty();
    }

    @Test
    void theNameOfThePlaceNotChosenYetHoldsOnlyThere() {
        var odd = new ChosenPlace(UTRECHT, Coordinates.NOT_CHOSEN);

        assertThat(odd.nameAt(0, 0, english)).contains(UTRECHT);
        assertThat(odd.nameAt(0.0001, 0, english)).isEmpty();
    }
}

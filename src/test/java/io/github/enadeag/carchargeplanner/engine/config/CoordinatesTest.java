package io.github.enadeag.carchargeplanner.engine.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CoordinatesTest {

    @ParameterizedTest
    @CsvSource({"0, 0", "-90, -180", "90, 180", "51.0, 5.0", "-33.9, 151.2"})
    void aLatitudeAndALongitudeInRangeAreCoordinates(double latitude, double longitude) {
        var coordinates = new Coordinates(latitude, longitude);

        assertThat(coordinates.latitude()).isEqualTo(latitude);
        assertThat(coordinates.longitude()).isEqualTo(longitude);
        assertThat(Coordinates.areValid(latitude, longitude)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"90.001, 0", "-90.001, 0", "120, 5", "NaN, 5", "Infinity, 5"})
    void aLatitudeOutOfRangeIsRefused(double latitude, double longitude) {
        assertThatThrownBy(() -> new Coordinates(latitude, longitude))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("latitude must be between -90 and 90");
        assertThat(Coordinates.areValid(latitude, longitude)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"0, 180.001", "0, -180.001", "51, 360", "51, NaN", "51, -Infinity"})
    void aLongitudeOutOfRangeIsRefused(double latitude, double longitude) {
        assertThatThrownBy(() -> new Coordinates(latitude, longitude))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("longitude must be between -180 and 180");
        assertThat(Coordinates.areValid(latitude, longitude)).isFalse();
    }
}

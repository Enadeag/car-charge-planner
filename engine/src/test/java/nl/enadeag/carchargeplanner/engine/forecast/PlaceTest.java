package nl.enadeag.carchargeplanner.engine.forecast;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.engine.config.Coordinates;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlaceTest {

    private static final Place JEFFERSON = place("Amsterdam", "Ohio", "Jefferson");
    private static final Place LICKING = place("Amsterdam", "Ohio", "Licking");
    private static final Place NEW_YORK = place("Amsterdam", "New York", "Montgomery");

    private static Place place(String name, String region, String area) {
        return new Place(name, new Coordinates(0, 0), ZoneId.of("UTC"), region, "United States", area);
    }

    @Test
    void aResultThatReadsLikeNoOtherShowsItsLabel() {
        var shown = List.of(JEFFERSON, NEW_YORK);
        assertThat(JEFFERSON.labelAmong(shown)).isEqualTo("Amsterdam, Ohio, United States");
        assertThat(NEW_YORK.labelAmong(shown)).isEqualTo("Amsterdam, New York, United States");
    }

    @Test
    void resultsThatReadAlikeShowTheirFinerArea() {
        var shown = List.of(JEFFERSON, NEW_YORK, LICKING);
        assertThat(JEFFERSON.labelAmong(shown)).isEqualTo("Amsterdam, Jefferson, Ohio, United States");
        assertThat(LICKING.labelAmong(shown)).isEqualTo("Amsterdam, Licking, Ohio, United States");
        assertThat(NEW_YORK.labelAmong(shown)).isEqualTo("Amsterdam, New York, United States");
    }

    @Test
    void aResultAloneShowsItsLabel() {
        assertThat(JEFFERSON.labelAmong(List.of(JEFFERSON))).isEqualTo("Amsterdam, Ohio, United States");
    }
}

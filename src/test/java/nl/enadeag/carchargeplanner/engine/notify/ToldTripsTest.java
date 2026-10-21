package nl.enadeag.carchargeplanner.engine.notify;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.LogCapture;
import nl.enadeag.carchargeplanner.engine.store.StoredFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ToldTripsTest {

    private static final ToldTrip ASKED = new ToldTrip(Instant.parse("2026-07-05T06:30:00Z"), true,
            Optional.of(23.5), false);

    @TempDir
    Path folder;

    private Path path() {
        return folder.resolve("data").resolve("trip-notices.json");
    }

    @Test
    void savedTripsAreLoadedInTheirOrder() {
        var notAsked = new ToldTrip(Instant.parse("2026-07-04T15:00:00Z"), false, Optional.empty(), true);
        new ToldTrips(path()).save(List.of(ASKED, notAsked));

        assertThat(new ToldTrips(path()).load()).containsExactly(ASKED, notAsked);
    }

    @Test
    void aTripTheOwnerWasNotAskedAboutHasNoGridShareInTheFile() throws IOException {
        new ToldTrips(path()).save(List.of(new ToldTrip(Instant.parse("2026-07-04T15:00:00Z"), true, Optional.empty(),
                false)));

        assertThat(Files.readString(path())).doesNotContain("askedGridPct");
    }

    /** The shape of version 1, so an update does not tell the owner again. */
    @Test
    void aFileInTheShapeOfVersionOneIsRead() throws IOException {
        Files.createDirectories(path().getParent());
        Files.writeString(path(), "{\"version\": 1, \"trips\": [{\"departure\": \"2026-07-05T06:30:00Z\", "
                + "\"chargeStart\": true, \"askedGridPct\": 23.5, \"short\": false}]}");

        assertThat(new ToldTrips(path()).load()).containsExactly(ASKED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not json", "[]", "{\"trips\": {}}",
        "{\"trips\": [{\"departure\": \"soon\", \"chargeStart\": true, \"short\": false}]}",
        "{\"trips\": [{\"departure\": 5, \"chargeStart\": true, \"short\": false}]}",
        "{\"trips\": [{\"departure\": \"2026-07-05T06:30:00Z\", \"chargeStart\": 1, \"short\": false}]}",
        "{\"trips\": [{\"departure\": \"2026-07-05T06:30:00Z\", \"chargeStart\": true, \"short\": \"no\"}]}",
        "{\"trips\": [{\"departure\": \"2026-07-05T06:30:00Z\", \"chargeStart\": true, \"short\": false, "
                + "\"askedGridPct\": \"much\"}]}"})
    void aDamagedFileHoldsNoTrips(String content) throws IOException {
        Files.createDirectories(path().getParent());
        Files.writeString(path(), content);

        assertThat(new ToldTrips(path()).load()).isEmpty();
    }

    @Test
    void aDamagedTripIsNamedByItsPlaceInTheFile() throws IOException {
        Files.createDirectories(path().getParent());
        Files.writeString(path(), "{\"trips\": [{\"departure\": \"2026-07-05T06:30:00Z\", \"chargeStart\": true, "
                + "\"short\": false}, {\"departure\": \"2026-07-05T06:30:00Z\"}]}");
        try (var log = LogCapture.of(StoredFile.class)) {
            assertThat(new ToldTrips(path()).load()).isEmpty();

            assertThat(log.lines())
                    .containsExactly("WARNING " + path() + " cannot be used: trip 2 is not a whole trip");
        }
    }
}

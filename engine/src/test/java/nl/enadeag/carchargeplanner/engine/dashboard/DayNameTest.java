package nl.enadeag.carchargeplanner.engine.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DayNameTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 9);

    private static DayName expected(String kind, LocalDate date) {
        return switch (kind) {
            case "today" -> new DayName.Today(date);
            case "tomorrow" -> new DayName.Tomorrow(date);
            default -> new DayName.Other(date);
        };
    }

    @ParameterizedTest
    @CsvSource({"-1, other", "0, today", "1, tomorrow", "2, other", "365, other"})
    void aDayIsNamedFromTheDayThePlanWasMade(int daysAfterToday, String kind) {
        var date = TODAY.plusDays(daysAfterToday);

        var name = DayName.of(date, TODAY);

        assertThat(name).isEqualTo(expected(kind, date));
        assertThat(name.date()).isEqualTo(date);
    }
}

package nl.enadeag.carchargeplanner.desktop.dashboard.day;

import static nl.enadeag.carchargeplanner.desktop.dashboard.PlannedDays.TODAY;
import static nl.enadeag.carchargeplanner.desktop.dashboard.PlannedDays.TWO_DAYS;
import static nl.enadeag.carchargeplanner.desktop.dashboard.PlannedDays.at;
import static nl.enadeag.carchargeplanner.desktop.dashboard.PlannedDays.day;
import static nl.enadeag.carchargeplanner.desktop.dashboard.PlannedDays.planned;
import static nl.enadeag.carchargeplanner.desktop.dashboard.PlannedDays.sunny;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nl.enadeag.carchargeplanner.desktop.text.Language;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import java.util.List;
import org.junit.jupiter.api.Test;

class DayMapperTest {

    @Test
    void aDayOfAnotherPlanIsRefused() {
        var later = TODAY.plusDays(5);
        var other = planned(at(later, 8, 0), false, day(later, 50, 50, sunny(later, 3)));

        assertThatThrownBy(() -> new DayMapper(TWO_DAYS, List.of(), new Messages(Language.ENGLISH))
                .map(other.dashboard(List.of(), List.of()).days()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

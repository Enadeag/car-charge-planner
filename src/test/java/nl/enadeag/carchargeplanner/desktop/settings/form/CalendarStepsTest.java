package nl.enadeag.carchargeplanner.desktop.settings.form;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CalendarStepsTest {

    private static List<CalendarStep> open(CalendarSteps steps) {
        return Arrays.stream(CalendarStep.values()).filter(steps::isOpen).toList();
    }

    @ParameterizedTest
    @CsvSource({
        "false, false, false, false, 1",
        "true,  false, true,  true,  1",
        "false, true,  true,  true,  1",
        "true,  true,  false, false, 2",
        "true,  true,  false, true,  2",
        "true,  true,  true,  false, 3",
        "true,  true,  true,  true,  4",
    })
    void eachStepOpensOnceTheOneBeforeItIsDone(boolean read, boolean send, boolean source, boolean key, int count) {
        assertThat(open(new CalendarSteps(read, send, source, key)))
                .isEqualTo(Arrays.asList(CalendarStep.values()).subList(0, count));
    }
}

package nl.enadeag.carchargeplanner.platform.linux;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.desktop.system.ClockFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class LocaleClockTest {

    private static ClockFormat clockOf(Map<String, String> environment) {
        var given = new ArrayList<ClockFormat>();
        new LocaleClock(environment).follow(given::add);
        assertThat(given).hasSize(1);
        return given.getFirst();
    }

    @ParameterizedTest
    @CsvSource({"en_US.UTF-8, TWELVE_HOUR", "en_US, TWELVE_HOUR", "en_IN.UTF-8, TWELVE_HOUR",
        "en_GB.UTF-8, TWENTY_FOUR_HOUR", "nl_NL.UTF-8, TWENTY_FOUR_HOUR", "nl_NL@euro, TWENTY_FOUR_HOUR",
        "en_IE.UTF-8, TWENTY_FOUR_HOUR"})
    void theTimeLocalesRegionSaysTheClock(String timeLocale, ClockFormat clock) {
        assertThat(clockOf(Map.of("LC_TIME", timeLocale, "LANG", "nl_NL.UTF-8"))).isEqualTo(clock);
    }

    @Test
    void lcAllComesBeforeLcTimeAndLcTimeBeforeLang() {
        assertThat(clockOf(Map.of("LC_ALL", "en_US.UTF-8", "LC_TIME", "en_GB.UTF-8", "LANG", "en_GB.UTF-8")))
                .isEqualTo(ClockFormat.TWELVE_HOUR);
        assertThat(clockOf(Map.of("LC_TIME", "en_US.UTF-8", "LANG", "en_GB.UTF-8")))
                .isEqualTo(ClockFormat.TWELVE_HOUR);
        assertThat(clockOf(Map.of("LANG", "en_US.UTF-8"))).isEqualTo(ClockFormat.TWELVE_HOUR);
    }

    @Test
    void anEmptyVariableIsPassedOver() {
        var environment = new HashMap<String, String>();
        environment.put("LC_ALL", "");
        environment.put("LC_TIME", "en_US.UTF-8");
        environment.put("LANG", "en_GB.UTF-8");

        assertThat(clockOf(environment)).isEqualTo(ClockFormat.TWELVE_HOUR);
    }

    @ParameterizedTest
    @ValueSource(strings = {"C", "C.UTF-8", "POSIX", "zz_ZZ.UTF-8", ".UTF-8"})
    void theCLocaleOrALocaleWithoutDataLeavesTheClockToTheLanguage(String timeLocale) {
        assertThat(clockOf(Map.of("LC_TIME", timeLocale))).isEqualTo(ClockFormat.LANGUAGE);
    }

    @Test
    void withoutAnyLocaleTheClockIsTheLanguages() {
        assertThat(clockOf(Map.of())).isEqualTo(ClockFormat.LANGUAGE);
    }
}

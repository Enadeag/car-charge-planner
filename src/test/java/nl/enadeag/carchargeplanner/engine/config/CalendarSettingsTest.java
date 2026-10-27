package nl.enadeag.carchargeplanner.engine.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting;
import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CalendarSettingsTest {

    private static final LocalTime NINE = LocalTime.of(9, 0);
    private static final LocalTime SIX = LocalTime.of(18, 0);

    @Test
    void theStandardIsAQuarterOfAnHourAndNineToSix() {
        assertThat(CalendarSettings.STANDARD).isEqualTo(new CalendarSettings(Optional.empty(),
                Duration.ofMinutes(15), NINE, SIX));
    }

    @Test
    void theMarginRunsFromNothingToFourHours() {
        assertThat(new CalendarSettings(Optional.empty(), Duration.ZERO, NINE, SIX).travelMargin()).isZero();
        assertThat(new CalendarSettings(Optional.empty(), Duration.ofHours(4), NINE, SIX).travelMargin())
                .isEqualTo(Duration.ofHours(4));
        assertThatThrownBy(() -> new CalendarSettings(Optional.empty(), Duration.ofMinutes(241), NINE, SIX))
                .isInstanceOfSatisfying(InvalidSetting.class, e -> {
                    assertThat(e.setting()).isEqualTo(ConfigSetting.TRAVEL_MARGIN);
                    assertThat(e.rule()).isEqualTo(InvalidSetting.Rule.OUT_OF_RANGE);
                });
        assertThatThrownBy(() -> new CalendarSettings(Optional.empty(), Duration.ofMinutes(-1), NINE, SIX))
                .isInstanceOf(InvalidSetting.class);
    }

    @Test
    void theReturnMustBeAfterTheDeparture() {
        assertThat(new CalendarSettings(Optional.empty(), Duration.ZERO, NINE, NINE.plusMinutes(1)).allDayReturn())
                .isEqualTo(NINE.plusMinutes(1));
        assertThatThrownBy(() -> new CalendarSettings(Optional.empty(), Duration.ZERO, NINE, NINE))
                .isInstanceOfSatisfying(InvalidSetting.class, e -> {
                    assertThat(e.setting()).isEqualTo(ConfigSetting.ALL_DAY_TIMES);
                    assertThat(e.rule()).isEqualTo(InvalidSetting.Rule.NOT_AFTER_DEPARTURE);
                });
    }

    @Test
    void theFileMustBeAWholePath() {
        var whole = Path.of("calendar.ics").toAbsolutePath();
        assertThat(new CalendarSettings(Optional.of(whole), Duration.ZERO, NINE, SIX).icsFile()).contains(whole);
        assertThatThrownBy(() -> new CalendarSettings(Optional.of(Path.of("calendar.ics")), Duration.ZERO, NINE, SIX))
                .isInstanceOfSatisfying(InvalidSetting.class, e -> {
                    assertThat(e.setting()).isEqualTo(ConfigSetting.CALENDAR_FILE);
                    assertThat(e.rule()).isEqualTo(InvalidSetting.Rule.NOT_A_FULL_PATH);
                });
    }

    @Test
    void theConfigFileHoldsWholeMinutesOnly() {
        assertThatThrownBy(() -> new CalendarSettings(Optional.empty(), Duration.ofSeconds(90), NINE, SIX))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CalendarSettings(Optional.empty(), Duration.ZERO, NINE.plusSeconds(1), SIX))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CalendarSettings(Optional.empty(), Duration.ZERO, NINE, SIX.plusNanos(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

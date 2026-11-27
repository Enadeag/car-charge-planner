package nl.enadeag.carchargeplanner.android.form

import java.util.Locale
import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class LimitTest {

    private val english = NumberText(Locale.ENGLISH)
    private val dutch = NumberText(Locale.forLanguageTag("nl"))

    /** The household use is typed per day and the shares in %, where the engine keeps kW and shares of 1. */
    @Test
    fun aLimitIsInTheUnitItsFieldShows() {
        assertThat(Limit.of(ConfigSetting.HOUSE_BASELOAD)).isEqualTo(Limit.atLeast(0.0))
        assertThat(Limit.of(ConfigSetting.CHARGER_EFFICIENCY)).isEqualTo(Limit.fromTo(1.0, 100.0))
        assertThat(Limit.of(ConfigSetting.CAR_BATTERY)).isEqualTo(Limit.fromTo(1.0, 1000.0))
        assertThat(Limit.of(ConfigSetting.CAR_TRIP_SURCHARGE)).isEqualTo(Limit.fromTo(0.0, 100.0))
        assertThat(Limit.of(ConfigSetting.PLANNING_MARGIN)).isEqualTo(Limit(0.0, false, 100.0, true))
        assertThat(Limit.of(ConfigSetting.PANEL_LOSSES)).isEqualTo(Limit(0.0, true, 100.0, false))
        assertThat(Limit.of(ConfigSetting.MINIMUM_WINDOW))
            .isEqualTo(Limit(0.0, false, Double.POSITIVE_INFINITY, false))
    }

    @ParameterizedTest
    @EnumSource(value = ConfigSetting::class, names = ["PANEL_COUNT", "PANEL_TILT", "NOTIFY_LEAD", "CHARGING_HOURS"])
    fun aSettingThatIsNotATypedNumberHasNoLimit(setting: ConfigSetting) {
        assertThat(Limit.of(setting)).isNull()
    }

    @Test
    fun eachShapeHasItsOwnSentence() {
        assertThat(Limit.fromTo(1.0, 80.0).shape).isEqualTo(Limit.Shape.FROM_TO)
        assertThat(Limit.atLeast(0.0).shape).isEqualTo(Limit.Shape.AT_LEAST)
        assertThat(Limit.of(ConfigSetting.MINIMUM_WINDOW)!!.shape).isEqualTo(Limit.Shape.MORE_THAN)
        assertThat(Limit.of(ConfigSetting.PLANNING_MARGIN)!!.shape).isEqualTo(Limit.Shape.MORE_THAN_AT_MOST)
        assertThat(Limit.of(ConfigSetting.PANEL_LOSSES)!!.shape).isEqualTo(Limit.Shape.AT_LEAST_BELOW)
    }

    @Test
    fun theEndsAreWrittenAsTheFieldWritesNumbersWithoutDecimalsTheyDoNotNeed() {
        assertThat(Limit.fromTo(1.0, 1000.0).written(english)).containsExactly("1", "1000")
        assertThat(Limit.fromTo(-90.0, 90.0).written(dutch)).containsExactly("-90", "90")
        assertThat(Limit.fromTo(0.5, 2.25).written(dutch)).containsExactly("0,5", "2,25")
        assertThat(Limit.atLeast(0.0).written(english)).containsExactly("0")
    }

    @Test
    fun endsThatMakeNoLimitAreRefused() {
        assertThatIllegalArgumentException().isThrownBy { Limit.fromTo(5.0, 5.0) }
        assertThatIllegalArgumentException().isThrownBy { Limit.atLeast(Double.NEGATIVE_INFINITY) }
        assertThatIllegalArgumentException().isThrownBy { Limit(0.0, true, Double.POSITIVE_INFINITY, true) }
    }
}

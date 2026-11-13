package nl.enadeag.carchargeplanner.buildlogic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.gradle.api.GradleException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class AppVersionTest {

    @ParameterizedTest
    @CsvSource({
            "0.1.0, 100",
            "0.1.0-SNAPSHOT, 100",
            "0.0.1, 1",
            "0.1.1, 101",
            "0.2.0, 200",
            "1.0.0, 10000",
            "1.2.3, 10203",
            "2.99.99, 29999",
            "10.0.0, 100000",
            "210000.0.0, 2100000000",
    })
    void theCodeIsMajorTimesTenThousandPlusMinorTimesAHundredPlusPatch(String name, int code) {
        assertThat(AppVersion.versionCode(name)).isEqualTo(code);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.100.0", "0.1.100", "1.100.99", "1.99.100"})
    void aMinorOrPatchAbove99IsRefused(String name) {
        assertThatThrownBy(() -> AppVersion.versionCode(name)).isInstanceOf(GradleException.class)
                .hasMessageContaining("'" + name + "'").hasMessageContaining("above 99");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "0.1", "0.1.0.0", "v0.1.0", "android-v0.1.0", "0.1.0-rc1", "0.1.0-snapshot",
        "0.1.0-SNAPSHOT-SNAPSHOT", "0.01.0", "00.1.0", "0.1.00", "0.1.0 ", " 0.1.0", "0.1.0\n", "0.1.x", "-1.0.0",
        "0.1.0+1", "1234567890.0.0", "\u0660.\u0661.\u0660"})
    void anyOtherFormIsRefused(String name) {
        assertThatThrownBy(() -> AppVersion.versionCode(name)).isInstanceOf(GradleException.class)
                .hasMessageContaining("is not X.Y.Z or X.Y.Z-SNAPSHOT");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.0.0", "0.0.0-SNAPSHOT", "210000.0.1", "999999999.0.0"})
    void aCodeOfZeroOrAbovePlaysLimitIsRefused(String name) {
        assertThatThrownBy(() -> AppVersion.versionCode(name)).isInstanceOf(GradleException.class)
                .hasMessageContaining("must be from 1 up to 2100000000");
    }
}

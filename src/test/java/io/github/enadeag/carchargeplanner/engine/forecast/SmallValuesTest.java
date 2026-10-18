package io.github.enadeag.carchargeplanner.engine.forecast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SmallValuesTest {

    @Test
    void aByteSizeIsAtLeastOneByte() {
        assertThat(new ByteSize(1).bytes()).isEqualTo(1);
        assertThatThrownBy(() -> new ByteSize(0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(ByteSize.ofKilobytes(2).bytes()).isEqualTo(2048);
        assertThat(ByteSize.ofMegabytes(2).bytes()).isEqualTo(2 * 1024 * 1024);
    }

    @Test
    void aSecretSettingThatIsBlankHasNoValueToHide() {
        var info = FakeProvider.info("needy", "Needy", List.of(
                new SettingSpec("api_key", "API key", "Your key.", false, true),
                new SettingSpec("token", "Token", "Your token.", false, true),
                new SettingSpec("site", "Site", "Your site.", false, false)));

        assertThat(info.secretValues(Map.of("api_key", "  ", "token", "T-1", "site", "home"))).containsExactly("T-1");
    }
}

package nl.enadeag.carchargeplanner.desktop.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AppIconTest {

    @Test
    void theAppHasTheIconInEverySize() {
        var icon = new AppIcon();

        for (var size : AppIcon.SIZES) {
            var image = icon.image(size);
            assertThat(image.getWidth()).isEqualTo(size);
            assertThat(image.getHeight()).isEqualTo(size);
        }
    }

    @Test
    void aSizeTheIconIsNotDrawnInIsRefused() {
        assertThatThrownBy(() -> new AppIcon().image(20)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("no icon of 20 px");
    }
}

package nl.enadeag.carchargeplanner.platform.windows;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.desktop.system.PanelShade;
import nl.enadeag.carchargeplanner.desktop.system.StatusDot;
import nl.enadeag.carchargeplanner.desktop.system.StatusIcon;
import nl.enadeag.carchargeplanner.desktop.system.TrayStatus;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class TrayLookTest {

    private final StatusIcon icon = new StatusIcon();
    private final AtomicReference<PanelShade> taskbar = new AtomicReference<>(PanelShade.DARK);
    private final TrayLook look = new TrayLook(icon, taskbar::get, "Car charge planner");

    @Test
    void withoutASentenceTheTooltipIsTheTitle() {
        assertThat(look.tooltip(new TrayStatus(StatusDot.NONE, ""))).isEqualTo("Car charge planner");
    }

    @Test
    void theStatusSentenceIsTheTooltipsSecondLine() {
        assertThat(look.tooltip(new TrayStatus(StatusDot.SUN, "Charge on sun now, until 14:30, at 10 A.")))
                .isEqualTo("Car charge planner\nCharge on sun now, until 14:30, at 10 A.");
    }

    @Test
    void aTooLongTooltipIsCutToWhatWindowsShowsWithAnEllipsis() {
        var tooltip = look.tooltip(new TrayStatus(StatusDot.GRID, "x".repeat(200)));

        assertThat(tooltip).hasSize(TrayLook.TOOLTIP_MAX)
                .isEqualTo("Car charge planner\n" + "x".repeat(TrayLook.TOOLTIP_MAX - 20) + "…");
    }

    @Test
    void theIconComesInEveryNotificationAreaSize() {
        var images = look.images(new TrayStatus(StatusDot.SUN, ""));

        assertThat(images).extracting(BufferedImage::getWidth).containsExactlyElementsOf(TrayLook.SIZES);
        assertThat(images).extracting(BufferedImage::getHeight).containsExactlyElementsOf(TrayLook.SIZES);
    }

    @Test
    void theIconIsDrawnForTheTaskbarsShadeAsItIsNow() {
        var status = new TrayStatus(StatusDot.GRID, "");

        var onDark = look.images(status);
        taskbar.set(PanelShade.LIGHT);
        var onLight = look.images(status);

        assertThat(pixels(onDark)).isEqualTo(pixels(drawn(status, PanelShade.DARK)));
        assertThat(pixels(onLight)).isEqualTo(pixels(drawn(status, PanelShade.LIGHT)));
        assertThat(pixels(onLight)).isNotEqualTo(pixels(onDark));
    }

    private List<BufferedImage> drawn(TrayStatus status, PanelShade shade) {
        return TrayLook.SIZES.stream().map(size -> icon.image(status.dot(), shade, size)).toList();
    }

    private static List<List<Integer>> pixels(List<BufferedImage> images) {
        return images.stream().map(image -> {
            var argb = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
            return Arrays.stream(argb).boxed().toList();
        }).toList();
    }
}

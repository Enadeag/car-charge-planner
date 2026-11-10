package nl.enadeag.carchargeplanner.platform.windows;

import nl.enadeag.carchargeplanner.desktop.system.PanelShade;
import nl.enadeag.carchargeplanner.desktop.system.StatusIcon;
import nl.enadeag.carchargeplanner.desktop.system.TrayStatus;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.Supplier;

final class TrayLook {

    /**
     * The notification area's icon size at 100, 125, 150, 175, 200, 250 and 300 % and beyond. AWT draws the icon at
     * 16 px times the display's scale and takes the image of exactly that size from these, so none is scaled.
     */
    static final List<Integer> SIZES = List.of(16, 20, 24, 28, 32, 40, 48, 64);
    /** Windows cuts a tooltip off after 127 characters. */
    static final int TOOLTIP_MAX = 127;

    private final StatusIcon icon;
    private final Supplier<PanelShade> taskbar;
    private final String title;

    /** The taskbar's shade is asked again for each set of images. */
    TrayLook(StatusIcon icon, Supplier<PanelShade> taskbar, String title) {
        this.icon = icon;
        this.taskbar = taskbar;
        this.title = title;
    }

    /**
     * The icon for the status in every size of {@link #SIZES}, drawn for the taskbar's shade as it is now.
     *
     * @return one image per size, smallest first
     */
    List<BufferedImage> images(TrayStatus status) {
        var shade = taskbar.get();
        return SIZES.stream().map(size -> icon.image(status.dot(), shade, size)).toList();
    }

    /**
     * The tooltip for the status: the title, and the status's sentence on the next line when it has one.
     *
     * @return the text, shortened with an ellipsis to {@link #TOOLTIP_MAX} characters
     */
    String tooltip(TrayStatus status) {
        var text = status.text().isEmpty() ? title : title + "\n" + status.text();
        return text.length() <= TOOLTIP_MAX ? text : text.substring(0, TOOLTIP_MAX - 1) + "…";
    }
}

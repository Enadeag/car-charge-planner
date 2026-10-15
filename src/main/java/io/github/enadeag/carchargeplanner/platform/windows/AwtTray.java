package io.github.enadeag.carchargeplanner.platform.windows;

import io.github.enadeag.carchargeplanner.desktop.StatusDot;
import io.github.enadeag.carchargeplanner.desktop.StatusIcon;
import io.github.enadeag.carchargeplanner.desktop.Tray;
import io.github.enadeag.carchargeplanner.desktop.TrayStatus;
import java.awt.AWTException;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BaseMultiResolutionImage;
import java.util.List;
import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Point2D;

/** The tray icon on Windows, through AWT. */
public final class AwtTray implements Tray {

    /**
     * The notification area's icon size at 100, 125, 150, 175, 200, 250 and 300 % and beyond. AWT draws the icon at
     * 16 px times the display's scale and takes the image of exactly that size from these, so none is scaled.
     */
    private static final List<Integer> SIZES = List.of(16, 20, 24, 28, 32, 40, 48, 64);
    /** Windows cuts a tooltip off after 127 characters. */
    private static final int TOOLTIP_MAX = 127;

    private final StatusIcon icon;
    private final TaskbarShade taskbar;
    private final ObservableValue<ColorScheme> colorScheme;
    private final String title;
    private TrayStatus status = new TrayStatus(StatusDot.NONE, "");
    private TrayIcon trayIcon;

    /**
     * Makes the tray; nothing appears until {@link #show(TrayActions)}.
     *
     * @param icon draws the icon with its status dot, in the sizes the display's scale may ask for
     * @param taskbar tells whether the icon sits on a light or a dark taskbar; read again whenever the icon changes
     * @param colorScheme the system's light or dark ({@code Platform.getPreferences().colorSchemeProperty()}); when it
     *     changes the icon is drawn again, because Windows' own light and dark setting switches the taskbar too
     * @param title the first line of the icon's tooltip
     */
    public AwtTray(StatusIcon icon, TaskbarShade taskbar, ObservableValue<ColorScheme> colorScheme, String title) {
        this.icon = icon;
        this.taskbar = taskbar;
        this.colorScheme = colorScheme;
        this.title = title;
    }

    @Override
    public synchronized void show(TrayActions actions) throws TrayUnavailable {
        // without a desktop (a service, or an ssh session) even making the menu fails
        if (GraphicsEnvironment.isHeadless() || !SystemTray.isSupported()) {
            throw new TrayUnavailable("Windows has no notification area for this app", null);
        }
        var menu = new PopupMenu();
        menu.add(menuItem("Open dashboard", actions.open()));
        menu.addSeparator();
        menu.add(menuItem("Quit", actions.quit()));
        trayIcon = new TrayIcon(image(), tooltip(), menu);
        trayIcon.setImageAutoSize(true);
        trayIcon.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getButton() == MouseEvent.BUTTON1) {
                    actions.clicked().accept(new Point2D(event.getXOnScreen(), event.getYOnScreen()));
                }
            }
        });
        try {
            SystemTray.getSystemTray().add(trayIcon);
        } catch (AWTException e) {
            throw new TrayUnavailable("Windows has no notification area for this app", e);
        }
        // JavaFX's properties belong to its thread
        Platform.runLater(() -> colorScheme.subscribe(scheme -> redraw()));
    }

    @Override
    public synchronized void showStatus(TrayStatus status) {
        this.status = status;
        redraw();
    }

    @Override
    public synchronized void remove() {
        if (trayIcon != null) {
            SystemTray.getSystemTray().remove(trayIcon);
        }
    }

    private synchronized void redraw() {
        if (trayIcon != null) {
            trayIcon.setImage(image());
            trayIcon.setToolTip(tooltip());
        }
    }

    /** The icon in every size, for AWT to pick the one of the display's scale from. */
    private Image image() {
        var shade = taskbar.read();
        return new BaseMultiResolutionImage(
                SIZES.stream().map(size -> icon.image(status.dot(), shade, size)).toArray(Image[]::new));
    }

    private String tooltip() {
        var text = status.text().isEmpty() ? title : title + "\n" + status.text();
        return text.length() <= TOOLTIP_MAX ? text : text.substring(0, TOOLTIP_MAX - 1) + "…";
    }

    private static MenuItem menuItem(String label, Runnable action) {
        var item = new MenuItem(label);
        item.addActionListener(event -> action.run());
        return item;
    }
}

package io.github.enadeag.carchargeplanner.platform.windows;

import io.github.enadeag.carchargeplanner.desktop.NotificationText;
import io.github.enadeag.carchargeplanner.desktop.Notifications;
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
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BaseMultiResolutionImage;
import java.time.Duration;
import java.util.List;
import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Point2D;

/**
 * The tray icon on Windows, through AWT, and the app's notifications as the icon's messages: Windows shows them as
 * toasts, and a click on one reaches the icon's action listener.
 */
public final class AwtTray implements Tray, Notifications {

    /**
     * The notification area's icon size at 100, 125, 150, 175, 200, 250 and 300 % and beyond. AWT draws the icon at
     * 16 px times the display's scale and takes the image of exactly that size from these, so none is scaled.
     */
    private static final List<Integer> SIZES = List.of(16, 20, 24, 28, 32, 40, 48, 64);
    /** Windows cuts a tooltip off after 127 characters. */
    private static final int TOOLTIP_MAX = 127;
    /**
     * Where Enter on the icon opens the popup: Windows does not say where the icon is, and a place off every screen
     * puts the popup in the primary screen's bottom right, where the notification area is unless it was moved.
     */
    private static final Point2D BY_KEYBOARD = new Point2D(-1_000_000, -1_000_000);
    /** Windows' own default; AWT names the system's setting when it can. */
    private static final Duration DOUBLE_CLICK = Duration.ofMillis(500);

    private final StatusIcon icon;
    private final TaskbarShade taskbar;
    private final ObservableValue<ColorScheme> colorScheme;
    private final String title;
    private MenuLabels labels;
    private List<MenuItem> items = List.of();
    private TrayStatus status = new TrayStatus(StatusDot.NONE, "");
    private TrayIcon trayIcon;
    private final TrayMessages messages = new TrayMessages();

    /**
     * Makes the tray; nothing appears until {@link #show(TrayActions)}, and its menu needs
     * {@link #showLabels(MenuLabels)} first.
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

    /**
     * {@inheritDoc}
     *
     * @throws IllegalStateException when the menu has no labels yet: {@link #showLabels(MenuLabels)} comes first
     */
    @Override
    public synchronized void show(TrayActions actions) throws TrayUnavailable {
        if (labels == null) {
            throw new IllegalStateException("the menu needs its labels before the tray shows");
        }
        // without a desktop (a service, or an ssh session) even making the menu fails
        if (GraphicsEnvironment.isHeadless() || !SystemTray.isSupported()) {
            throw new TrayUnavailable("Windows has no notification area for this app", null);
        }
        items = List.of(menuItem(actions.open()), menuItem(actions.settings()), menuItem(actions.about()),
                menuItem(actions.quit()));
        relabel();
        var menu = new PopupMenu();
        menu.add(items.get(0));
        menu.add(items.get(1));
        menu.add(items.get(2));
        menu.addSeparator();
        menu.add(items.get(3));
        trayIcon = new TrayIcon(image(), tooltip(), menu);
        trayIcon.setImageAutoSize(true);
        var action = new TrayIconAction(doubleClickTime());
        trayIcon.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                action.pressed(event.getWhen());
            }

            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getButton() == MouseEvent.BUTTON1) {
                    actions.clicked().accept(new Point2D(event.getXOnScreen(), event.getYOnScreen()));
                }
            }
        });
        // a click on a message, a double click on the icon, or Enter on it after Win+B: the popup opens for the last
        trayIcon.addActionListener(event -> {
            if (!messages.click() && action.byKeyboard(event.getWhen())) {
                actions.clicked().accept(BY_KEYBOARD);
            }
        });
        try {
            SystemTray.getSystemTray().add(trayIcon);
        } catch (AWTException e) {
            throw new TrayUnavailable("Windows has no notification area for this app", e);
        }
        var shown = trayIcon;
        messages.attach((title, body) -> shown.displayMessage(title, body, TrayIcon.MessageType.INFO));
        // JavaFX's properties belong to its thread
        Platform.runLater(() -> colorScheme.subscribe(scheme -> redraw()));
    }

    @Override
    public synchronized void showStatus(TrayStatus status) {
        this.status = status;
        redraw();
    }

    @Override
    public synchronized void showLabels(MenuLabels labels) {
        this.labels = labels;
        relabel();
    }

    @Override
    public synchronized void remove() {
        messages.detach();
        if (trayIcon != null) {
            SystemTray.getSystemTray().remove(trayIcon);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>The action runs on AWT's event thread.
     */
    @Override
    public void listen(Runnable clicked) {
        messages.listen(clicked);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Shown only while the icon is in the tray, between {@link #show(TrayActions)} and {@link #remove()}.
     */
    @Override
    public void show(NotificationText text) {
        messages.show(text);
    }

    /** Nothing to close: the messages go with the icon, at {@link #remove()}. */
    @Override
    public void close() {
    }

    private synchronized void redraw() {
        if (trayIcon != null) {
            trayIcon.setImage(image());
            trayIcon.setToolTip(tooltip());
        }
    }

    private static Duration doubleClickTime() {
        return Toolkit.getDefaultToolkit().getDesktopProperty("awt.multiClickInterval") instanceof Integer millis
                ? Duration.ofMillis(millis) : DOUBLE_CLICK;
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

    /** The menu's entries, once they are made, in the order of {@link MenuLabels}. */
    private void relabel() {
        if (!items.isEmpty()) {
            var texts = List.of(labels.open(), labels.settings(), labels.about(), labels.quit());
            for (int i = 0; i < items.size(); i++) {
                items.get(i).setLabel(texts.get(i));
            }
        }
    }

    private static MenuItem menuItem(Runnable action) {
        var item = new MenuItem();
        item.addActionListener(event -> action.run());
        return item;
    }
}

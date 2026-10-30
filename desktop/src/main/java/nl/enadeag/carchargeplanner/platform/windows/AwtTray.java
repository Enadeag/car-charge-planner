package nl.enadeag.carchargeplanner.platform.windows;

import nl.enadeag.carchargeplanner.desktop.system.NotificationTarget;
import nl.enadeag.carchargeplanner.desktop.system.NotificationText;
import nl.enadeag.carchargeplanner.desktop.system.Notifications;
import nl.enadeag.carchargeplanner.desktop.system.StatusDot;
import nl.enadeag.carchargeplanner.desktop.system.StatusIcon;
import nl.enadeag.carchargeplanner.desktop.system.Tray;
import nl.enadeag.carchargeplanner.desktop.system.TrayMenuEntry;
import nl.enadeag.carchargeplanner.desktop.system.TrayStatus;
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
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;
import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Point2D;

/**
 * The tray icon on Windows, through AWT, and the app's notifications as the icon's messages: Windows shows them as
 * toasts, and a click on one reaches the icon's action listener. What the icon shows and what its menu holds are
 * decided by {@link TrayLook} and {@link TrayMenuEntry}; this class only hands them to AWT.
 */
public final class AwtTray implements Tray, Notifications {

    /**
     * Where Enter on the icon opens the popup: Windows does not say where the icon is, and a place off every screen
     * puts the popup in the primary screen's bottom right, where the notification area is unless it was moved.
     */
    private static final Point2D BY_KEYBOARD = new Point2D(-1_000_000, -1_000_000);
    /** Windows' own default; AWT names the system's setting when it can. */
    private static final Duration DOUBLE_CLICK = Duration.ofMillis(500);

    private final TrayLook look;
    private final ObservableValue<ColorScheme> colorScheme;
    private MenuLabels labels;
    private final Map<TrayMenuEntry, MenuItem> items = new EnumMap<>(TrayMenuEntry.class);
    private TrayStatus status = new TrayStatus(StatusDot.NONE, "");
    private TrayIcon trayIcon;
    private final TrayMessages messages = new TrayMessages();

    /**
     * Makes the tray; nothing appears until {@link #add(Actions)}, and its menu needs
     * {@link #setMenuLabels(MenuLabels)} first.
     *
     * @param icon draws the icon with its status dot, in the sizes the display's scale may ask for
     * @param taskbar tells whether the icon sits on a light or a dark taskbar; read again whenever the icon changes
     * @param colorScheme the system's light or dark ({@code Platform.getPreferences().colorSchemeProperty()}); when it
     *     changes the icon is drawn again, because Windows' own light and dark setting switches the taskbar too
     * @param title the first line of the icon's tooltip
     */
    public AwtTray(StatusIcon icon, TaskbarShade taskbar, ObservableValue<ColorScheme> colorScheme, String title) {
        this.look = new TrayLook(icon, taskbar::read, title);
        this.colorScheme = colorScheme;
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalStateException when the menu has no labels yet: {@link #setMenuLabels(MenuLabels)} comes first
     */
    @Override
    public synchronized void add(Actions actions) throws TrayUnavailable {
        if (labels == null) {
            throw new IllegalStateException("the menu needs its labels before the tray shows");
        }
        // without a desktop (a service, or an ssh session) even making the menu fails
        if (GraphicsEnvironment.isHeadless() || !SystemTray.isSupported()) {
            throw new TrayUnavailable("Windows has no notification area for this app", null);
        }
        var menu = new PopupMenu();
        for (var entry : TrayMenuEntry.values()) {
            if (entry.separated()) {
                menu.addSeparator();
            }
            var item = new MenuItem(entry.label(labels));
            var action = entry.action(actions);
            item.addActionListener(_ -> action.run());
            items.put(entry, item);
            menu.add(item);
        }
        trayIcon = new TrayIcon(image(), look.tooltip(status), menu);
        trayIcon.setImageAutoSize(true);
        var source = new TrayActionSource(doubleClickTime());
        trayIcon.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                source.recordPress(event.getWhen());
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
            if (!messages.claimClick() && source.cameFromKeyboard(event.getWhen())) {
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
        Platform.runLater(() -> colorScheme.subscribe(_ -> refreshIconAndTooltip()));
    }

    @Override
    public synchronized void setStatus(TrayStatus status) {
        this.status = status;
        refreshIconAndTooltip();
    }

    @Override
    public synchronized void setMenuLabels(MenuLabels labels) {
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
    public void onClick(Consumer<NotificationTarget> clicked) {
        messages.onClick(clicked);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Shown only while the icon is in the tray, between {@link #add(Actions)} and {@link #remove()}.
     */
    @Override
    public void send(NotificationText text) {
        messages.show(text);
    }

    /** Stops passing clicks on: the messages themselves go with the icon, at {@link #remove()}. */
    @Override
    public void close() {
        messages.stopPassingClicks();
    }

    private synchronized void refreshIconAndTooltip() {
        if (trayIcon != null) {
            trayIcon.setImage(image());
            trayIcon.setToolTip(look.tooltip(status));
        }
    }

    private static Duration doubleClickTime() {
        return Toolkit.getDefaultToolkit().getDesktopProperty("awt.multiClickInterval") instanceof Integer millis
                ? Duration.ofMillis(millis) : DOUBLE_CLICK;
    }

    /** The icon in every size, for AWT to pick the one of the display's scale from. */
    private Image image() {
        return new BaseMultiResolutionImage(look.images(status).toArray(Image[]::new));
    }

    /** Gives the menu's entries the current labels; does nothing before {@link #add(Actions)} made them. */
    private void relabel() {
        items.forEach((entry, item) -> item.setLabel(entry.label(labels)));
    }
}

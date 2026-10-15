package io.github.enadeag.carchargeplanner.platform.windows;

import io.github.enadeag.carchargeplanner.desktop.AppIcon;
import io.github.enadeag.carchargeplanner.desktop.Tray;
import java.awt.AWTException;
import java.awt.GraphicsEnvironment;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/** The tray icon on Windows, through AWT. */
public final class AwtTray implements Tray {

    private final AppIcon icon;
    private final String title;
    private TrayIcon trayIcon;

    public AwtTray(AppIcon icon, String title) {
        this.icon = icon;
        this.title = title;
    }

    @Override
    public void show(TrayActions actions) throws TrayUnavailable {
        // without a desktop (a service, or an ssh session) even making the menu fails
        if (GraphicsEnvironment.isHeadless() || !SystemTray.isSupported()) {
            throw new TrayUnavailable("Windows has no notification area for this app", null);
        }
        var menu = new PopupMenu();
        menu.add(menuItem("Open dashboard", actions.open()));
        menu.addSeparator();
        menu.add(menuItem("Quit", actions.quit()));
        // a large image that AWT scales down stays sharp at 125 and 150 % too
        trayIcon = new TrayIcon(icon.image(64), title, menu);
        trayIcon.setImageAutoSize(true);
        trayIcon.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getButton() == MouseEvent.BUTTON1) {
                    actions.open().run();
                }
            }
        });
        try {
            SystemTray.getSystemTray().add(trayIcon);
        } catch (AWTException e) {
            throw new TrayUnavailable("Windows has no notification area for this app", e);
        }
    }

    @Override
    public void remove() {
        if (trayIcon != null) {
            SystemTray.getSystemTray().remove(trayIcon);
        }
    }

    private static MenuItem menuItem(String label, Runnable action) {
        var item = new MenuItem(label);
        item.addActionListener(event -> action.run());
        return item;
    }
}

package spike;

import java.awt.AWTException;
import java.awt.Font;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalTime;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Question 1a / 2 / 3 (Windows): plain AWT SystemTray. Left click opens a window, right click a menu,
 * the icon toggles a green status dot every 5 s, "Notify" shows TrayIcon.displayMessage and logs a
 * click on the balloon (Windows sends it as an ActionEvent on the TrayIcon).
 */
public class AwtTray {
    static JFrame frame;
    static boolean dot;

    static void log(String s) {
        System.out.println(LocalTime.now().withNano(0) + " " + s);
        System.out.flush();
    }

    public static void main(String[] args) throws AWTException {
        log("java " + Runtime.version() + ", toolkit " + Toolkit.getDefaultToolkit().getClass().getName()
                + ", SystemTray.isSupported=" + SystemTray.isSupported());
        if (!SystemTray.isSupported()) {
            log("no system tray; exiting");
            System.exit(1);
        }
        var tray = SystemTray.getSystemTray();
        var size = tray.getTrayIconSize();
        log("tray icon size reported by AWT: " + size.width + "x" + size.height
                + ", screen dpi " + Toolkit.getDefaultToolkit().getScreenResolution());

        var menu = new PopupMenu();
        var open = new MenuItem("Open window");
        var toggle = new MenuItem("Toggle status dot");
        var notify = new MenuItem("Show notification");
        var quit = new MenuItem("Quit");
        menu.add(open);
        menu.add(toggle);
        menu.add(notify);
        menu.addSeparator();
        menu.add(quit);

        // Hand AWT a large image and let it scale: on Windows at 150% this gives a sharper icon than 16px.
        var icon = new TrayIcon(Icons.icon(64, null), "Car charge planner (AWT)", menu);
        icon.setImageAutoSize(true);
        icon.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                log("mouseClicked button=" + e.getButton() + " count=" + e.getClickCount());
                if (e.getButton() == MouseEvent.BUTTON1) {
                    showWindow();
                }
            }
        });
        icon.addActionListener(e -> log("TrayIcon ActionEvent (double click or balloon click) cmd=" + e.getActionCommand()));
        open.addActionListener(e -> { log("menu: open"); showWindow(); });
        toggle.addActionListener(e -> { log("menu: toggle"); toggleDot(icon); });
        notify.addActionListener(e -> {
            log("menu: notify");
            icon.displayMessage("Charge window starts", "Today 11:30-15:00, about 18 kWh from the sun",
                    TrayIcon.MessageType.INFO);
        });
        quit.addActionListener(e -> System.exit(0));
        tray.add(icon);
        log("icon added");

        new Timer(5000, e -> toggleDot(icon)).start();
        if (args.length > 0 && args[0].equals("--notify-after-3s")) {
            new Timer(3000, e -> {
                log("auto notify");
                icon.displayMessage("Charge window starts", "Today 11:30-15:00, about 18 kWh from the sun",
                        TrayIcon.MessageType.INFO);
                ((Timer) e.getSource()).stop();
            }).start();
        }
    }

    static void toggleDot(TrayIcon icon) {
        dot = !dot;
        icon.setImage(Icons.icon(64, dot ? Icons.GREEN : null));
        log("icon changed, dot=" + dot);
    }

    static void showWindow() {
        SwingUtilities.invokeLater(() -> {
            if (frame == null) {
                frame = new JFrame("Car charge planner (AWT tray spike)");
                var label = new JLabel("Opened from the tray icon", SwingConstants.CENTER);
                label.setFont(label.getFont().deriveFont(Font.PLAIN, 18f));
                frame.add(label);
                frame.setSize(420, 200);
                frame.setLocationRelativeTo(null);
                frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
            }
            frame.setVisible(true);
            frame.toFront();
            log("window shown");
        });
    }
}

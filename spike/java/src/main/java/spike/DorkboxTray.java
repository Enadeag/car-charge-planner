package spike;

import dorkbox.systemTray.MenuItem;
import dorkbox.systemTray.Separator;
import dorkbox.systemTray.SystemTray;
import java.time.LocalTime;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Question 1b: dorkbox SystemTray. On Linux it picks GTK AppIndicator (via JNA) when it can.
 * AppIndicator has no left-click event: a left click opens the same menu.
 * Build and run with the profile: ./mvnw -q -Pdorkbox compile exec:exec -Dmain=spike.DorkboxTray
 */
public class DorkboxTray {
    static boolean dot;

    static void log(String s) {
        System.out.println(LocalTime.now().withNano(0) + " " + s);
        System.out.flush();
    }

    public static void main(String[] args) {
        dorkbox.systemTray.SystemTray.DEBUG = true;
        var tray = SystemTray.get("car-charge-planner-spike");
        if (tray == null) {
            log("dorkbox: no tray available");
            System.exit(1);
        }
        log("dorkbox tray type: " + tray.getType() + ", menu entry image size " + tray.getMenuImageSize()
                + ", tray image size " + tray.getTrayImageSize());
        tray.setImage(Icons.icon(64, null));
        tray.setTooltip("Car charge planner (dorkbox)");
        tray.setStatus("Status: idle");
        var menu = tray.getMenu();
        menu.add(new MenuItem("Open window", e -> log("menu: open window")));
        menu.add(new MenuItem("Toggle status dot", e -> log("menu: toggle")));
        menu.add(new Separator());
        menu.add(new MenuItem("Quit", e -> { tray.shutdown(); System.exit(0); }));

        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            dot = !dot;
            tray.setImage(Icons.icon(64, dot ? Icons.GREEN : null));
            tray.setStatus(dot ? "Status: charging" : "Status: idle");
            log("icon changed, dot=" + dot);
        }, 5, 5, TimeUnit.SECONDS);
    }
}

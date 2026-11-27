package nl.enadeag.carchargeplanner.platform.linux;

import nl.enadeag.carchargeplanner.desktop.system.ClockFormat;
import nl.enadeag.carchargeplanner.desktop.system.ClockSetting;
import java.lang.System.Logger.Level;
import java.util.function.Consumer;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.types.Variant;

/**
 * GNOME's clock format, {@code clock-format} of {@code org.gnome.desktop.interface}, "12h" or "24h", read through the
 * desktop portal's Settings, which also reaches it from inside a Flatpak sandbox, and followed by its
 * {@code SettingChanged} signal. A desktop without the portal or the setting gives the clock another setting says.
 */
public final class PortalClock implements ClockSetting {

    /** The namespace of GNOME's interface settings. */
    static final String NAMESPACE = "org.gnome.desktop.interface";
    /** The key of the clock format. */
    static final String KEY = "clock-format";

    private static final System.Logger LOG = System.getLogger(PortalClock.class.getName());

    private final BusConnector connector;
    private final ClockSetting otherwise;
    private final Object giving = new Object();
    /** The clock given last; null until the first is given. */
    private ClockFormat last;

    /**
     * Makes the setting; nothing is read yet.
     *
     * @param connector opens the session bus connection, which stays open while the app runs to hear the changes
     * @param otherwise the setting followed instead when the portal or its setting is not there, such as the
     *     environment's time locale
     */
    public PortalClock(BusConnector connector, ClockSetting otherwise) {
        this.connector = connector;
        this.otherwise = otherwise;
    }

    @Override
    public void follow(Consumer<ClockFormat> changed) {
        DBusConnection bus = null;
        try {
            bus = connector.connect();
            // listen before reading, so a change right after the read is not missed
            bus.addSigHandler(SettingsPortal.SettingChanged.class, signal -> {
                if (NAMESPACE.equals(signal.namespace) && KEY.equals(signal.key)) {
                    give(of(signal.value), changed);
                }
            });
            var portal = bus.getRemoteObject(SettingsPortal.BUS_NAME, SettingsPortal.OBJECT_PATH,
                    SettingsPortal.class);
            give(of(read(portal)), changed);
        } catch (DBusException | RuntimeException e) {
            // dbus-java reports a missing portal or setting, an error reply, as a runtime exception
            LOG.log(Level.DEBUG, "cannot read GNOME's clock format through the desktop portal ("
                    + e.getMessage() + "); the time locale says the clock");
            if (bus != null) {
                bus.disconnect();
            }
            otherwise.follow(changed);
        }
    }

    private void give(ClockFormat clock, Consumer<ClockFormat> changed) {
        synchronized (giving) {
            if (clock != last) {
                last = clock;
                changed.accept(clock);
            }
        }
    }

    /** The setting's value, through the method of version 2, or the older one when the portal lacks it. */
    private static Variant<?> read(SettingsPortal portal) {
        try {
            return portal.ReadOne(NAMESPACE, KEY);
        } catch (RuntimeException e) {
            LOG.log(Level.DEBUG, "the desktop portal cannot read one setting (" + e.getMessage()
                    + "); trying its older Read");
            return portal.Read(NAMESPACE, KEY);
        }
    }

    /**
     * The clock a value of the setting stands for.
     *
     * @param value the value, possibly wrapped in further variants, as the older Read gives it
     * @return a 12- or 24-hour clock for "12h" or "24h", otherwise the language's own
     */
    static ClockFormat of(Variant<?> value) {
        Object inside = value;
        while (inside instanceof Variant<?> variant) {
            inside = variant.getValue();
        }
        if ("12h".equals(inside)) {
            return ClockFormat.TWELVE_HOUR;
        }
        if ("24h".equals(inside)) {
            return ClockFormat.TWENTY_FOUR_HOUR;
        }
        return ClockFormat.LANGUAGE;
    }
}

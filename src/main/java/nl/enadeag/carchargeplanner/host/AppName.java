package nl.enadeag.carchargeplanner.host;

/** The app's names, as the owner and other programs see them. */
public final class AppName {

    /** The name the owner sees, in the tray, the windows and the command line's messages. */
    public static final String TITLE = "Car charge planner";
    /** The name other programs see, such as on the keychain's entries and the desktop entry. */
    public static final String ID = "car-charge-planner";
    /**
     * The app's ID as a Flatpak (ADR 0008), which names its menu entry there, so the notifications are tied to it.
     * Provisional until the app is on Flathub; CONTRIBUTING lists every place it appears.
     */
    public static final String FLATPAK_ID = "io.github.enadeag.car_charge_planner.CarChargePlanner";

    private AppName() {
    }
}

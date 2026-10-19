package nl.enadeag.carchargeplanner.platform.windows;

import nl.enadeag.carchargeplanner.desktop.system.Tray.MenuLabels;
import nl.enadeag.carchargeplanner.desktop.system.Tray.Actions;
import java.util.function.Function;

/** The entries of the tray icon's menu, in the order the menu shows them. */
enum TrayMenuEntry {

    /** Opens the dashboard. */
    OPEN(MenuLabels::open, Actions::open, false),
    /** Opens Settings. */
    SETTINGS(MenuLabels::settings, Actions::settings, false),
    /** Opens Settings on About. */
    ABOUT(MenuLabels::about, Actions::about, false),
    /** Quits the app; a line sets it apart from the others, so it is not chosen by mistake. */
    QUIT(MenuLabels::quit, Actions::quit, true);

    private final Function<MenuLabels, String> label;
    private final Function<Actions, Runnable> action;
    private final boolean separated;

    TrayMenuEntry(Function<MenuLabels, String> label, Function<Actions, Runnable> action, boolean separated) {
        this.label = label;
        this.action = action;
        this.separated = separated;
    }

    /**
     * What the entry says.
     *
     * @param labels the menu's labels in the owner's language
     * @return this entry's label among them
     */
    String label(MenuLabels labels) {
        return label.apply(labels);
    }

    /**
     * What a click on the entry does.
     *
     * @param actions the tray's actions
     * @return this entry's action among them
     */
    Runnable action(Actions actions) {
        return action.apply(actions);
    }

    /**
     * Whether a separator line comes before the entry.
     *
     * @return true for the entry set apart from the ones above it
     */
    boolean separated() {
        return separated;
    }
}

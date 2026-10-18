package io.github.enadeag.carchargeplanner.platform.windows;

import io.github.enadeag.carchargeplanner.desktop.system.Tray.MenuLabels;
import io.github.enadeag.carchargeplanner.desktop.system.Tray.TrayActions;
import java.util.function.Function;

/** The entries of the tray icon's menu, in the order the menu shows them. */
enum TrayMenuEntry {

    /** Opens the dashboard. */
    OPEN(MenuLabels::open, TrayActions::open, false),
    /** Opens Settings. */
    SETTINGS(MenuLabels::settings, TrayActions::settings, false),
    /** Opens Settings on About. */
    ABOUT(MenuLabels::about, TrayActions::about, false),
    /** Quits the app; a line sets it apart from the others, so it is not chosen by mistake. */
    QUIT(MenuLabels::quit, TrayActions::quit, true);

    private final Function<MenuLabels, String> label;
    private final Function<TrayActions, Runnable> action;
    private final boolean separated;

    TrayMenuEntry(Function<MenuLabels, String> label, Function<TrayActions, Runnable> action, boolean separated) {
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
    Runnable action(TrayActions actions) {
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

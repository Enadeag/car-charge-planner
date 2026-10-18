package io.github.enadeag.carchargeplanner.desktop.system;

/** How the popup keeps out of the taskbar. JavaFX cannot ask for that directly, and each system needs another way. */
public enum TaskbarHiding {

    /**
     * The popup is owned by an invisible utility window: the Windows taskbar leaves out windows that another window
     * owns.
     */
    OWNER_WINDOW,

    /**
     * Nothing is done. Under X11 an owner would show as a small framed window (opacity does not reach it there), and
     * Plasma lists the popup in its taskbar whether it has an owner or not.
     */
    NONE
}

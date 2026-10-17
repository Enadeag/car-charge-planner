package io.github.enadeag.carchargeplanner;

/** How the dashboard starts, once it is known whether the tray icon is there. */
enum DashboardStart {
    /** As the tray icon's popup; closing the window hides it again. */
    POPUP,
    /** As the app's window, opened now; closing it quits the app. */
    WINDOW,
    /** As the app's window, hidden until the app is started again, as at login; closing it quits the app. */
    HIDDEN_WINDOW
}

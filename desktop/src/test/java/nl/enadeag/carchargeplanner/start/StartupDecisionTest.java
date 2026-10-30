package nl.enadeag.carchargeplanner.start;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.desktop.app.DisplayMode;
import java.time.Duration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StartupDecisionTest {

    @ParameterizedTest(name = "{0}, notifications need the tray: {1}")
    @CsvSource({
        "AUTOMATIC, false, true",
        "TRAY,      false, true",
        "WINDOW,    false, false",
        "WINDOW,    true,  true"})
    void theTrayIsTriedUnlessTheOwnerWantsAWindowAndTheNotificationsDoNotNeedIt(DisplayMode mode,
            boolean notificationsNeedTheTray, boolean tried) {
        assertThat(new StartupDecision(mode, false, notificationsNeedTheTray).triesTheTray()).isEqualTo(tried);
    }

    @ParameterizedTest(name = "{0}, at login: {1}")
    @CsvSource({
        "AUTOMATIC, false, 0",
        "AUTOMATIC, true,  5",
        "TRAY,      false, 5",
        "WINDOW,    false, 0"})
    void theAppWaitsForTheTrayAtLoginAndWhenTheOwnerAskedForIt(DisplayMode mode, boolean atLogin,
            long waitSeconds) {
        assertThat(new StartupDecision(mode, atLogin, false).trayWait())
                .isEqualTo(Duration.ofSeconds(waitSeconds));
    }

    @ParameterizedTest(name = "{0}, at login: {1}, tray shown: {2}")
    @CsvSource({
        "AUTOMATIC, false, true,  POPUP",
        "TRAY,      true,  true,  POPUP",
        "AUTOMATIC, false, false, WINDOW",
        "AUTOMATIC, true,  false, HIDDEN_WINDOW",
        "WINDOW,    false, true,  WINDOW",
        "WINDOW,    true,  true,  HIDDEN_WINDOW",
        "WINDOW,    false, false, WINDOW"})
    void theDashboardIsThePopupOnlyBehindATrayIconAndTheWindowStaysHiddenAtLogin(DisplayMode mode, boolean atLogin,
            boolean trayShown, DashboardStart start) {
        assertThat(new StartupDecision(mode, atLogin, true).dashboard(trayShown)).isEqualTo(start);
    }
}

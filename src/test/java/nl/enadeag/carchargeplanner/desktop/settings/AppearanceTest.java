package nl.enadeag.carchargeplanner.desktop.settings;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.desktop.Fx;
import nl.enadeag.carchargeplanner.desktop.FxTest;
import nl.enadeag.carchargeplanner.desktop.app.Appearance;
import nl.enadeag.carchargeplanner.desktop.dashboard.Dashboard;
import nl.enadeag.carchargeplanner.desktop.dashboard.DashboardActions;
import nl.enadeag.carchargeplanner.desktop.settings.page.Category;
import nl.enadeag.carchargeplanner.desktop.system.AppIcon;
import nl.enadeag.carchargeplanner.desktop.system.TaskbarHiding;
import nl.enadeag.carchargeplanner.desktop.text.Language;
import nl.enadeag.carchargeplanner.desktop.text.LanguageChoice;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import javafx.application.ColorScheme;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Point2D;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The appearance the owner chooses reaches every open window at once, as the app wires it. */
@FxTest
class AppearanceTest {

    @TempDir
    Path folder;

    private final SimpleObjectProperty<ColorScheme> system = new SimpleObjectProperty<>(ColorScheme.LIGHT);
    private final SimpleObjectProperty<Appearance> appearance = new SimpleObjectProperty<>(Appearance.SYSTEM);
    private final LanguageChoice language = new LanguageChoice(Language.ENGLISH);
    private SettingsFixture settings;
    private SettingsWindow settingsWindow;
    private Dashboard dashboard;

    @BeforeEach
    void make() throws Exception {
        settings = new SettingsFixture(folder, Language.ENGLISH);
        settingsWindow = settings.window(language, appearance, system);
        dashboard = Fx.call(() -> new Dashboard(new AppIcon(), Appearance.scheme(appearance, system), Fx.REDUCED_MOTION,
                () -> Instant.parse("2026-10-15T08:00:00Z"), TaskbarHiding.NONE, language,
                new DashboardActions(level -> { }, period -> { }, period -> { }, () -> { }, () -> { }, () -> { }, () -> { })));
        dashboard.openWindow();
        settingsWindow.open(Category.APP);
        Fx.settle();
    }

    @AfterEach
    void closeAll() throws Exception {
        Fx.run(() -> List.copyOf(Window.getWindows()).forEach(Window::hide));
    }

    /** Whether each open window is dark: the dashboard's window or popup, and Settings. */
    private static List<Boolean> darkWindows() throws Exception {
        return Fx.call(() -> Window.getWindows().stream().filter(Window::isShowing)
                .filter(window -> window.getScene() != null && window.getWidth() > 1)
                .map(window -> window.getScene().getRoot().getStyleClass().contains("dark"))
                .toList());
    }

    private void set(Runnable change) throws Exception {
        Fx.run(change);
        Fx.settle();
    }

    @Test
    void choosingDarkMakesTheDashboardAndSettingsDarkAndLightMakesThemLightAgain() throws Exception {
        assertThat(darkWindows()).containsExactly(false, false);

        set(() -> appearance.set(Appearance.DARK));
        assertThat(darkWindows()).containsExactly(true, true);

        set(() -> appearance.set(Appearance.LIGHT));
        assertThat(darkWindows()).containsExactly(false, false);
    }

    @Test
    void theOpenPopupTurnsDarkToo() throws Exception {
        closeAll();
        dashboard.popUp(new Point2D(960, 990));
        Fx.settle();
        assertThat(darkWindows()).containsExactly(false);

        set(() -> appearance.set(Appearance.DARK));

        assertThat(darkWindows()).containsExactly(true);
    }

    @Test
    void followingTheSystemKeepsFollowingItWhileTheAppRuns() throws Exception {
        set(() -> appearance.set(Appearance.LIGHT));
        set(() -> system.set(ColorScheme.DARK));
        assertThat(darkWindows()).containsExactly(false, false);

        set(() -> appearance.set(Appearance.SYSTEM));
        assertThat(darkWindows()).containsExactly(true, true);

        set(() -> system.set(ColorScheme.LIGHT));
        assertThat(darkWindows()).containsExactly(false, false);
    }

    @Test
    void savingDarkInSettingsMakesEveryOpenWindowDarkAtOnce() throws Exception {
        var view = settingsView();
        Fx.run(() -> settings.choose(view, TextKey.FIELD_APPEARANCE, Appearance.DARK));

        set(() -> SettingsFixture.save(view));

        assertThat(appearance.get()).isEqualTo(Appearance.DARK);
        assertThat(darkWindows()).containsExactly(true, true);
    }

    @Test
    void aDarkSettingsStaysDarkWhenAnotherLanguageMakesItAgain() throws Exception {
        set(() -> appearance.set(Appearance.DARK));

        set(() -> language.choose(Language.DUTCH));

        assertThat(darkWindows()).containsExactly(true, true);
    }

    private static SettingsView settingsView() throws Exception {
        return Fx.call(() -> Window.getWindows().stream()
                .filter(shown -> shown.isShowing() && shown.getScene().getRoot() instanceof SettingsView)
                .map(shown -> (SettingsView) shown.getScene().getRoot())
                .findFirst().orElseThrow());
    }
}

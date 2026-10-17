package io.github.enadeag.carchargeplanner.desktop.settings;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.enadeag.carchargeplanner.desktop.AppIcon;
import io.github.enadeag.carchargeplanner.desktop.Appearance;
import io.github.enadeag.carchargeplanner.desktop.Dashboard;
import io.github.enadeag.carchargeplanner.desktop.DashboardActions;
import io.github.enadeag.carchargeplanner.desktop.Fx;
import io.github.enadeag.carchargeplanner.desktop.FxTest;
import io.github.enadeag.carchargeplanner.desktop.Language;
import io.github.enadeag.carchargeplanner.desktop.LanguageChoice;
import io.github.enadeag.carchargeplanner.desktop.TaskbarHiding;
import io.github.enadeag.carchargeplanner.desktop.TextKey;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
        assertThat(darkWindows()).hasSize(2).containsOnly(false);

        set(() -> appearance.set(Appearance.DARK));
        assertThat(darkWindows()).hasSize(2).containsOnly(true);

        set(() -> appearance.set(Appearance.LIGHT));
        assertThat(darkWindows()).containsOnly(false);
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
        assertThat(darkWindows()).containsOnly(false);

        set(() -> appearance.set(Appearance.SYSTEM));
        assertThat(darkWindows()).containsOnly(true);

        set(() -> system.set(ColorScheme.LIGHT));
        assertThat(darkWindows()).containsOnly(false);
    }

    @Test
    void savingDarkInSettingsMakesEveryOpenWindowDarkAtOnce() throws Exception {
        var view = settingsView();
        Fx.run(() -> settings.choose(view, TextKey.FIELD_APPEARANCE, Appearance.DARK));

        set(view::save);

        assertThat(appearance.get()).isEqualTo(Appearance.DARK);
        assertThat(darkWindows()).hasSize(2).containsOnly(true);
    }

    @Test
    void aDarkSettingsStaysDarkWhenAnotherLanguageMakesItAgain() throws Exception {
        set(() -> appearance.set(Appearance.DARK));

        set(() -> language.choose(Optional.of(Language.DUTCH)));

        assertThat(settingsView()).isNotNull();
        assertThat(darkWindows()).hasSize(2).containsOnly(true);
    }

    private static SettingsView settingsView() throws Exception {
        return Fx.call(() -> Window.getWindows().stream()
                .filter(shown -> shown.isShowing() && shown.getScene().getRoot() instanceof SettingsView)
                .map(shown -> (SettingsView) shown.getScene().getRoot())
                .findFirst().orElseThrow());
    }
}

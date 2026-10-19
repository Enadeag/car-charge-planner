package nl.enadeag.carchargeplanner.desktop.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.desktop.Fx;
import nl.enadeag.carchargeplanner.desktop.FxTest;
import nl.enadeag.carchargeplanner.desktop.text.Language;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.theme.WindowTheme;
import nl.enadeag.carchargeplanner.host.AppName;
import java.util.Optional;
import javafx.application.ColorScheme;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;

@FxTest
class WindowThemeTest {

    @Test
    void switchingTheSystemToDarkWhileTheAppRunsRestylesTheDashboard() throws Exception {
        var scheme = new SimpleObjectProperty<>(ColorScheme.LIGHT);
        var dashboard = Fx.call(() -> {
            var view = new DashboardView(() -> { }, () -> { }, Fx.REDUCED_MOTION,
                    AppName.TITLE, new Messages(Language.ENGLISH));
            view.show(Models.plan(Optional.empty()));
            new WindowTheme(scheme).style(new Scene(new StackPane(view)));
            return view;
        });

        var light = Fx.call(() -> background(dashboard));
        Fx.run(() -> scheme.set(ColorScheme.DARK));
        var dark = Fx.call(() -> background(dashboard));

        assertThat(dark.getBrightness()).isLessThan(0.2);
        assertThat(light.getBrightness()).isGreaterThan(0.8);
    }

    private static Color background(DashboardView view) {
        view.applyCss();
        return (Color) view.getBackground().getFills().getFirst().getFill();
    }
}

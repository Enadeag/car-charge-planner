package io.github.enadeag.carchargeplanner.desktop;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import javafx.application.ColorScheme;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;

class SystemThemeTest {

    @Test
    void switchingTheSystemToDarkWhileTheAppRunsRestylesTheDashboard() throws Exception {
        var scheme = new SimpleObjectProperty<>(ColorScheme.LIGHT);
        var dashboard = Fx.call(() -> {
            var view = new DashboardView(() -> { }, () -> { }, new Messages(Language.ENGLISH));
            view.show(Models.plan(Optional.empty()));
            new SystemTheme(scheme).style(new Scene(new StackPane(view)));
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

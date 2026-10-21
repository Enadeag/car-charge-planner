package nl.enadeag.carchargeplanner.desktop.theme;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.desktop.Fx;
import nl.enadeag.carchargeplanner.desktop.FxTest;
import nl.enadeag.carchargeplanner.desktop.system.TextSize;
import java.util.List;
import javafx.application.ColorScheme;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The windows' text follows the system's text size, over AtlantaFX's 14 px. */
@FxTest
class WindowThemeTextSizeTest {

    private final SimpleObjectProperty<TextSize> textSize = new SimpleObjectProperty<>(TextSize.NORMAL);

    @AfterEach
    void closeAll() throws Exception {
        Fx.run(() -> List.copyOf(Window.getWindows()).forEach(Window::hide));
    }

    private Label styled(Label label) {
        var scene = new Scene(new StackPane(label));
        new WindowTheme(new SimpleObjectProperty<>(ColorScheme.LIGHT), textSize).style(scene);
        return label;
    }

    private static double fontSize(Label label) {
        label.applyCss();
        return label.getFont().getSize();
    }

    @Test
    void theTextIsAsLargeAsTheSystemsTextSize() throws Exception {
        textSize.set(new TextSize(150));
        var label = Fx.call(() -> styled(new Label("Charge window")));

        assertThat(Fx.call(() -> fontSize(label))).isEqualTo(21);
    }

    @Test
    void atNormalSizeTheTextKeepsAtlantaFxsSize() throws Exception {
        var label = Fx.call(() -> styled(new Label("Charge window")));

        assertThat(Fx.call(() -> fontSize(label))).isEqualTo(14);
    }

    @Test
    void aChangeWhileTheAppRunsResizesTheTextAtOnce() throws Exception {
        var label = Fx.call(() -> styled(new Label("Charge window")));

        Fx.run(() -> textSize.set(new TextSize(200)));
        var larger = Fx.call(() -> fontSize(label));
        Fx.run(() -> textSize.set(new TextSize(125)));
        var smaller = Fx.call(() -> fontSize(label));
        Fx.run(() -> textSize.set(TextSize.NORMAL));
        var normal = Fx.call(() -> fontSize(label));

        assertThat(List.of(larger, smaller, normal)).containsExactly(28.0, 17.5, 14.0);
    }

    @Test
    void aTooltipIsAsLargeAsTheText() throws Exception {
        textSize.set(new TextSize(150));
        var tooltip = Fx.call(() -> {
            var label = styled(new Label("Charge window"));
            var shown = new Tooltip("From 11:15 to 14:30");
            label.setTooltip(shown);
            var stage = new Stage();
            stage.setScene(label.getScene());
            stage.show();
            shown.show(stage);
            return shown;
        });

        assertThat(Fx.call(() -> {
            tooltip.getScene().getRoot().applyCss();
            return tooltip.getFont().getSize();
        })).isEqualTo(21);
    }
}

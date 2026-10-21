package nl.enadeag.carchargeplanner.desktop.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import nl.enadeag.carchargeplanner.desktop.Fx;
import nl.enadeag.carchargeplanner.desktop.FxTest;
import nl.enadeag.carchargeplanner.desktop.settings.page.Category;
import nl.enadeag.carchargeplanner.desktop.system.TextSize;
import nl.enadeag.carchargeplanner.desktop.text.Language;
import java.nio.file.Path;
import java.util.List;
import javafx.beans.property.SimpleObjectProperty;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Settings opens as much larger as the system's text, so as many lines of it fit as at normal size. */
@FxTest
class SettingsTextSizeTest {

    @TempDir
    Path folder;

    private final SimpleObjectProperty<TextSize> textSize = new SimpleObjectProperty<>(TextSize.NORMAL);
    private SettingsWindow window;

    @BeforeEach
    void make() throws Exception {
        window = new SettingsFixture(folder, Language.ENGLISH).window(textSize);
    }

    @AfterEach
    void closeAll() throws Exception {
        Fx.run(() -> List.copyOf(Window.getWindows()).forEach(Window::hide));
    }

    /** The height Settings opens with, and closes again. */
    private double openedHeight() throws Exception {
        window.open(Category.APP);
        Fx.settle();
        return Fx.call(() -> {
            var stage = (Stage) Window.getWindows().stream()
                    .filter(shown -> shown.isShowing() && shown.getScene().getRoot() instanceof SettingsView)
                    .findFirst().orElseThrow();
            var height = stage.getHeight();
            ((SettingsView) stage.getScene().getRoot()).cancel();
            return height;
        });
    }

    @Test
    void withLargerTextSettingsOpensAsMuchHigher() throws Exception {
        var normal = openedHeight();
        Fx.run(() -> textSize.set(new TextSize(150)));
        var larger = openedHeight();

        assertThat(larger).isCloseTo(normal * 1.5, within(1.0));
    }
}

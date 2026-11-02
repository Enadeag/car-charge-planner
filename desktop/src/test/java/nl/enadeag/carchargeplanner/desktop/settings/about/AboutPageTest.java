package nl.enadeag.carchargeplanner.desktop.settings.about;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nl.enadeag.carchargeplanner.desktop.Fx;
import nl.enadeag.carchargeplanner.desktop.FxTest;
import nl.enadeag.carchargeplanner.desktop.text.Language;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.engine.secrets.KeyStorage;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

@FxTest
class AboutPageTest {

    @Test
    void keysThatCannotBeKeptAnywhereAreNeverTheDesktopsAsItKeepsAFile() {
        var place = Path.of("place");
        var facts = new AboutFacts("1.0", place, place, place, place, place);

        assertThatThrownBy(() -> Fx.run(() -> new AboutPage(facts, link -> { }, new Messages(Language.ENGLISH))
                .show(KeyStorage.UNAVAILABLE)))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }
}

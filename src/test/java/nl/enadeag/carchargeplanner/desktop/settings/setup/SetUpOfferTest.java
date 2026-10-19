package nl.enadeag.carchargeplanner.desktop.settings.setup;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.desktop.settings.page.Category;
import nl.enadeag.carchargeplanner.engine.Outlook;
import nl.enadeag.carchargeplanner.engine.config.ConfigProblem;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Without a config the app leads into set-up: once by itself, and from the ways into Settings until there is one. */
class SetUpOfferTest {

    private static final Outlook MISSING = new Outlook.NotSetUp(Path.of("engine.json"));

    private final List<String> opened = new ArrayList<>();

    private SetUpOffer offer(boolean openFirst) {
        return new SetUpOffer(() -> opened.add("set-up"), category -> opened.add(category.name()), openFirst);
    }

    @Test
    void theFirstPlanWithoutAConfigOpensSetUpOnceAndCancellingLeavesItClosed() {
        var offer = offer(true);

        offer.follow(MISSING);
        offer.follow(MISSING);

        assertThat(opened).containsExactly("set-up");
    }

    @Test
    void startedHiddenItOnlyOffersSetUp() {
        var offer = offer(false);

        offer.follow(MISSING);
        offer.openSettings(Category.SOLAR);

        assertThat(opened).containsExactly("set-up");
    }

    @Test
    void onceSetUpSettingsOpensAgain() {
        var offer = offer(true);
        offer.follow(MISSING);

        offer.follow(new Outlook.NoForecast("Open-Meteo: no connection."));
        offer.openSettings(Category.AWAY);

        assertThat(opened).containsExactly("set-up", "AWAY");
    }

    @ParameterizedTest
    @EnumSource(value = ConfigProblem.Kind.class, names = {"UNREADABLE", "INVALID"})
    void aConfigThatIsThereButWrongIsLeftToSettings(ConfigProblem.Kind kind) {
        var offer = offer(true);

        offer.follow(new Outlook.ConfigUnusable(kind, Path.of("engine.json"), "detail"));
        offer.openSettings(Category.SOLAR);

        assertThat(opened).containsExactly("SOLAR");
    }
}

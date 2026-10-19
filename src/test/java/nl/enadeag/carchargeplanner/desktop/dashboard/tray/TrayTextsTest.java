package nl.enadeag.carchargeplanner.desktop.dashboard.tray;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.desktop.system.StatusDot;
import nl.enadeag.carchargeplanner.desktop.system.Tray;
import nl.enadeag.carchargeplanner.desktop.system.TrayStatus;
import nl.enadeag.carchargeplanner.desktop.text.Language;
import nl.enadeag.carchargeplanner.desktop.text.LanguageChoice;
import nl.enadeag.carchargeplanner.engine.Outlook;
import nl.enadeag.carchargeplanner.engine.config.ConfigProblem;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The tray's menu and tooltip follow the language the owner chooses. */
class TrayTextsTest {

    /** A tray that only notes what it is told. */
    private static final class NotedTray implements Tray {
        final List<MenuLabels> labels = new ArrayList<>();
        final List<TrayStatus> statuses = new ArrayList<>();

        @Override
        public void show(Actions actions) {
        }

        @Override
        public void showStatus(TrayStatus status) {
            statuses.add(status);
        }

        @Override
        public void showLabels(MenuLabels labels) {
            this.labels.add(labels);
        }

        @Override
        public void remove() {
        }
    }

    private final NotedTray tray = new NotedTray();
    private final LanguageChoice language = new LanguageChoice(Language.ENGLISH);
    private final TrayTexts texts = new TrayTexts(tray, language);

    @Test
    void theTrayStartsWithItsMenuAndTheForecastBeingFetched() {
        assertThat(tray.labels).containsExactly(new Tray.MenuLabels("Open dashboard", "Settings…", "About…", "Quit"));
        assertThat(tray.statuses).containsExactly(new TrayStatus(StatusDot.NONE, "Fetching the forecast…"));
    }

    @Test
    void anotherLanguageRelabelsTheMenuAndShowsTheLatestOutlookAgainInIt() {
        texts.show(new Outlook.NoForecast("Open-Meteo: no connection."));

        language.choose(Language.DUTCH);

        assertThat(tray.labels.getLast())
                .isEqualTo(new Tray.MenuLabels("Dashboard openen", "Instellingen…", "Over…", "Afsluiten"));
        assertThat(tray.statuses.getLast())
                .isEqualTo(new TrayStatus(StatusDot.ACTION, "Geen voorspelling: Open-Meteo: no connection."));
    }

    @Test
    void beforeTheFirstOutlookAnotherLanguageSaysTheForecastIsBeingFetchedInIt() {
        language.choose(Language.DUTCH);

        assertThat(tray.statuses.getLast()).isEqualTo(new TrayStatus(StatusDot.NONE, "De voorspelling wordt opgehaald…"));
    }

    @Test
    void choosingTheLanguageSpokenAlreadyChangesNothing() {
        language.choose(Language.ENGLISH);

        assertThat(tray.labels).hasSize(1);
        assertThat(tray.statuses).hasSize(1);
    }

    @Test
    void aLaterOutlookIsShownInTheLanguageChosen() {
        language.choose(Language.DUTCH);

        texts.show(new Outlook.NoForecast("Open-Meteo: no connection."));

        assertThat(tray.statuses.getLast().text()).isEqualTo("Geen voorspelling: Open-Meteo: no connection.");
    }

    @Test
    void beforeTheFirstSetUpTheSettingsEntryLeadsIntoSetUpInEveryLanguageUntilThereIsAConfig() {
        texts.show(new Outlook.NotSetUp(Path.of("engine.json")));
        language.choose(Language.DUTCH);
        var dutch = tray.labels.getLast();
        texts.show(new Outlook.NoForecast("Open-Meteo: no connection."));

        assertThat(tray.labels.get(1).settings()).isEqualTo("Set up…");
        assertThat(dutch.settings()).isEqualTo("Instellen…");
        assertThat(tray.labels.getLast().settings()).isEqualTo("Instellingen…");
        assertThat(tray.statuses.get(1).dot()).isEqualTo(StatusDot.ACTION);
    }

    @Test
    void aConfigThatCannotBeReadKeepsTheSettingsEntry() {
        texts.show(new Outlook.ConfigUnusable(ConfigProblem.Kind.INVALID, Path.of("engine.json"), "system: is required"));

        assertThat(tray.labels).extracting(Tray.MenuLabels::settings).containsOnly("Settings…");
    }
}

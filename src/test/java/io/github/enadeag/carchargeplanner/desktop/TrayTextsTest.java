package io.github.enadeag.carchargeplanner.desktop;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.enadeag.carchargeplanner.engine.Outlook;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The tray's menu and tooltip follow the language the owner chooses. */
class TrayTextsTest {

    /** A tray that only notes what it is told. */
    private static final class NotedTray implements Tray {
        final List<MenuLabels> labels = new ArrayList<>();
        final List<TrayStatus> statuses = new ArrayList<>();

        @Override
        public void show(TrayActions actions) {
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

        language.choose(Optional.of(Language.DUTCH));

        assertThat(tray.labels.getLast())
                .isEqualTo(new Tray.MenuLabels("Dashboard openen", "Instellingen…", "Over…", "Afsluiten"));
        assertThat(tray.statuses.getLast())
                .isEqualTo(new TrayStatus(StatusDot.ACTION, "Geen voorspelling: Open-Meteo: no connection."));
    }

    @Test
    void beforeTheFirstOutlookAnotherLanguageSaysTheForecastIsBeingFetchedInIt() {
        language.choose(Optional.of(Language.DUTCH));

        assertThat(tray.statuses.getLast()).isEqualTo(new TrayStatus(StatusDot.NONE, "De voorspelling wordt opgehaald…"));
    }

    @Test
    void choosingTheLanguageSpokenAlreadyChangesNothing() {
        language.choose(Optional.of(Language.ENGLISH));

        assertThat(tray.labels).hasSize(1);
        assertThat(tray.statuses).hasSize(1);
    }

    @Test
    void aLaterOutlookIsShownInTheLanguageChosen() {
        language.choose(Optional.of(Language.DUTCH));

        texts.show(new Outlook.NoForecast("Open-Meteo: no connection."));

        assertThat(tray.statuses.getLast().text()).isEqualTo("Geen voorspelling: Open-Meteo: no connection.");
    }
}

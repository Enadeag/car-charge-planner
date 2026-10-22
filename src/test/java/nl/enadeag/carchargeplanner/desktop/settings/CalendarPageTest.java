package nl.enadeag.carchargeplanner.desktop.settings;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.desktop.Fx;
import nl.enadeag.carchargeplanner.desktop.FxTest;
import nl.enadeag.carchargeplanner.desktop.Scenes;
import nl.enadeag.carchargeplanner.desktop.TextScale;
import nl.enadeag.carchargeplanner.desktop.settings.page.Category;
import nl.enadeag.carchargeplanner.desktop.text.Language;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.calendar.CalendarSecrets;
import nl.enadeag.carchargeplanner.engine.calendar.Consent;
import nl.enadeag.carchargeplanner.engine.calendar.ConsentRecord;
import nl.enadeag.carchargeplanner.engine.config.CalendarSettings;
import nl.enadeag.carchargeplanner.engine.config.ConfigFile;
import nl.enadeag.carchargeplanner.engine.secrets.Secrets;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The Calendar page: its steps open in order, the consents are stored with their date and text version, and
 * withdrawing one asks first and on Save wipes what the calendar brought in.
 */
@FxTest
class CalendarPageTest {

    private static final Instant NOW = Instant.parse("2026-10-22T08:00:00Z");
    private static final String LINK = "https://calendar.example/private-abc/basic.ics";
    private static final String KEY = "ors-key-123";

    @TempDir
    Path folder;

    private SettingsFixture settings;

    @AfterEach
    void close() throws Exception {
        settings.close();
    }

    private SettingsView open() throws Exception {
        settings = new SettingsFixture(folder, Language.ENGLISH, Clock.fixed(NOW, ZoneOffset.UTC));
        return settings.open();
    }

    /** Opens Settings on stores that hold both consents, given before, and the calendar's secret settings. */
    private SettingsView openSetUp(String link) throws Exception {
        settings = new SettingsFixture(folder, Language.ENGLISH, Clock.fixed(NOW, ZoneOffset.UTC));
        settings.engine.saveConsents(Set.of(Consent.READ_CALENDAR, Consent.SEND_LOCATIONS));
        var keys = link.isEmpty() ? Map.of(CalendarSecrets.ROUTE_SERVICE, Map.of(CalendarSecrets.API_KEY, KEY))
                : Map.of(CalendarSecrets.CALENDAR, Map.of(CalendarSecrets.ICAL_LINK, link),
                        CalendarSecrets.ROUTE_SERVICE, Map.of(CalendarSecrets.API_KEY, KEY));
        settings.engine.saveApiKeys(new Secrets(keys));
        return settings.open();
    }

    private CheckBox box(SettingsView view, TextKey consent) {
        return (CheckBox) settings.field(view, consent);
    }

    private TextField text(SettingsView view, TextKey field) {
        return (TextField) settings.field(view, field);
    }

    private Path icsFile() {
        return folder.resolve("calendar.ics").toAbsolutePath();
    }

    @Test
    void theLaterStepsOpenOnceTheOneBeforeIsDone() throws Exception {
        var view = open();

        Fx.run(() -> {
            view.show(Category.CALENDAR);
            assertThat(text(view, TextKey.FIELD_ICAL_LINK).isDisabled()).isTrue();
            box(view, TextKey.CONSENT_READ_CALENDAR).setSelected(true);
            assertThat(text(view, TextKey.FIELD_ICAL_LINK).isDisabled()).isTrue();
            box(view, TextKey.CONSENT_SEND_LOCATIONS).setSelected(true);
            assertThat(text(view, TextKey.FIELD_ICAL_LINK).isDisabled()).isFalse();
            assertThat(text(view, TextKey.FIELD_ROUTE_KEY).isDisabled()).isTrue();
            text(view, TextKey.FIELD_ICS_FILE).setText(icsFile().toString());
            assertThat(text(view, TextKey.FIELD_ROUTE_KEY).isDisabled()).isFalse();
            assertThat(settings.control(view, TextKey.FIELD_TRAVEL_MARGIN).isDisabled()).isTrue();
            text(view, TextKey.FIELD_ROUTE_KEY).setText(KEY);
            assertThat(settings.control(view, TextKey.FIELD_TRAVEL_MARGIN).isDisabled()).isFalse();
        });
    }

    @Test
    void bothConsentsAreSavedWithTheirDateAndTextVersionWithTheCalendarAndTheKey() throws Exception {
        var view = open();

        var saved = Fx.call(() -> {
            box(view, TextKey.CONSENT_READ_CALENDAR).setSelected(true);
            box(view, TextKey.CONSENT_SEND_LOCATIONS).setSelected(true);
            text(view, TextKey.FIELD_ICAL_LINK).setText(LINK);
            text(view, TextKey.FIELD_ROUTE_KEY).setText(KEY);
            return SettingsFixture.save(view);
        });

        assertThat(saved).isTrue();
        var consents = settings.engine.consents();
        assertThat(consents.record(Consent.READ_CALENDAR)).contains(ConsentRecord.givenNow(Consent.READ_CALENDAR, NOW));
        assertThat(consents.allCount()).isTrue();
        var keys = settings.engine.storedApiKeys();
        assertThat(keys.of(CalendarSecrets.CALENDAR)).containsEntry(CalendarSecrets.ICAL_LINK, LINK);
        assertThat(keys.of(CalendarSecrets.ROUTE_SERVICE)).containsEntry(CalendarSecrets.API_KEY, KEY);
        assertThat(Files.readString(settings.configPath)).doesNotContain(LINK);
    }

    @Test
    void anAddressThatIsNoCalendarsIsRefusedAndNothingIsSaved() throws Exception {
        var view = open();

        var saved = Fx.call(() -> {
            box(view, TextKey.CONSENT_READ_CALENDAR).setSelected(true);
            box(view, TextKey.CONSENT_SEND_LOCATIONS).setSelected(true);
            text(view, TextKey.FIELD_ICAL_LINK).setText("http://calendar.example/basic.ics");
            return SettingsFixture.save(view);
        });

        assertThat(saved).isFalse();
        assertThat(Fx.call(view::shown)).isEqualTo(Category.CALENDAR);
        assertThat(settings.engine.consents().counting()).isEmpty();
    }

    @Test
    void untickingAConsentAsksFirstAndKeepingItChangesNothing() throws Exception {
        var view = openSetUp(LINK);

        Fx.run(() -> {
            view.show(Category.CALENDAR);
            box(view, TextKey.CONSENT_SEND_LOCATIONS).setSelected(false);
            assertThat(box(view, TextKey.CONSENT_SEND_LOCATIONS).isSelected()).isTrue();
            settings.button(view, TextKey.CONSENT_KEEP).fire();
        });

        assertThat(Fx.call(() -> box(view, TextKey.CONSENT_SEND_LOCATIONS).isSelected())).isTrue();
        assertThat(Fx.call(view::isChanged)).isFalse();
        assertThat(Fx.call(() -> Scenes.all(view).stream().filter(Scenes::reallyVisible)
                .noneMatch(node -> node instanceof Button button
                        && button.getText().equals(settings.messages.text(TextKey.CONSENT_WITHDRAW))))).isTrue();
    }

    @Test
    void withdrawingAConsentWipesTheAddressOnSaveAndKeepsTheKeyAndTheSettings() throws Exception {
        var view = openSetUp(LINK);
        var config = new ConfigFile(settings.configPath);
        var margin = new CalendarSettings(Optional.empty(), Duration.ofMinutes(30), LocalTime.of(9, 0),
                LocalTime.of(18, 0));
        config.write(config.read().withCalendar(margin));
        Fx.run(view::reload);

        var saved = Fx.call(() -> {
            view.show(Category.CALENDAR);
            box(view, TextKey.CONSENT_READ_CALENDAR).setSelected(false);
            settings.button(view, TextKey.CONSENT_WITHDRAW).fire();
            assertThat(box(view, TextKey.CONSENT_READ_CALENDAR).isSelected()).isFalse();
            assertThat(text(view, TextKey.FIELD_ICAL_LINK).getText()).isEmpty();
            return SettingsFixture.save(view);
        });

        assertThat(saved).isTrue();
        assertThat(settings.engine.consents().counting()).containsExactly(Consent.SEND_LOCATIONS);
        var keys = settings.engine.storedApiKeys();
        assertThat(keys.of(CalendarSecrets.CALENDAR)).isEmpty();
        assertThat(keys.of(CalendarSecrets.ROUTE_SERVICE)).containsEntry(CalendarSecrets.API_KEY, KEY);
        assertThat(config.read().calendar()).isEqualTo(margin);
    }

    @Test
    void withdrawingBothConsentsWipesTheFileAndTheConsents() throws Exception {
        var view = openSetUp("");
        var config = new ConfigFile(settings.configPath);
        config.write(config.read().withCalendar(new CalendarSettings(Optional.of(icsFile()), Duration.ofMinutes(15),
                LocalTime.of(9, 0), LocalTime.of(18, 0))));
        Fx.run(view::reload);

        var saved = Fx.call(() -> {
            view.show(Category.CALENDAR);
            box(view, TextKey.CONSENT_READ_CALENDAR).setSelected(false);
            settings.button(view, TextKey.CONSENT_WITHDRAW).fire();
            box(view, TextKey.CONSENT_SEND_LOCATIONS).setSelected(false);
            settings.button(view, TextKey.CONSENT_WITHDRAW).fire();
            return SettingsFixture.save(view);
        });

        assertThat(saved).isTrue();
        assertThat(config.read().calendar()).isEqualTo(CalendarSettings.STANDARD);
        assertThat(settings.engine.consents().given()).isEmpty();
        assertThat(folder.resolve("calendar-consents.json")).doesNotExist();
    }

    @Test
    void aConsentToAnotherVersionOfItsTextShowsUntickedAndTheCalendarStaysLocked() throws Exception {
        settings = new SettingsFixture(folder, Language.ENGLISH, Clock.fixed(NOW, ZoneOffset.UTC));
        Files.writeString(folder.resolve("calendar-consents.json"), """
                {"version": 1, "consents": [
                  {"consent": "READ_CALENDAR", "givenAt": "2026-10-01T08:00:00Z", "textVersion": 1},
                  {"consent": "SEND_LOCATIONS", "givenAt": "2026-10-01T08:00:00Z", "textVersion": 99}]}
                """);
        var view = settings.open();

        Fx.run(() -> {
            assertThat(box(view, TextKey.CONSENT_READ_CALENDAR).isSelected()).isTrue();
            assertThat(box(view, TextKey.CONSENT_SEND_LOCATIONS).isSelected()).isFalse();
            assertThat(text(view, TextKey.FIELD_ICAL_LINK).isDisabled()).isTrue();
            assertThat(view.isChanged()).isFalse();
        });
    }

    @Test
    void theLinkOpensTheRouteServicesPrivacyPolicy() throws Exception {
        var view = open();

        Fx.run(() -> Scenes.all(view.pages().stream().filter(page -> page.category() == Category.CALENDAR)
                        .findFirst().orElseThrow().view()).stream()
                .filter(node -> node instanceof Hyperlink link
                        && link.getText().equals(settings.messages.text(TextKey.CONSENT_PRIVACY_POLICY)))
                .map(Hyperlink.class::cast).findFirst().orElseThrow().fire());

        assertThat(settings.opened).containsExactly(URI.create("https://heigit.org/privacy-policy/"));
    }

    static Stream<Arguments> languagesAndScales() {
        return Stream.of(Language.values()).flatMap(language -> Stream.of(TextScale.values())
                .map(scale -> Arguments.of(language, scale)));
    }

    /** What the default view leaves out: the dates of the consents, the question before a withdrawal, open steps. */
    @ParameterizedTest
    @MethodSource("languagesAndScales")
    void theQuestionAndTheDatesShowWholeInEveryLanguageWithLargerText(Language language, TextScale scale)
            throws Exception {
        settings = new SettingsFixture(folder, language, Clock.fixed(NOW, ZoneOffset.UTC));
        Files.writeString(folder.resolve("calendar-consents.json"), """
                {"version": 1, "consents": [
                  {"consent": "READ_CALENDAR", "givenAt": "2026-09-30T08:00:00Z", "textVersion": 1},
                  {"consent": "SEND_LOCATIONS", "givenAt": "2026-09-30T08:00:00Z", "textVersion": 99}]}
                """);
        settings.engine.saveApiKeys(new Secrets(Map.of(CalendarSecrets.CALENDAR,
                Map.of(CalendarSecrets.ICAL_LINK, LINK))));
        var view = settings.open(scale);

        var problems = Fx.call(() -> {
            view.show(Category.CALENDAR);
            box(view, TextKey.CONSENT_SEND_LOCATIONS).setSelected(true);
            box(view, TextKey.CONSENT_READ_CALENDAR).setSelected(false);
            view.applyCss();
            view.layout();
            var found = new ArrayList<>(Scenes.cutOff(view));
            found.addAll(Scenes.overlapping(view));
            return found;
        });

        assertThat(problems).isEmpty();
    }
}

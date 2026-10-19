package nl.enadeag.carchargeplanner.desktop.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.javafx.stage.WindowHelper;
import nl.enadeag.carchargeplanner.desktop.CssWarnings;
import nl.enadeag.carchargeplanner.desktop.Fx;
import nl.enadeag.carchargeplanner.desktop.FxTest;
import nl.enadeag.carchargeplanner.desktop.Keyboard;
import nl.enadeag.carchargeplanner.desktop.Scenes;
import nl.enadeag.carchargeplanner.desktop.system.AppIcon;
import nl.enadeag.carchargeplanner.desktop.system.TaskbarHiding;
import nl.enadeag.carchargeplanner.desktop.text.Language;
import nl.enadeag.carchargeplanner.desktop.text.LanguageChoice;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.Outlook;
import nl.enadeag.carchargeplanner.engine.away.OneOffAwayPeriod;
import nl.enadeag.carchargeplanner.engine.config.ConfigProblem;
import nl.enadeag.carchargeplanner.engine.config.Percent;
import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastSource;
import nl.enadeag.carchargeplanner.engine.forecast.Freshness;
import nl.enadeag.carchargeplanner.engine.forecast.ProviderRole;
import nl.enadeag.carchargeplanner.engine.plan.DayPlan;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.ColorScheme;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TabPane;
import javafx.scene.input.KeyCode;
import javafx.scene.robot.Robot;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The dashboard as the tray and the composition root use it, on JavaFX's headless platform (a 1000 px screen). */
@FxTest
class DashboardTest {

    private static final Point2D ICON_AT_BOTTOM_RIGHT = new Point2D(960, 990);

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-15T08:00:00Z"));
    private final InstantSource clock = now::get;
    private final LanguageChoice language = new LanguageChoice(Language.ENGLISH);
    private final List<OneOffAwayPeriod> removed = new ArrayList<>();
    private final AtomicInteger settingsOpened = new AtomicInteger();
    private final AtomicInteger setUpOpened = new AtomicInteger();
    private final AtomicInteger quit = new AtomicInteger();
    private Dashboard dashboard;

    @BeforeEach
    void make() throws Exception {
        dashboard = dashboard(TaskbarHiding.OWNER_WINDOW);
    }

    private Dashboard dashboard(TaskbarHiding taskbarHiding) throws Exception {
        return Fx.call(() -> new Dashboard(new AppIcon(), new SimpleObjectProperty<>(ColorScheme.LIGHT),
                new SimpleObjectProperty<>(true), clock, taskbarHiding, language,
                new DashboardActions(level -> { }, period -> { }, removed::add, () -> { },
                        settingsOpened::incrementAndGet, setUpOpened::incrementAndGet, quit::incrementAndGet)));
    }

    private static List<Window> showingWindows() throws Exception {
        return Fx.call(() -> Window.getWindows().stream().filter(Window::isShowing).toList());
    }

    @AfterEach
    void closeAll() throws Exception {
        Fx.run(() -> List.copyOf(Window.getWindows()).forEach(Window::hide));
    }

    private static Optional<Stage> showing(StageStyle style) throws Exception {
        return Fx.call(() -> Window.getWindows().stream()
                .filter(window -> window instanceof Stage stage && stage.getStyle() == style && stage.isShowing())
                .map(Stage.class::cast)
                .findFirst());
    }

    private static Optional<Stage> popup() throws Exception {
        return showing(StageStyle.TRANSPARENT);
    }

    private static Optional<Stage> window() throws Exception {
        return showing(StageStyle.DECORATED);
    }

    private void clickTheIcon() throws Exception {
        dashboard.popUp(ICON_AT_BOTTOM_RIGHT);
        Fx.settle();
    }

    private void later(Duration duration) {
        now.updateAndGet(instant -> instant.plus(duration));
    }

    @Test
    void aClickOnTheIconOpensThePopupAboveIt() throws Exception {
        clickTheIcon();

        var popup = popup().orElseThrow();
        assertThat(popup.getY() + popup.getHeight()).isLessThanOrEqualTo(ICON_AT_BOTTOM_RIGHT.getY());
        assertThat(popup.getX()).isLessThan(ICON_AT_BOTTOM_RIGHT.getX());
        assertThat(popup.getX() + popup.getWidth()).isLessThanOrEqualTo(1000);
    }

    @Test
    void aPlanThatCameWhileThePopupWasClosedFitsWhenItOpensAgain() throws Exception {
        clickTheIcon();
        clickTheIcon();
        dashboard.show(planWithTwoDays(), List.of());
        later(Duration.ofSeconds(2));

        clickTheIcon();

        var popup = popup().orElseThrow();
        double needed = Fx.call(() -> {
            var root = popup.getScene().getRoot();
            root.applyCss();
            root.layout();
            return root.prefHeight(popup.getWidth());
        });
        assertThat(popup.getHeight()).isEqualTo(needed);
    }

    /** Detaches the popup with the plan into the window, and returns the window. */
    private Stage detachedWith(Outlook outlook) throws Exception {
        clickTheIcon();
        dashboard.show(outlook, List.of());
        Fx.settle();
        var detach = (Button) popup().orElseThrow().getScene().lookup(".detach");
        Fx.run(detach::fire);
        return window().orElseThrow();
    }

    private static void selectDay(Stage stage, int day) throws Exception {
        var tabs = (TabPane) stage.getScene().lookup(".day-tabs");
        Fx.run(() -> tabs.getSelectionModel().select(day));
        Fx.settle();
    }

    /** Where the node lies in its stage once a layout pass has run, as the next frame draws it. */
    private static Bounds laidOut(Stage stage, String selector) throws Exception {
        return Fx.call(() -> {
            var root = stage.getScene().getRoot();
            root.applyCss();
            root.layout();
            var node = stage.getScene().lookup(selector);
            return node.localToScene(node.getBoundsInLocal());
        });
    }

    @Test
    void aBusyDayInTheWindowKeepsTheHeaderAndTabsInViewAndTheWindowGrowsToShowIt() throws Exception {
        var window = detachedWith(planWithABusyTomorrow(4));
        double tabsHeight = laidOut(window, ".day-tabs").getHeight();

        selectDay(window, 1);

        var tabs = laidOut(window, ".day-tabs");
        assertThat(laidOut(window, ".dashboard-header").getMinY()).isGreaterThanOrEqualTo(0);
        assertThat(tabs.getHeight()).isEqualTo(tabsHeight);
        var scroll = laidOut(window, ".day-scroll");
        double dayHeight = Fx.call(() -> ((ScrollPane) window.getScene().lookup(".day-scroll")).getContent()
                .getLayoutBounds().getHeight());
        assertThat(scroll.getHeight()).as("the whole day shows").isGreaterThanOrEqualTo(dayHeight);
    }

    @Test
    void theWindowGrowsToShowTheAwayEntryWhenItOpens() throws Exception {
        var window = detachedWith(planWithTwoDays());
        double before = Fx.call(window::getHeight);

        var add = Fx.call(() -> Scenes.all(window.getScene().lookup(".away-card")).stream()
                .filter(node -> node instanceof Button button && "Add".equals(button.getText()))
                .map(Button.class::cast).findFirst().orElseThrow());
        Fx.run(add::fire);
        Fx.settle();

        assertThat(Fx.call(window::getHeight)).isGreaterThan(before);
        var scroll = laidOut(window, ".day-scroll");
        double dayHeight = Fx.call(() -> ((ScrollPane) window.getScene().lookup(".day-scroll")).getContent()
                .getLayoutBounds().getHeight());
        assertThat(scroll.getHeight()).as("the whole day shows, with the entry").isGreaterThanOrEqualTo(dayHeight);

        double withEntry = Fx.call(window::getHeight);
        Fx.run(() -> {
            AwayEntry.enterTimes(window.getScene().getRoot(), "16:00", "10:00");
            AwayEntry.save(window.getScene().getRoot());
        });
        Fx.settle();
        assertThat(Fx.call(window::getHeight)).as("and grows again for the times and the problem").isGreaterThan(withEntry);
    }

    @Test
    void aWindowForADayTallerThanTheScreenStaysOnTheScreen() throws Exception {
        var window = detachedWith(planWithABusyTomorrow(200));

        selectDay(window, 1);

        assertThat(window.getY()).isGreaterThanOrEqualTo(0);
        assertThat(window.getY() + window.getHeight()).isLessThanOrEqualTo(1000);
    }

    @Test
    void thePopupAlsoStaysOnTheScreenWithADayTallerThanIt() throws Exception {
        clickTheIcon();
        dashboard.show(planWithABusyTomorrow(200), List.of());
        Fx.settle();
        var popup = popup().orElseThrow();

        selectDay(popup, 1);

        assertThat(popup.getY()).isGreaterThanOrEqualTo(PopupPlacement.MARGIN);
        assertThat(popup.getY() + popup.getHeight()).isLessThanOrEqualTo(1000 - PopupPlacement.MARGIN);
        assertThat(laidOut(popup, ".day-tabs").getMinY()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void thePopupStaysOnTheScreenWhenTheDayChangesTwiceBeforeTheNextFrame() throws Exception {
        clickTheIcon();
        dashboard.show(planWithABusyTomorrow(20), List.of());
        Fx.settle();
        var popup = popup().orElseThrow();
        var tabs = Fx.call(() -> (TabPane) popup.getScene().lookup(".day-tabs"));

        // in one go on the JavaFX thread, so no frame comes between the two fits: the second one finds the popup where
        // the first left it, which must be on the screen already, or the headless window throws and breaks it off
        Fx.run(() -> {
            tabs.getSelectionModel().select(1);
            tabs.getSelectionModel().select(0);
        });

        double needed = Fx.call(() -> {
            var root = popup.getScene().getRoot();
            root.applyCss();
            root.layout();
            return root.prefHeight(popup.getWidth());
        });
        assertThat(popup.getHeight()).isEqualTo(needed);
        assertThat(popup.getY() + popup.getHeight()).isLessThanOrEqualTo(1000 - PopupPlacement.MARGIN);
    }

    @Test
    void everyStyleResolvesWhenThePopupReopensWithAPlanThatCameWhileItWasClosed() throws Exception {
        try (var css = new CssWarnings()) {
            clickTheIcon();
            clickTheIcon();
            dashboard.show(planWithTwoDays(), List.of());
            later(Duration.ofSeconds(2));

            clickTheIcon();

            assertThat(css.warnings()).isEmpty();
        }
    }

    @Test
    void escapeClosesThePopup() throws Exception {
        clickTheIcon();

        Fx.run(() -> {
            var robot = new Robot();
            robot.keyPress(KeyCode.ESCAPE);
            robot.keyRelease(KeyCode.ESCAPE);
        });

        assertThat(popup()).isEmpty();
    }

    @Test
    void escapeInTheBatteryEntryClosesOnlyTheEntryAndAgainThePopup() throws Exception {
        dashboard.show(planWithTwoDays(), List.of());
        clickTheIcon();
        var root = popup().orElseThrow().getScene().getRoot();
        Fx.run(() -> Scenes.all(root).stream()
                .filter(node -> node instanceof Button button
                        && language.messages().text(TextKey.BATTERY_UPDATE).equals(button.getText()))
                .map(Button.class::cast).findFirst().orElseThrow().fire());

        Keyboard.press(KeyCode.ESCAPE);

        assertThat(popup()).isPresent();
        assertThat(Fx.call(() -> Scenes.reallyVisible(root.lookup(".battery-editor")))).isFalse();
        Keyboard.press(KeyCode.ESCAPE);
        assertThat(popup()).isEmpty();
    }

    @Test
    void escapeHidesTheWindowWhileTheTrayCanBringItBack() throws Exception {
        dashboard.openWindow();
        Fx.settle();

        Keyboard.press(KeyCode.ESCAPE);

        assertThat(window()).isEmpty();
    }

    @Test
    void escapeLeavesTheWindowOpenWhenClosingItQuitsTheApp() throws Exception {
        dashboard.quitWhenClosed();
        dashboard.openWindow();
        Fx.settle();

        Keyboard.press(KeyCode.ESCAPE);

        assertThat(window()).isPresent();
        assertThat(quit).hasValue(0);
    }

    /**
     * What a click on another window, or on the panel, does to the popup. The headless platform has no window manager
     * that moves the focus, so the test tells the popup what the system would.
     */
    private static void focusElsewhere() throws Exception {
        var popup = popup().orElseThrow();
        Fx.run(() -> {
            if (popup.getOwner() != null) {
                WindowHelper.setFocused(popup.getOwner(), false);
            }
            WindowHelper.setFocused(popup, false);
        });
        Fx.settle();
    }

    @Test
    void withoutAnOwnerWindowOnlyThePopupShows() throws Exception {
        dashboard = dashboard(TaskbarHiding.NONE);

        clickTheIcon();

        assertThat(showingWindows()).containsExactly(popup().orElseThrow());
    }

    @Test
    void withoutAnOwnerWindowAClickElsewhereStillClosesThePopup() throws Exception {
        dashboard = dashboard(TaskbarHiding.NONE);
        clickTheIcon();

        focusElsewhere();

        assertThat(popup()).isEmpty();
    }

    @Test
    void theOwnerWindowIsSeeThroughAndOnePixelLarge() throws Exception {
        clickTheIcon();

        var owner = (Stage) popup().orElseThrow().getOwner();

        assertThat(owner.isShowing()).isTrue();
        assertThat(owner.getOpacity()).isZero();
        assertThat(owner.getWidth()).isLessThanOrEqualTo(1);
        assertThat(owner.getHeight()).isLessThanOrEqualTo(1);
    }

    @Test
    void theOwnerWindowGoesAwayWithThePopup() throws Exception {
        clickTheIcon();
        var owner = popup().orElseThrow().getOwner();

        clickTheIcon();

        assertThat(Fx.call(owner::isShowing)).isFalse();
    }

    @Test
    void theKeyboardReachesThePopup() throws Exception {
        clickTheIcon();
        dashboard.show(planWithTwoDays(), List.of());
        Fx.settle();
        var tabs = (TabPane) popup().orElseThrow().getScene().lookup(".day-tabs");

        Fx.run(() -> {
            tabs.requestFocus();
            var robot = new Robot();
            robot.keyPress(KeyCode.RIGHT);
            robot.keyRelease(KeyCode.RIGHT);
        });

        assertThat(Fx.call(() -> tabs.getSelectionModel().getSelectedIndex())).isEqualTo(1);
    }

    private static ForecastSource source(LocalDate today, ZoneId zone) {
        var fetched = today.atTime(7, 45).atZone(zone).toInstant();
        return new ForecastSource(new ProviderId("open-meteo"), "Open-Meteo", "Weather data: Open-Meteo.com (CC BY 4.0)", fetched,
                Freshness.FRESH, ProviderRole.PRIMARY, "", Optional.empty());
    }

    private static Outlook planWithTwoDays() {
        var zone = ZoneId.of("Europe/Brussels");
        var today = LocalDate.of(2026, 10, 15);
        var days = List.of(new DayPlan(today, new Percent(50), List.of(), new Percent(50), new Percent(40), 10, List.of()),
                new DayPlan(today.plusDays(1), new Percent(40), List.of(), new Percent(40), new Percent(30), 10, List.of()));
        return new Outlook.Planned(today.atTime(8, 0).atZone(zone).toInstant(), zone, days, new Percent(50), false, Optional.empty(), source(today, zone), 0.4, new Percent(30), new Percent(80));
    }

    /** Today without a window, tomorrow with many windows: a much taller main card than today's. */
    private static Outlook planWithABusyTomorrow(int windowCount) {
        var zone = ZoneId.of("Europe/Brussels");
        var today = LocalDate.of(2026, 10, 15);
        var tomorrow = today.plusDays(1);
        var windows = new ArrayList<nl.enadeag.carchargeplanner.engine.plan.Window>();
        var start = tomorrow.atTime(6, 0).atZone(zone).toInstant();
        for (int i = 0; i < windowCount; i++) {
            var from = start.plus(Duration.ofMinutes(15L * i));
            windows.add(new nl.enadeag.carchargeplanner.engine.plan.Window(from, from.plus(Duration.ofMinutes(10)), 6, 1.4, 0.2, 0.05));
        }
        var days = List.of(new DayPlan(today, new Percent(50), List.of(), new Percent(50), new Percent(40), 10, List.of()),
                new DayPlan(tomorrow, new Percent(40), windows, new Percent(60), new Percent(50), 10, List.of()));
        return new Outlook.Planned(today.atTime(8, 0).atZone(zone).toInstant(), zone, days, new Percent(50), true, Optional.empty(), source(today, zone), 0.4, new Percent(30), new Percent(80));
    }

    @Test
    void aReportedIconPositionOffEveryScreenFallsBackToTheCornerOfThePrimaryScreen() throws Exception {
        dashboard.popUp(new Point2D(-40_000, 90_000));
        Fx.settle();

        var popup = popup().orElseThrow();
        assertThat(popup.getX() + popup.getWidth()).isEqualTo(1000 - PopupPlacement.MARGIN);
        assertThat(popup.getY() + popup.getHeight()).isEqualTo(1000 - PopupPlacement.MARGIN);
    }

    @Test
    void aClickElsewhereClosesThePopup() throws Exception {
        clickTheIcon();

        focusElsewhere();

        assertThat(popup()).isEmpty();
    }

    @Test
    void aSecondClickOnTheIconClosesThePopup() throws Exception {
        clickTheIcon();
        later(Duration.ofSeconds(2));

        clickTheIcon();

        assertThat(popup()).isEmpty();
    }

    @Test
    void theClickThatClosedThePopupByTakingTheFocusDoesNotOpenItAgain() throws Exception {
        clickTheIcon();
        focusElsewhere();

        later(Duration.ofMillis(100));
        clickTheIcon();
        assertThat(popup()).isEmpty();

        later(Duration.ofSeconds(1));
        clickTheIcon();
        assertThat(popup()).isPresent();
    }

    @Test
    void thePopupDetachesIntoAWindowThatTheIconThenBringsBack() throws Exception {
        clickTheIcon();

        var detach = (Button) popup().orElseThrow().getScene().lookup(".detach");
        Fx.run(detach::fire);

        assertThat(popup()).isEmpty();
        var window = window().orElseThrow();
        assertThat(Fx.call(() -> window.getScene().lookup(".dashboard"))).isNotNull();
        assertThat(Fx.call(() -> window.getScene().lookup(".detach").isVisible())).isFalse();

        later(Duration.ofSeconds(2));
        clickTheIcon();
        assertThat(popup()).isEmpty();
        assertThat(window()).isPresent();
    }

    @Test
    void theMenuOpensTheWindow() throws Exception {
        dashboard.openWindow();
        Fx.settle();

        assertThat(window()).isPresent();
    }

    @Test
    void anotherLanguageRendersTheOpenPopupAgainOnTheSameDayWithItsChartsAndAccessibleTexts() throws Exception {
        clickTheIcon();
        dashboard.show(planWithTwoDays(), List.of());
        Fx.settle();
        selectDay(popup().orElseThrow(), 1);

        language.choose(Language.DUTCH);
        Fx.settle();

        var popup = popup().orElseThrow();
        var root = popup.getScene().getRoot();
        var tabs = Fx.call(() -> (TabPane) root.lookup(".day-tabs"));
        assertThat(Fx.call(() -> tabs.getSelectionModel().getSelectedIndex())).isEqualTo(1);
        assertThat(Fx.call(() -> tabs.getTabs().getFirst().getText())).isEqualTo("Vandaag");
        assertThat(Fx.call(tabs::getAccessibleText)).isEqualTo("Dagen");
        assertThat(Fx.call(() -> Scenes.visibleTexts(root))).contains("Morgen · Geen laadvenster", "Verwachte zon", "Accu van de auto",
                "Afwezig", "Instellingen");
        assertThat(Fx.call(() -> root.lookup(".detach").getAccessibleText())).isEqualTo("In een venster openen");
        assertThat(Fx.call(() -> root.lookup(".solar-chart").getAccessibleText())).startsWith("Morgen, verwachte zon");
        var settings = Fx.call(() -> Scenes.all(root).stream()
                .filter(node -> node instanceof Button button && "Instellingen".equals(button.getText()))
                .map(Button.class::cast).findFirst().orElseThrow());
        Fx.run(settings::fire);
        assertThat(settingsOpened).as("the footer's actions still work").hasValue(1);
    }

    @Test
    void anotherLanguageListsTheAwayPeriodsInItAndTheirRemoveButtonsStillWork() throws Exception {
        var tomorrow = OneOffAwayPeriod.allDay(LocalDate.of(2026, 10, 16), ZoneId.of("Europe/Brussels"));
        clickTheIcon();
        dashboard.show(planWithTwoDays(), List.of(tomorrow));
        Fx.settle();

        language.choose(Language.DUTCH);
        Fx.settle();

        var root = popup().orElseThrow().getScene().getRoot();
        assertThat(Fx.call(() -> Scenes.visibleTexts(root))).contains("Auto weg", "Morgen, hele dag");
        var remove = Fx.call(() -> Scenes.all(root).stream()
                .filter(node -> node instanceof Button button && "Morgen, hele dag verwijderen".equals(button.getAccessibleText()))
                .map(Button.class::cast).findFirst().orElseThrow());
        Fx.run(remove::fire);
        assertThat(removed).containsExactly(tomorrow);
    }

    @Test
    void anotherLanguageReachesTheWindowAndALaterOutlook() throws Exception {
        var window = detachedWith(planWithTwoDays());

        language.choose(Language.DUTCH);
        Fx.settle();
        dashboard.show(new Outlook.NoForecast("Open-Meteo: no connection."), List.of());
        Fx.settle();

        assertThat(window()).containsSame(window);
        assertThat(Fx.call(() -> window.getScene().lookup(".detach").isVisible())).isFalse();
        assertThat(Fx.call(() -> Scenes.visibleTexts(window.getScene().getRoot()))).contains("Geen voorspelling");
    }

    @Test
    void beforeTheFirstOutlookAnotherLanguageSaysTheForecastIsBeingFetchedInIt() throws Exception {
        clickTheIcon();

        language.choose(Language.DUTCH);
        Fx.settle();

        var popup = popup().orElseThrow();
        assertThat(Fx.call(() -> Scenes.visibleTexts(popup.getScene().getRoot())))
                .contains("De voorspelling wordt opgehaald…");
    }

    @Test
    void theDashboardShowsTheLatestOutlook() throws Exception {
        clickTheIcon();

        dashboard.show(new Outlook.ConfigUnusable(ConfigProblem.Kind.INVALID, Path.of("/home/owner/engine.json"),
                "system.car: is required"), List.of());
        Fx.settle();

        var popup = popup().orElseThrow();
        var texts = Fx.call(() -> Scenes.visibleTexts(popup.getScene().getRoot()));
        assertThat(texts).contains("Not set up", "Fix engine.json: system.car: is required");
    }

    @Test
    void beforeTheFirstSetUpTheMessageOpensSetUp() throws Exception {
        clickTheIcon();

        dashboard.show(new Outlook.NotSetUp(Path.of("/home/owner/engine.json")), List.of());
        Fx.settle();
        var root = popup().orElseThrow().getScene().getRoot();
        Fx.run(() -> Scenes.all(root).stream()
                .filter(node -> node instanceof Button button && Scenes.reallyVisible(button)
                        && language.messages().text(TextKey.START_SETUP).equals(button.getText()))
                .map(Button.class::cast).findFirst().orElseThrow().fire());

        assertThat(setUpOpened).hasValue(1);
    }

    @Test
    void aConfigThatCannotBeReadOffersNoSetUpThatWouldWriteOverIt() throws Exception {
        clickTheIcon();

        dashboard.show(new Outlook.ConfigUnusable(ConfigProblem.Kind.UNREADABLE, Path.of("/home/owner/engine.json"),
                "access denied"), List.of());
        Fx.settle();

        var root = popup().orElseThrow().getScene().getRoot();
        assertThat(Fx.call(() -> Scenes.visibleTexts(root))).doesNotContain(language.messages().text(TextKey.START_SETUP));
    }
}

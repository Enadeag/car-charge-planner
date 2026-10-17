package io.github.enadeag.carchargeplanner.desktop.settings;

import io.github.enadeag.carchargeplanner.desktop.AppIcon;
import io.github.enadeag.carchargeplanner.desktop.AppSettingsFile;
import io.github.enadeag.carchargeplanner.desktop.Appearance;
import io.github.enadeag.carchargeplanner.desktop.Background;
import io.github.enadeag.carchargeplanner.desktop.Fx;
import io.github.enadeag.carchargeplanner.desktop.Language;
import io.github.enadeag.carchargeplanner.desktop.LanguageChoice;
import io.github.enadeag.carchargeplanner.desktop.Messages;
import io.github.enadeag.carchargeplanner.desktop.Scenes;
import io.github.enadeag.carchargeplanner.desktop.TextKey;
import io.github.enadeag.carchargeplanner.desktop.TextScale;
import io.github.enadeag.carchargeplanner.desktop.Units;
import io.github.enadeag.carchargeplanner.engine.Engine;
import io.github.enadeag.carchargeplanner.engine.ManualScheduler;
import io.github.enadeag.carchargeplanner.engine.away.AwayFile;
import io.github.enadeag.carchargeplanner.engine.battery.BatteryFile;
import io.github.enadeag.carchargeplanner.engine.config.ConfigFile;
import io.github.enadeag.carchargeplanner.engine.config.Coordinates;
import io.github.enadeag.carchargeplanner.engine.config.EngineConfig;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastCache;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastProvider;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastService;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastStep;
import io.github.enadeag.carchargeplanner.engine.forecast.HttpGet;
import io.github.enadeag.carchargeplanner.engine.forecast.Place;
import io.github.enadeag.carchargeplanner.engine.forecast.PlaceResults;
import io.github.enadeag.carchargeplanner.engine.forecast.PlaceSearch;
import io.github.enadeag.carchargeplanner.engine.forecast.ProviderError;
import io.github.enadeag.carchargeplanner.engine.forecast.ProviderInfo;
import io.github.enadeag.carchargeplanner.engine.forecast.Providers;
import io.github.enadeag.carchargeplanner.engine.forecast.SunnyDays;
import io.github.enadeag.carchargeplanner.engine.forecast.SystemDescription;
import io.github.enadeag.carchargeplanner.engine.forecast.providers.ForecastSolar;
import io.github.enadeag.carchargeplanner.engine.forecast.providers.OpenMeteo;
import io.github.enadeag.carchargeplanner.engine.notify.AnnouncedWindows;
import io.github.enadeag.carchargeplanner.engine.notify.Announcer;
import io.github.enadeag.carchargeplanner.engine.secrets.ApiKeys;
import io.github.enadeag.carchargeplanner.engine.secrets.Keychain;
import io.github.enadeag.carchargeplanner.engine.secrets.NoKeychain;
import io.github.enadeag.carchargeplanner.engine.secrets.SecretsFile;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import javafx.application.ColorScheme;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

/**
 * Set-up over stores in a test's own folder, with no config file unless the test copies the example in, and the app's
 * own providers with their real texts, whose forecast and place search are played here. Nothing reaches the network,
 * the owner's files, keychain or autostart.
 */
final class SetUpFixture {

    /** A made-up place the search finds for any text. */
    static final Place PLACE = new Place("Zonnedorp", new Coordinates(52.1, 5.1), ZoneId.of("Europe/Amsterdam"),
            "Utrecht", "Netherlands", "");

    final Path folder;
    final Path configPath;
    final AppSettingsFile appSettings;
    final Engine engine;
    final Played provider = new Played(new OpenMeteo(offline()).info());
    final SettingsFixture.MemoryStartAtLogin login = new SettingsFixture.MemoryStartAtLogin();
    final List<URI> opened = new ArrayList<>();
    final Messages messages;
    /**
     * Unless a test chose another: runs the work at once, on the thread that asks for it, so the outcome is there as
     * the call returns.
     */
    final Background background;
    int closed;
    int finished;
    private Stage stage;

    SetUpFixture(Path folder, Language language) {
        this(folder, language, new NoKeychain(), new Background(Runnable::run, Runnable::run));
    }

    /** With this keychain, and this way to do the slow work, such as on virtual threads as the app does. */
    SetUpFixture(Path folder, Language language, Keychain keychain, Background background) {
        this.folder = folder;
        this.background = background;
        this.messages = new Messages(language);
        configPath = folder.resolve("engine.json");
        appSettings = new AppSettingsFile(folder.resolve("app.json"), Units.METRIC);
        var providers = new Providers(List.of(provider, new ForecastSolar(offline())));
        var apiKeys = new ApiKeys(keychain, new SecretsFile(folder.resolve("secrets.json")));
        engine = new Engine(new ConfigFile(configPath),
                new ForecastService(providers, new ForecastCache(folder.resolve("cache")), apiKeys), providers, apiKeys,
                new AwayFile(folder.resolve("away.json")), new BatteryFile(folder.resolve("battery.json")),
                Clock.systemUTC(), new Announcer(new AnnouncedWindows(folder.resolve("announced.json")), told -> { }),
                new ManualScheduler());
    }

    private static HttpGet offline() {
        return request -> {
            throw new IOException("no network in the tests");
        };
    }

    /** The owner's system as the example describes it, as for running set-up again. */
    SetUpFixture withExample() throws IOException {
        Files.copy(SettingsFixture.EXAMPLE, configPath);
        return this;
    }

    SettingsStores stores() {
        return new SettingsStores(engine, appSettings, Optional.of(login));
    }

    Path keysFile() {
        return folder.resolve("secrets.json");
    }

    /** The view as set-up opens it, on its first step, light. */
    SetUpView open() throws Exception {
        return open(ColorScheme.LIGHT);
    }

    /**
     * The view as set-up opens it, on its first step, light, with the system's text this much larger and the window
     * as much, in no window.
     */
    SetUpView open(TextScale scale) throws Exception {
        return Fx.call(() -> {
            var view = SetUpView.of(stores(), keysFile(), opened::add, Fx.REDUCED_MOTION, () -> closed++,
                    chosen -> finished++, background, messages);
            // as large as the app makes its window for the larger text: so many lines of it
            Scenes.layOut(view, ColorScheme.LIGHT, scale, scale.of(50 * 13), scale.of(46 * 13));
            var start = SetUpStart.read(new SettingsStorage(stores()));
            view.start(start, start.draft(EngineConfig.firstRun(ZoneId.of("Europe/Amsterdam"),
                    engine.providers().getFirst().id())), SetUpStep.WELCOME);
            return view;
        });
    }

    /** The view as set-up opens it, before what is stored was read, shown in this scheme. */
    SetUpView openReading(ColorScheme scheme) throws Exception {
        return Fx.call(() -> {
            var view = SetUpView.of(stores(), keysFile(), opened::add, Fx.REDUCED_MOTION, () -> closed++,
                    chosen -> finished++, background, messages);
            stage = Scenes.show(view, scheme, 50 * 13, 46 * 13);
            return view;
        });
    }

    /** Starts a view made by {@link #openReading(ColorScheme)} with what is stored, as set-up does once it is read. */
    void start(SetUpView view) {
        var start = SetUpStart.read(new SettingsStorage(stores()));
        view.start(start, start.draft(EngineConfig.firstRun(ZoneId.of("Europe/Amsterdam"),
                engine.providers().getFirst().id())), SetUpStep.WELCOME);
    }

    SetUpView open(ColorScheme scheme) throws Exception {
        return Fx.call(() -> {
            var view = SetUpView.of(stores(), keysFile(), opened::add, Fx.REDUCED_MOTION, () -> closed++,
                    chosen -> finished++, background, messages);
            var start = SetUpStart.read(new SettingsStorage(stores()));
            view.start(start, start.draft(EngineConfig.firstRun(ZoneId.of("Europe/Amsterdam"),
                    engine.providers().getFirst().id())), SetUpStep.WELCOME);
            stage = Scenes.show(view, scheme, 50 * 13, 46 * 13);
            return view;
        });
    }

    /** The set-up window, hidden, following a language and the appearance saved over a light system. */
    SetUpWindow window(LanguageChoice language, SimpleObjectProperty<Appearance> saved) throws Exception {
        return Fx.call(() -> new SetUpWindow(stores(), keysFile(), opened::add, chosen -> finished++,
                ZoneId.of("Europe/Amsterdam"), new SetUpWindow.Looks(new SimpleObjectProperty<>(ColorScheme.LIGHT),
                        saved, Fx.REDUCED_MOTION), new AppIcon(), language, background));
    }

    void close() throws Exception {
        if (stage != null) {
            Fx.run(stage::close);
        }
    }

    /** The control a screen reader calls by this name, on the step shown. */
    static Node field(SetUpView view, String name) {
        view.applyCss(); // a page shown since the last pulse has its controls' skins only then
        return Scenes.all(view).stream()
                .filter(node -> SettingsFixture.named(node, name))
                .map(node -> node instanceof Spinner<?> spinner ? spinner.getEditor() : node)
                .findFirst().orElseThrow(() -> new AssertionError("no field " + name));
    }

    Node field(SetUpView view, TextKey name) {
        return field(view, messages.text(name));
    }

    /** The control a screen reader reads by this name, with its help: the spinner itself, not its text field. */
    Node control(SetUpView view, TextKey name) {
        view.applyCss();
        return Scenes.all(view).stream().filter(node -> SettingsFixture.named(node, messages.text(name)))
                .findFirst().orElseThrow(() -> new AssertionError("no field " + name));
    }

    void type(SetUpView view, TextKey name, String text) {
        ((TextField) field(view, name)).setText(text);
    }

    @SuppressWarnings("unchecked")
    <T> void choose(SetUpView view, TextKey name, T choice) {
        ((ComboBox<T>) field(view, name)).setValue(choice);
    }

    /** A button the owner sees, by its text. */
    static Button button(Node root, String text) {
        root.applyCss();
        return Scenes.all(root).stream()
                .filter(node -> node instanceof Button button && text.equals(button.getText()))
                .map(Button.class::cast)
                .filter(Scenes::reallyVisible)
                .findFirst().orElseThrow(() -> new AssertionError("no button " + text));
    }

    Button button(Node root, TextKey text) {
        return button(root, messages.text(text));
    }

    /** The visible texts of the step shown, once laid out. */
    static List<String> texts(SetUpView view) {
        view.applyCss();
        view.layout();
        return Scenes.visibleTexts(view);
    }

    /**
     * Shows the preview and waits until it shows the plan, or why there is none, so nothing is still being written to
     * the test's folder when the test ends.
     */
    void preview(SetUpView view) throws Exception {
        Fx.run(() -> view.show(SetUpStep.PREVIEW));
        awaitPreview(view);
    }

    /** Waits until the preview shown shows the plan, or why there is none. */
    void awaitPreview(SetUpView view) throws Exception {
        SettingsFixture.waitFor(() -> !texts(view).contains(messages.text(TextKey.LOADING)));
    }

    /** Searches the place as the owner does and picks the one found; on the location step. */
    void choosePlace(SetUpView view) throws Exception {
        Fx.run(() -> {
            type(view, TextKey.PLACE_FIELD, "Zonnedorp");
            button(view, TextKey.PLACE_SEARCH).fire();
        });
        SettingsFixture.waitFor(() -> Scenes.visibleTexts(view).contains(PLACE.label()));
        Fx.run(() -> button(view, PLACE.label()).fire());
    }

    /**
     * A provider with a real provider's texts and settings whose answers are played: two sunny days from today, or a
     * failure; its place search finds {@link #PLACE}.
     */
    static final class Played implements ForecastProvider {

        final List<Map<String, String>> asked = new CopyOnWriteArrayList<>();
        private final ProviderInfo info;
        volatile boolean failing;

        Played(ProviderInfo info) {
            this.info = info;
        }

        @Override
        public ProviderInfo info() {
            return info;
        }

        @Override
        public List<ForecastStep> fetch(SystemDescription system, Map<String, String> settings, Instant now)
                throws ProviderError {
            asked.add(Map.copyOf(settings));
            if (failing) {
                throw new ProviderError.Unavailable("no network");
            }
            return SunnyDays.days(LocalDate.ofInstant(now, SunnyDays.TZ), 2, 5.0);
        }

        @Override
        public Optional<PlaceSearch> placeSearch() {
            return Optional.of((text, language) -> new PlaceResults.Found(List.of(PLACE)));
        }
    }
}

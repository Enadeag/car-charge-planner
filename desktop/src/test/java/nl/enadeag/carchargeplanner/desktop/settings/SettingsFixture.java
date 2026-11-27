package nl.enadeag.carchargeplanner.desktop.settings;

import nl.enadeag.carchargeplanner.desktop.Fx;
import nl.enadeag.carchargeplanner.desktop.Scenes;
import nl.enadeag.carchargeplanner.desktop.TextScale;
import nl.enadeag.carchargeplanner.desktop.app.AppSettingsFile;
import nl.enadeag.carchargeplanner.desktop.app.Appearance;
import nl.enadeag.carchargeplanner.desktop.app.Background;
import nl.enadeag.carchargeplanner.desktop.app.Units;
import nl.enadeag.carchargeplanner.desktop.settings.about.AboutFacts;
import nl.enadeag.carchargeplanner.desktop.settings.page.Fold;
import nl.enadeag.carchargeplanner.desktop.settings.store.SaveOutcome;
import nl.enadeag.carchargeplanner.desktop.settings.store.SettingsStores;
import nl.enadeag.carchargeplanner.desktop.system.AppIcon;
import nl.enadeag.carchargeplanner.desktop.system.PosixFilePrivacy;
import nl.enadeag.carchargeplanner.desktop.system.StartAtLogin;
import nl.enadeag.carchargeplanner.desktop.system.TextSize;
import nl.enadeag.carchargeplanner.desktop.text.Language;
import nl.enadeag.carchargeplanner.desktop.text.LanguageChoice;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.desktop.theme.WindowTheme;
import nl.enadeag.carchargeplanner.engine.Engine;
import nl.enadeag.carchargeplanner.engine.away.AwayFile;
import nl.enadeag.carchargeplanner.engine.battery.BatteryFile;
import nl.enadeag.carchargeplanner.engine.config.ConfigFile;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastCache;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastProvider;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastService;
import nl.enadeag.carchargeplanner.engine.forecast.HttpGet;
import nl.enadeag.carchargeplanner.engine.forecast.Providers;
import nl.enadeag.carchargeplanner.engine.forecast.providers.ForecastSolar;
import nl.enadeag.carchargeplanner.engine.forecast.providers.OpenMeteo;
import nl.enadeag.carchargeplanner.engine.notify.AnnouncedWindows;
import nl.enadeag.carchargeplanner.engine.notify.Announcer;
import nl.enadeag.carchargeplanner.engine.notify.ToldTrips;
import nl.enadeag.carchargeplanner.engine.secrets.ApiKeys;
import nl.enadeag.carchargeplanner.engine.secrets.KeyFallback;
import nl.enadeag.carchargeplanner.engine.secrets.Keychain;
import nl.enadeag.carchargeplanner.engine.secrets.NoKeychain;
import nl.enadeag.carchargeplanner.engine.secrets.SecretsFile;
import nl.enadeag.carchargeplanner.engine.store.PrivateFiles;
import nl.enadeag.carchargeplanner.engine.trip.TripFile;
import nl.enadeag.carchargeplanner.host.AppName;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.ColorScheme;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.AccessibleAttribute;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

/**
 * Settings over stores in a test's own folder: the example config, an app settings file, and a start at login that
 * only remembers. Nothing touches the owner's files, keychain or autostart.
 */
public final class SettingsFixture {

    private static final PrivateFiles FILES = new PrivateFiles(new PosixFilePrivacy());

    public static final Path EXAMPLE = Path.of("docs", "example-engine.json");

    final Path folder;
    final Path configPath;
    final AppSettingsFile appSettings;
    final Engine engine;
    final MemoryStartAtLogin login = new MemoryStartAtLogin();
    final List<URI> opened = new ArrayList<>();
    final Messages messages;
    /**
     * Unless a test chose another: runs the work at once, on the thread that asks for it, so the outcome is there as
     * the call returns.
     */
    final Background background;
    int closed;
    int saved;
    private Stage stage;

    SettingsFixture(Path folder, Language language) throws IOException {
        this(folder, language, List.of());
    }

    /** With these forecast providers, such as one with a place search. */
    SettingsFixture(Path folder, Language language, List<ForecastProvider> known) throws IOException {
        this(folder, language, known, new NoKeychain(), new Background(Runnable::run, Runnable::run));
    }

    /** With this keychain, and this way to do the slow work, such as on virtual threads as the app does. */
    SettingsFixture(Path folder, Language language, List<ForecastProvider> known, Keychain keychain,
            Background background) throws IOException {
        this(folder, language, known, keychain, background, Clock.systemUTC());
    }

    /** At a fixed time, such as for a picture that must be the same on every run. */
    SettingsFixture(Path folder, Language language, Clock clock) throws IOException {
        this(folder, language, List.of(), new NoKeychain(), new Background(Runnable::run, Runnable::run), clock);
    }

    private SettingsFixture(Path folder, Language language, List<ForecastProvider> known, Keychain keychain,
            Background background, Clock clock) throws IOException {
        this.folder = folder;
        this.background = background;
        this.messages = new Messages(language);
        configPath = folder.resolve("engine.json");
        Files.copy(EXAMPLE, configPath);
        appSettings = new AppSettingsFile(folder.resolve("app.json"), Units.METRIC, FILES, clock);
        var providers = new Providers(known);
        var apiKeys = new ApiKeys(keychain, new SecretsFile(folder.resolve("secrets.json"), FILES, clock),
                KeyFallback.PRIVATE_FILE, Clock.systemUTC());
        engine = new Engine(new ConfigFile(configPath, FILES, clock),
                new ForecastService(providers, new ForecastCache(folder.resolve("cache"), FILES, clock), apiKeys),
                providers,
                apiKeys,
                new AwayFile(folder.resolve("away.json"), FILES, clock),
                new TripFile(folder.resolve("trips.json"), FILES, clock),
                new BatteryFile(folder.resolve("battery.json"), FILES, clock), clock,
                new Announcer(new AnnouncedWindows(folder.resolve("announced.json"), FILES, clock),
                        new ToldTrips(folder.resolve("trip-notices.json"), FILES, clock), _ -> { }));
    }

    /**
     * The app's own providers, with their real texts and settings, which never reach the network here: any request
     * fails.
     */
    static List<ForecastProvider> realProviders() {
        HttpGet offline = _ -> {
            throw new IOException("no network in the tests");
        };
        return List.of(new OpenMeteo(offline), new ForecastSolar(offline));
    }

    /** The view as Settings opens it, shown light, with start at login available unless {@code installed} is false. */
    SettingsView open(boolean installed) throws Exception {
        return open(installed, ColorScheme.LIGHT);
    }

    SettingsView open(boolean installed, Optional<Runnable> runSetUp) throws Exception {
        return open(installed, ColorScheme.LIGHT, runSetUp);
    }

    SettingsView open(boolean installed, ColorScheme scheme) throws Exception {
        return open(installed, scheme, Optional.empty());
    }

    SettingsView open(boolean installed, ColorScheme scheme, Optional<Runnable> runSetUp) throws Exception {
        return Fx.call(() -> {
            var view = runSetUp
                    .map(setUp -> SettingsView.of(stores(installed), facts(), opened::add, () -> closed++,
                            chosen -> saved++, setUp, background, messages))
                    .orElseGet(() -> SettingsView.of(stores(installed), facts(), opened::add, () -> closed++,
                            chosen -> saved++, background, messages));
            view.reload();
            stage = Scenes.show(view, scheme, 56 * 13, 40 * 13);
            return view;
        });
    }

    SettingsStores stores(boolean installed) {
        return new SettingsStores(engine, appSettings, installed ? Optional.of(login) : Optional.empty(),
                () -> ZoneId.of("Europe/Amsterdam"));
    }

    AboutFacts facts() {
        return new AboutFacts("1.2.3", configPath, appSettings.path(), folder.resolve("data"),
                folder.resolve("data").resolve("app.0.log"), folder.resolve("secrets.json"));
    }

    /**
     * The Settings window, hidden, following a language and an appearance over the system's scheme; after a Save it
     * chooses the language and appearance the app settings file holds, as the app does.
     */
    SettingsWindow window(LanguageChoice language, ObjectProperty<Appearance> appearance,
            ObservableValue<ColorScheme> system) throws Exception {
        return Fx.call(() -> new SettingsWindow(stores(true), facts(), opened::add, chosen -> {
            saved++;
            chosen.language().ifPresentOrElse(language::choose, language::followSystem);
            appearance.set(chosen.appearance());
        }, () -> { }, Scenes.theme(Appearance.scheme(appearance, system)), new AppIcon(), AppName.TITLE, language, background));
    }

    /** The Settings window, hidden, light, following a language as {@link #window(LanguageChoice, ObjectProperty, ObservableValue)}. */
    SettingsWindow window(LanguageChoice language) throws Exception {
        return window(language, new SimpleObjectProperty<>(Appearance.SYSTEM), new SimpleObjectProperty<>(ColorScheme.LIGHT));
    }

    /** The Settings window, hidden, in English, light, with the system's text this large; made on the JavaFX thread. */
    SettingsWindow window(ObservableValue<TextSize> textSize) throws Exception {
        return Fx.call(() -> new SettingsWindow(stores(true), facts(), opened::add, chosen -> saved++, () -> { },
                new WindowTheme(new SimpleObjectProperty<>(ColorScheme.LIGHT), textSize), new AppIcon(),
                AppName.TITLE, new LanguageChoice(Language.ENGLISH), background));
    }

    SettingsView open() throws Exception {
        return open(true);
    }

    /**
     * The view as Settings opens it, light, with the system's text this much larger and the window as much, in no
     * window.
     */
    SettingsView open(TextScale scale) throws Exception {
        return Fx.call(() -> {
            var view = SettingsView.of(stores(true), facts(), opened::add, () -> closed++, chosen -> saved++,
                    background, messages);
            view.reload();
            // as large as the app makes its window for the larger text: so many lines of it
            Scenes.layOut(view, ColorScheme.LIGHT, scale, scale.of(56 * 13), scale.of(40 * 13));
            return view;
        });
    }

    /**
     * Presses Save as the owner does and waits for what it came to; with this fixture's background, the save is done
     * by the time this returns. Call on the JavaFX thread.
     *
     * @return true when everything was stored
     */
    static boolean save(SettingsView view) {
        view.save(() -> { });
        return view.outcome().filter(SaveOutcome.Saved.class::isInstance).isPresent();
    }

    void close() throws Exception {
        if (stage != null) {
            Fx.run(stage::close);
        }
    }

    /**
     * The control this name is the label of, or a check box that says it, the place the owner types or chooses in, on
     * any category's page.
     */
    static Node field(SettingsView view, String name) {
        // a page not shown yet has no skins, so a spinner's text field is found through the spinner
        return view.pages().stream().flatMap(page -> Scenes.all(page.view()).stream())
                .filter(node -> named(node, name))
                .filter(SettingsFixture::isNoFoldRow)
                .map(node -> node instanceof Spinner<?> spinner ? spinner.getEditor() : node)
                .findFirst().orElseThrow(() -> new AssertionError("no field " + name));
    }

    /** Whether a control shows this name: a label whose {@code labelFor} it is says it, or, a check box, it does. */
    public static boolean named(Node node, String name) {
        if (node instanceof CheckBox box) {
            return name.equals(box.getText());
        }
        return node.queryAccessibleAttribute(AccessibleAttribute.LABELED_BY) instanceof Label label
                && name.equals(label.getText());
    }

    /**
     * What a field's row shows below it or before it, as one text: the problem first, then the tag and the sentence
     * that explains it; for a weekly away period, its problem.
     *
     * @param control the field, or a part of it
     * @return the texts shown, separated by a space; empty when none shows
     */
    public static String shownWith(Node control) {
        var row = control;
        while (row != null && !row.getStyleClass().contains("field-row") && !row.getStyleClass().contains("away-row")) {
            row = row.getParent();
        }
        if (row == null) {
            throw new AssertionError("no field row around " + control);
        }
        var texts = new ArrayList<String>();
        for (var style : List.of("field-problem", "field-tag", "note")) {
            Scenes.all(row).stream()
                    .filter(node -> node instanceof Label label && label.getStyleClass().contains(style)
                            && label.isVisible() && !label.getText().isEmpty())
                    .map(node -> ((Label) node).getText()).forEach(texts::add);
        }
        return String.join(" ", texts);
    }

    /** A fold's row can be named as the field under it, such as a provider's "API key": it is no field. */
    public static boolean isNoFoldRow(Node node) {
        return !(node.getParent() instanceof Fold);
    }

    Node field(SettingsView view, TextKey name) {
        return field(view, messages.text(name));
    }

    /** The control this name is the label of: the spinner itself, not its text field. */
    Node control(SettingsView view, TextKey name) {
        return view.pages().stream().flatMap(page -> Scenes.all(page.view()).stream())
                .filter(node -> named(node, messages.text(name)))
                .findFirst().orElseThrow(() -> new AssertionError("no field " + name));
    }

    /** Types into a number field as the owner does, without confirming it with Enter. */
    void type(SettingsView view, TextKey name, String text) {
        ((TextField) field(view, name)).setText(text);
    }

    @SuppressWarnings("unchecked")
    <T> void choose(SettingsView view, TextKey name, T choice) {
        ((ComboBox<T>) field(view, name)).setValue(choice);
    }

    Button button(Node root, TextKey text) {
        return Scenes.all(root).stream()
                .filter(node -> node instanceof Button button && button.getText().equals(messages.text(text)))
                .map(Button.class::cast)
                .filter(Scenes::reallyVisible)
                .findFirst().orElseThrow(() -> new AssertionError("no button " + text));
    }

    /** Waits until the JavaFX thread finds the condition true, such as after a search on another thread. */
    public static void waitFor(java.util.concurrent.Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while (!Fx.call(condition)) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("waited in vain");
            }
            Thread.onSpinWait();
        }
    }

    /** A start at login that only remembers, and can be told to fail, or to wait before it switches. */
    public static final class MemoryStartAtLogin implements StartAtLogin {

        public volatile boolean on;
        volatile boolean failing;
        /** Switching on waits until this opens; open unless a test closes it. */
        public volatile CountDownLatch slow = new CountDownLatch(0);
        public final AtomicInteger switches = new AtomicInteger();

        @Override
        public boolean isOn() {
            return on;
        }

        @Override
        public void switchOn() throws IOException {
            switches.incrementAndGet();
            try {
                slow.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException _) {
                throw new IOException("interrupted");
            }
            if (failing) {
                throw new IOException("access denied");
            }
            on = true;
        }

        @Override
        public void switchOff() throws IOException {
            if (failing) {
                throw new IOException("access denied");
            }
            on = false;
        }
    }
}

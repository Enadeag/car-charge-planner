package io.github.enadeag.carchargeplanner.desktop.settings;

import io.github.enadeag.carchargeplanner.desktop.AppSettingsFile;
import io.github.enadeag.carchargeplanner.desktop.Fx;
import io.github.enadeag.carchargeplanner.desktop.Language;
import io.github.enadeag.carchargeplanner.desktop.Messages;
import io.github.enadeag.carchargeplanner.desktop.Scenes;
import io.github.enadeag.carchargeplanner.desktop.StartAtLogin;
import io.github.enadeag.carchargeplanner.desktop.TextKey;
import io.github.enadeag.carchargeplanner.desktop.Units;
import io.github.enadeag.carchargeplanner.engine.Engine;
import io.github.enadeag.carchargeplanner.engine.away.AwayFile;
import io.github.enadeag.carchargeplanner.engine.battery.BatteryFile;
import io.github.enadeag.carchargeplanner.engine.config.ConfigFile;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastCache;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastService;
import io.github.enadeag.carchargeplanner.engine.forecast.Providers;
import io.github.enadeag.carchargeplanner.engine.notify.AnnouncedWindows;
import io.github.enadeag.carchargeplanner.engine.notify.Announcer;
import io.github.enadeag.carchargeplanner.engine.secrets.ApiKeys;
import io.github.enadeag.carchargeplanner.engine.secrets.NoKeychain;
import io.github.enadeag.carchargeplanner.engine.secrets.SecretsFile;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.application.ColorScheme;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

/**
 * Settings over stores in a test's own folder: the example config, an app settings file, and a start at login that
 * only remembers. Nothing touches the owner's files, keychain or autostart.
 */
final class SettingsFixture {

    static final Path EXAMPLE = Path.of("docs", "example-engine.json");

    final Path folder;
    final Path configPath;
    final AppSettingsFile appSettings;
    final Engine engine;
    final MemoryStartAtLogin login = new MemoryStartAtLogin();
    final List<URI> opened = new ArrayList<>();
    final Messages messages;
    int closed;
    int saved;
    private Stage stage;

    SettingsFixture(Path folder, Language language) throws IOException {
        this.folder = folder;
        this.messages = new Messages(language);
        configPath = folder.resolve("engine.json");
        Files.copy(EXAMPLE, configPath);
        appSettings = new AppSettingsFile(folder.resolve("app.json"), Units.METRIC);
        var providers = new Providers(List.of());
        var apiKeys = new ApiKeys(new NoKeychain(), new SecretsFile(folder.resolve("secrets.json")));
        engine = new Engine(new ConfigFile(configPath),
                new ForecastService(providers, new ForecastCache(folder.resolve("cache")), apiKeys), providers, apiKeys,
                new AwayFile(folder.resolve("away.json")), new BatteryFile(folder.resolve("battery.json")),
                Clock.systemUTC(), new Announcer(new AnnouncedWindows(folder.resolve("announced.json")), told -> { }));
    }

    /** The view as Settings opens it, shown light, with start at login available unless {@code installed} is false. */
    SettingsView open(boolean installed) throws Exception {
        return open(installed, ColorScheme.LIGHT);
    }

    SettingsView open(boolean installed, ColorScheme scheme) throws Exception {
        return Fx.call(() -> {
            var stores = new SettingsStores(engine, appSettings, installed ? Optional.of(login) : Optional.empty());
            var facts = new AboutFacts("1.2.3", configPath, appSettings.path(), folder.resolve("data"),
                    folder.resolve("secrets.json"));
            var view = SettingsView.of(stores, facts, opened::add, () -> closed++, () -> saved++, messages);
            view.reload();
            stage = Scenes.show(view, scheme);
            stage.setWidth(56 * 13);
            stage.setHeight(40 * 13);
            return view;
        });
    }

    SettingsView open() throws Exception {
        return open(true);
    }

    void close() throws Exception {
        if (stage != null) {
            Fx.run(stage::close);
        }
    }

    /**
     * The control whose screen reader name is this field's name, the place the owner types or chooses in, on any
     * category's page.
     */
    static Node field(SettingsView view, String name) {
        return view.pages().stream().flatMap(page -> Scenes.all(page.view()).stream())
                .filter(node -> name.equals(node.getAccessibleText()))
                .findFirst().orElseThrow(() -> new AssertionError("no field " + name));
    }

    Node field(SettingsView view, TextKey name) {
        return field(view, messages.text(name));
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

    /** A start at login that only remembers, and can be told to fail. */
    static final class MemoryStartAtLogin implements StartAtLogin {

        boolean on;
        boolean failing;

        @Override
        public boolean isOn() {
            return on;
        }

        @Override
        public void switchOn() throws IOException {
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

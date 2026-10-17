package io.github.enadeag.carchargeplanner.platform.linux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.enadeag.carchargeplanner.engine.config.ProviderId;
import io.github.enadeag.carchargeplanner.engine.secrets.ApiKeys;
import io.github.enadeag.carchargeplanner.engine.secrets.KeyStorage;
import io.github.enadeag.carchargeplanner.engine.secrets.KeychainException;
import io.github.enadeag.carchargeplanner.engine.secrets.Secrets;
import io.github.enadeag.carchargeplanner.engine.secrets.SecretsFile;
import io.github.enadeag.carchargeplanner.engine.secrets.SecretsProblem;
import io.github.enadeag.carchargeplanner.engine.secrets.UnlockOutcome;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.DBus;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.types.Variant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Against a throwaway GNOME Keyring on a private D-Bus of its own, never the developer's login keyring: both daemons
 * run with an environment that holds only folders inside the test's own, and the bus has no service folders, so
 * nothing can start the real keyring daemon either.
 */
@EnabledOnOs(OS.LINUX)
class SecretServiceKeychainTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @TempDir
    Path folder;

    private Process bus;
    private Process keyring;
    private String address;

    /** Only for the test: locking the throwaway keyring, and asking to unlock it on a connection of the test's own. */
    @DBusInterfaceName("org.freedesktop.Secret.Service")
    interface Locker extends DBusInterface {
        SecretService.Pair<List<DBusPath>, DBusPath> Lock(List<DBusPath> objects);

        SecretService.Pair<List<DBusPath>, DBusPath> Unlock(List<DBusPath> objects);

        DBusPath ReadAlias(String name);
    }

    @BeforeEach
    void startAnIsolatedSessionBus() throws IOException {
        var socket = folder.resolve("bus");
        var config = Files.writeString(folder.resolve("bus.conf"), """
                <!DOCTYPE busconfig PUBLIC "-//freedesktop//DTD D-Bus Bus Configuration 1.0//EN"
                 "http://www.freedesktop.org/standards/dbus/1.0/busconfig.dtd">
                <busconfig>
                  <type>session</type>
                  <auth>EXTERNAL</auth>
                  <listen>unix:path=%s</listen>
                  <policy context="default">
                    <allow send_destination="*"/>
                    <allow receive_sender="*"/>
                    <allow own="*"/>
                  </policy>
                </busconfig>
                """.formatted(socket));
        try {
            bus = sandboxed("dbus-daemon", "--nofork", "--nopidfile", "--config-file=" + config, "--print-address=1").start();
        } catch (IOException e) {
            assumeTrue(false, "dbus-daemon is not installed");
        }
        // it prints the address once it accepts connections
        address = new BufferedReader(new InputStreamReader(bus.getInputStream(), StandardCharsets.UTF_8)).readLine().strip();
    }

    private void startAThrowawayKeyring() throws Exception {
        try {
            keyring = sandboxed("gnome-keyring-daemon", "--foreground", "--unlock", "--components=secrets").start();
        } catch (IOException e) {
            assumeTrue(false, "gnome-keyring-daemon is not installed");
        }
        try (var password = keyring.getOutputStream()) {
            password.write("throwaway".getBytes(StandardCharsets.UTF_8));
        }
        try (var watcher = connect()) {
            var dbus = watcher.getRemoteObject("org.freedesktop.DBus", "/org/freedesktop/DBus", DBus.class);
            var deadline = Instant.now().plus(TIMEOUT);
            while (!dbus.NameHasOwner(SecretService.BUS_NAME)) {
                assertThat(Instant.now()).as("the keyring takes its name on the bus").isBefore(deadline);
                Thread.sleep(20);
            }
        }
    }

    /** A process that sees only the test's folders and the private bus. */
    private ProcessBuilder sandboxed(String... command) throws IOException {
        var run = Files.createDirectories(folder.resolve("run"));
        Files.setPosixFilePermissions(run, PosixFilePermissions.fromString("rwx------"));
        var builder = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD);
        var environment = builder.environment();
        var path = environment.get("PATH");
        environment.clear();
        environment.put("PATH", path);
        environment.put("HOME", folder.resolve("home").toString());
        environment.put("XDG_RUNTIME_DIR", run.toString());
        environment.put("XDG_DATA_HOME", folder.resolve("data").toString());
        environment.put("XDG_CONFIG_HOME", folder.resolve("config").toString());
        environment.put("XDG_CACHE_HOME", folder.resolve("cache").toString());
        if (address != null) {
            environment.put("DBUS_SESSION_BUS_ADDRESS", address);
        }
        return builder;
    }

    @AfterEach
    void stopTheDaemons() throws InterruptedException {
        for (var daemon : new Process[] {keyring, bus}) {
            if (daemon != null) {
                daemon.destroy();
                daemon.waitFor();
            }
        }
    }

    private DBusConnection connect() throws DBusException {
        return DBusConnectionBuilder.forAddress(address).withShared(false).build();
    }

    private SecretServiceKeychain keychain(String application) {
        return new SecretServiceKeychain(this::connect, application);
    }

    /** Every connection the keychain made, oldest first. */
    private final List<DBusConnection> made = new CopyOnWriteArrayList<>();

    private SecretServiceKeychain counted() {
        return new SecretServiceKeychain(() -> {
            var connection = connect();
            made.add(connection);
            return connection;
        }, "car-charge-planner");
    }

    /** A Secret Service that takes the name and then never answers a search, until it is let go. */
    private static final class Silent implements SecretService.Service {
        final CountDownLatch letGo = new CountDownLatch(1);

        @Override
        public SecretService.Pair<Variant<?>, DBusPath> OpenSession(String algorithm, Variant<?> input) {
            return new SecretService.Pair<>(new Variant<>(""), new DBusPath("/session"));
        }

        @Override
        public SecretService.Pair<List<DBusPath>, List<DBusPath>> SearchItems(Map<String, String> attributes) {
            try {
                letGo.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new SecretService.Pair<>(List.of(), List.of());
        }

        @Override
        public Map<DBusPath, SecretService.Secret> GetSecrets(List<DBusPath> items, DBusPath session) {
            return Map.of();
        }

        @Override
        public SecretService.Pair<List<DBusPath>, DBusPath> Unlock(List<DBusPath> objects) {
            return new SecretService.Pair<>(objects, new DBusPath(SecretService.NO_PROMPT));
        }

        @Override
        public DBusPath ReadAlias(String name) {
            return new DBusPath(SecretService.DEFAULT_COLLECTION);
        }

        @Override
        public String getObjectPath() {
            return SecretService.SERVICE_PATH;
        }
    }

    @Test
    void aValueIsWrittenReadBackReplacedAndDeleted() throws Exception {
        startAThrowawayKeyring();
        var keychain = keychain("car-charge-planner");

        assertThat(keychain.read("forecast/open-meteo/api_key")).isEmpty();
        keychain.write("forecast/open-meteo/api_key", "FIRST-KEY");
        keychain.write("forecast/open-meteo/api_key", "SECOND-KEY-ëß");
        assertThat(keychain.read("forecast/open-meteo/api_key")).contains("SECOND-KEY-ëß");
        keychain.delete("forecast/open-meteo/api_key");
        keychain.delete("forecast/open-meteo/api_key"); // already gone: nothing happens
        assertThat(keychain.read("forecast/open-meteo/api_key")).isEmpty();
    }

    @Test
    void anotherApplicationsItemsAreNotSeen() throws Exception {
        startAThrowawayKeyring();
        keychain("another-app").write("index", "theirs");

        assertThat(keychain("car-charge-planner").read("index")).isEmpty();
        keychain("car-charge-planner").delete("index");
        assertThat(keychain("another-app").read("index")).contains("theirs");
    }

    @Test
    void theKeysAreKeptInTheKeyring() throws Exception {
        startAThrowawayKeyring();
        var keys = new ApiKeys(keychain("car-charge-planner"), new SecretsFile(folder.resolve("secrets.json")));
        var secrets = new Secrets(Map.of(new ProviderId("open-meteo"), Map.of("api_key", "OM-SECRET-1")));

        keys.save(secrets);

        assertThat(keys.load()).isEqualTo(secrets);
        assertThat(keys.storage()).isEqualTo(KeyStorage.KEYCHAIN);
        assertThat(folder.resolve("secrets.json")).doesNotExist();
    }

    /** Locks the throwaway keyring's default collection, as a keyring is locked after a login without its password. */
    private void lockTheKeyring() throws Exception {
        try (var connection = connect()) {
            connection.getRemoteObject(SecretService.BUS_NAME, SecretService.SERVICE_PATH, Locker.class)
                    .Lock(List.of(new DBusPath(SecretService.DEFAULT_COLLECTION)));
        }
    }

    @Test
    void aLockedKeyringIsReportedAsLockedAndTheKeysAreNotAvailableYet() throws Exception {
        startAThrowawayKeyring();
        var keychain = keychain("car-charge-planner");
        keychain.write("index", "[]");
        lockTheKeyring();

        assertThatThrownBy(() -> keychain.read("index")).isInstanceOfSatisfying(KeychainException.class,
                e -> assertThat(e.kind()).isEqualTo(KeychainException.Kind.LOCKED));
        assertThatThrownBy(() -> keychain.write("index", "[]")).isInstanceOfSatisfying(KeychainException.class,
                e -> assertThat(e.kind()).isEqualTo(KeychainException.Kind.LOCKED));
        var keys = new ApiKeys(keychain, new SecretsFile(folder.resolve("secrets.json")));
        assertThatThrownBy(keys::load).isInstanceOfSatisfying(SecretsProblem.class,
                problem -> assertThat(problem.kind()).isEqualTo(SecretsProblem.Kind.LOCKED));
        assertThat(keys.storage()).isEqualTo(KeyStorage.LOCKED_KEYCHAIN);
        assertThat(folder.resolve("secrets.json")).doesNotExist();
    }

    @Test
    void unlockingALockedKeyringAsksForThePromptOfGnomeKeyring() throws Exception {
        startAThrowawayKeyring();
        var keychain = keychain("car-charge-planner");
        keychain.write("index", "[]");
        lockTheKeyring();
        try (var connection = connect()) {
            var service = connection.getRemoteObject(SecretService.BUS_NAME, SecretService.SERVICE_PATH, Locker.class);
            var collection = service.ReadAlias(SecretService.DEFAULT_ALIAS);
            var asked = service.Unlock(List.of(collection));
            assertThat(asked.first).as("nothing is unlocked without the owner").isEmpty();
            assertThat(asked.second.getPath()).as("a prompt to show").startsWith("/org/freedesktop/secrets/prompt/");
        }

        // the private bus has no GNOME prompter (gcr), so GNOME Keyring cannot show a real one and ends it dismissed
        assertThat(keychain.unlock(TIMEOUT, () -> { })).isEqualTo(UnlockOutcome.DISMISSED);
        assertThatThrownBy(() -> keychain.read("index")).isInstanceOfSatisfying(KeychainException.class,
                e -> assertThat(e.kind()).isEqualTo(KeychainException.Kind.LOCKED));
        var keys = new ApiKeys(keychain, new SecretsFile(folder.resolve("secrets.json")));
        keys.save(new Secrets(Map.of(new ProviderId("open-meteo"), Map.of("api_key", "OM-SECRET-1"))));
        assertThat(keys.storage()).as("dismissed: the owner chose the file").isEqualTo(KeyStorage.FILE);
        keychain.close();
    }

    @Test
    void oneConnectionServesEveryCallAndClosingTheKeychainClosesIt() throws Exception {
        startAThrowawayKeyring();
        var keychain = counted();
        var keys = new ApiKeys(keychain, new SecretsFile(folder.resolve("secrets.json")));
        var secrets = new Secrets(Map.of(new ProviderId("open-meteo"), Map.of("api_key", "OM-SECRET-1")));

        keys.save(secrets);
        assertThat(new ApiKeys(keychain, new SecretsFile(folder.resolve("secrets.json"))).load()).isEqualTo(secrets);
        keychain.delete("index");

        assertThat(made).hasSize(1);
        assertThat(made.getFirst().isConnected()).isTrue();
        keychain.close();
        assertThat(made.getFirst().isConnected()).isFalse();
    }

    @Test
    void aConnectionThatBrokeIsMadeAgainAtTheNextCall() throws Exception {
        startAThrowawayKeyring();
        var keychain = counted();
        keychain.write("index", "[]");
        made.getFirst().disconnect();

        assertThat(keychain.read("index")).contains("[]");
        assertThat(made).hasSize(2);
        keychain.close();
    }

    @Test
    void aSecretServiceThatDoesNotAnswerInTimeCountsAsNoKeychain() throws Exception {
        var silent = new Silent();
        try (var service = connect()) {
            service.exportObject(silent);
            service.requestBusName(SecretService.BUS_NAME);
            var keychain = new SecretServiceKeychain(this::connect, "car-charge-planner", Duration.ofMillis(300));

            assertThatThrownBy(() -> keychain.read("index")).isInstanceOf(KeychainException.class)
                    .hasMessageContaining("did not answer");
            silent.letGo.countDown();
            keychain.close();
        }
    }

    @Test
    void withoutASecretServiceOnTheBusThereIsNoKeychain() {
        assertThatThrownBy(() -> keychain("car-charge-planner").read("index")).isInstanceOf(KeychainException.class)
                .hasMessageContaining("no Secret Service");
        assertThat(new ApiKeys(keychain("car-charge-planner"), new SecretsFile(folder.resolve("secrets.json"))).storage())
                .isEqualTo(KeyStorage.FILE);
    }
}

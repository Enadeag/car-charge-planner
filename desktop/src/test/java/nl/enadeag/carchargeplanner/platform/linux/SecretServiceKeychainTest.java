package nl.enadeag.carchargeplanner.platform.linux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import nl.enadeag.carchargeplanner.desktop.system.PosixFilePrivacy;
import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import nl.enadeag.carchargeplanner.engine.secrets.ApiKeys;
import nl.enadeag.carchargeplanner.engine.secrets.KeyStorage;
import nl.enadeag.carchargeplanner.engine.secrets.Keychain;
import nl.enadeag.carchargeplanner.engine.secrets.KeychainContractTest;
import nl.enadeag.carchargeplanner.engine.secrets.KeychainException;
import nl.enadeag.carchargeplanner.engine.secrets.Secrets;
import nl.enadeag.carchargeplanner.engine.secrets.SecretsFile;
import nl.enadeag.carchargeplanner.engine.secrets.SecretsProblem;
import nl.enadeag.carchargeplanner.engine.secrets.UnlockOutcome;
import nl.enadeag.carchargeplanner.engine.store.PrivateFiles;
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
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
 * nothing can start the real keyring daemon either. The contract every keychain keeps is checked there too; the tests
 * here add what only the Secret Service has: other applications' items, locking, its connection and its absence.
 */
@EnabledOnOs(OS.LINUX)
class SecretServiceKeychainTest extends KeychainContractTest {

    private static final PrivateFiles FILES = new PrivateFiles(new PosixFilePrivacy());

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @TempDir
    Path folder;

    private Process bus;
    private Process keyring;
    private String address;
    /** The keychain the contract's tests use, once one asked for it. */
    private SecretServiceKeychain forTheContract;

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
        bus = SystemPrograms.start(
                sandboxed("dbus-daemon", "--nofork", "--nopidfile", "--config-file=" + config, "--print-address=1"));
        // it prints the address once it accepts connections
        address = new BufferedReader(new InputStreamReader(bus.getInputStream(), StandardCharsets.UTF_8)).readLine().strip();
    }

    private void startAThrowawayKeyring() throws Exception {
        keyring = SystemPrograms.start(
                sandboxed("gnome-keyring-daemon", "--foreground", "--unlock", "--components=secrets"));
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

    @Override
    protected Keychain keychain() throws Exception {
        if (forTheContract == null) {
            startAThrowawayKeyring();
            forTheContract = keychain("car-charge-planner");
        }
        return forTheContract;
    }

    @AfterEach
    void stopTheDaemons() throws InterruptedException {
        if (forTheContract != null) {
            forTheContract.close();
        }
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

    /** How long a call may take in the tests of a call given up on: short, as they wait for it, yet room to connect. */
    private static final Duration SHORT = Duration.ofSeconds(1);

    /**
     * Connects to the private bus, but holds the first connect until it is let go, ignoring interrupts as a D-Bus
     * handshake may; counts how many connect at once.
     */
    private final class HeldFirstConnect implements BusConnector {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch letGo = new CountDownLatch(1);
        final CountDownLatch firstEnded = new CountDownLatch(1);
        final AtomicInteger connects = new AtomicInteger();
        final AtomicInteger inside = new AtomicInteger();
        final AtomicInteger mostAtOnce = new AtomicInteger();

        @Override
        public DBusConnection connect() throws DBusException {
            mostAtOnce.accumulateAndGet(inside.incrementAndGet(), Math::max);
            var first = connects.getAndIncrement() == 0;
            try {
                if (first) {
                    entered.countDown();
                    awaitIgnoringInterrupts(letGo);
                }
                var connection = SecretServiceKeychainTest.this.connect();
                made.add(connection);
                return connection;
            } finally {
                inside.decrementAndGet();
                if (first) {
                    firstEnded.countDown();
                }
            }
        }

        private static void awaitIgnoringInterrupts(CountDownLatch latch) {
            while (true) {
                try {
                    if (latch.await(30, TimeUnit.SECONDS)) {
                        return;
                    }
                } catch (InterruptedException _) {
                    // a handshake that is under way goes on
                }
            }
        }

        /** Waits until the first connect, let go, has ended. */
        void awaitTheFirstConnectsEnd() throws InterruptedException {
            assertThat(firstEnded.await(10, TimeUnit.SECONDS)).as("the first connect ended").isTrue();
        }

        /** Waits until the first connect is held, as the call that gave up started it. */
        void awaitTheFirstConnect() throws InterruptedException {
            assertThat(entered.await(10, TimeUnit.SECONDS)).as("the first connect started").isTrue();
        }
    }

    /** A Secret Service that takes the name and then never answers a search, until it is let go. */
    private static final class Silent implements SecretService.Service {
        final CountDownLatch letGo = new CountDownLatch(1);
        final CountDownLatch firstEnded = new CountDownLatch(1);

        @Override
        public SecretService.Pair<Variant<?>, DBusPath> OpenSession(String algorithm, Variant<?> input) {
            return new SecretService.Pair<>(new Variant<>(""), new DBusPath("/session"));
        }

        @Override
        public SecretService.Pair<List<DBusPath>, List<DBusPath>> SearchItems(Map<String, String> attributes) {
            try {
                letGo.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException _) {
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
            return new SecretService.Pair<>(objects, new DBusPath(SecretService.NO_OBJECT));
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
        var keys = new ApiKeys(keychain("car-charge-planner"), new SecretsFile(folder.resolve("secrets.json"), FILES));
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
        assertThatThrownBy(() -> keychain.delete("index")).isInstanceOfSatisfying(KeychainException.class,
                e -> assertThat(e.kind()).isEqualTo(KeychainException.Kind.LOCKED));
        var keys = new ApiKeys(keychain, new SecretsFile(folder.resolve("secrets.json"), FILES));
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
        var keys = new ApiKeys(keychain, new SecretsFile(folder.resolve("secrets.json"), FILES));
        keys.save(new Secrets(Map.of(new ProviderId("open-meteo"), Map.of("api_key", "OM-SECRET-1"))));
        assertThat(keys.storage()).as("dismissed: the owner chose the file").isEqualTo(KeyStorage.FILE);
        keychain.close();
    }

    @Test
    void oneConnectionServesEveryCallAndClosingTheKeychainClosesIt() throws Exception {
        startAThrowawayKeyring();
        var keychain = counted();
        var keys = new ApiKeys(keychain, new SecretsFile(folder.resolve("secrets.json"), FILES));
        var secrets = new Secrets(Map.of(new ProviderId("open-meteo"), Map.of("api_key", "OM-SECRET-1")));

        keys.save(secrets);
        assertThat(new ApiKeys(keychain, new SecretsFile(folder.resolve("secrets.json"), FILES)).load())
                .isEqualTo(secrets);
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
    void aCallNeverConnectsWhileAStepItsCallerGaveUpOnIsStillConnecting() throws Exception {
        startAThrowawayKeyring();
        var connector = new HeldFirstConnect();
        var keychain = new SecretServiceKeychain(connector, "car-charge-planner", SHORT);
        try {
            assertThatThrownBy(() -> keychain.read("index")).isInstanceOf(KeychainException.class);
            connector.awaitTheFirstConnect();

            // either outcome is allowed: its step may only wait for the first connect to end
            catchThrowable(() -> keychain.read("index"));
            connector.letGo.countDown();
            connector.awaitTheFirstConnectsEnd();

            assertThat(connector.connects).as("connects, the given-up first one only").hasValue(1);
            assertThat(connector.mostAtOnce).as("connects at the same time").hasValue(1);
        } finally {
            connector.letGo.countDown();
            keychain.close();
        }
    }

    @Test
    void aWriteWhoseCallerGaveUpWhileItConnectedDoesNotLandAfterwards() throws Exception {
        startAThrowawayKeyring();
        var connector = new HeldFirstConnect();
        var keychain = new SecretServiceKeychain(connector, "car-charge-planner", SHORT);
        try {
            assertThatThrownBy(() -> keychain.write("index", "late")).isInstanceOf(KeychainException.class);
            connector.awaitTheFirstConnect();

            connector.letGo.countDown();
            // its step takes the connecting lock after the first one let go of it
            var next = keychain.read("index");

            assertThat(made.getFirst().isConnected()).as("the connection made for the given-up write").isFalse();
            assertThat(next).as("read on the next connection").isEmpty();
            assertThat(keychain("car-charge-planner").read("index")).as("read on a connection of its own").isEmpty();
        } finally {
            connector.letGo.countDown();
            keychain.close();
        }
    }

    @Test
    void aConnectionMadeAfterTheKeychainClosedIsNotLeftOpen() throws Exception {
        startAThrowawayKeyring();
        var connector = new HeldFirstConnect();
        var keychain = new SecretServiceKeychain(connector, "car-charge-planner", TIMEOUT);
        var reading = new FutureTask<>(() -> keychain.read("index"));
        Thread.ofVirtual().start(reading);
        try {
            connector.awaitTheFirstConnect();

            keychain.close();
            connector.letGo.countDown();
            catchThrowable(() -> reading.get(10, TimeUnit.SECONDS)); // a call that meets close() may fail

            assertThat(made).hasSize(1);
            assertThat(made.getFirst().isConnected()).as("the connection made after close()").isFalse();
        } finally {
            connector.letGo.countDown();
            keychain.close();
        }
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
        assertThat(new ApiKeys(keychain("car-charge-planner"), new SecretsFile(folder.resolve("secrets.json"), FILES))
                .storage())
                .isEqualTo(KeyStorage.FILE);
    }
}

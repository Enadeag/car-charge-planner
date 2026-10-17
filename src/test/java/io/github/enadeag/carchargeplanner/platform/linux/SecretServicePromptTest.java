package io.github.enadeag.carchargeplanner.platform.linux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.enadeag.carchargeplanner.engine.secrets.KeychainException;
import io.github.enadeag.carchargeplanner.engine.secrets.UnlockOutcome;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.types.Variant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * The unlock prompt's outcomes, against a fake Secret Service on a private D-Bus: a throwaway GNOME Keyring cannot show
 * a real prompt there (it has no prompter), so this service answers the prompt as the test says, the way GNOME Keyring
 * and KWallet send their {@code Completed} signal.
 */
@EnabledOnOs(OS.LINUX)
class SecretServicePromptTest {

    private static final String COLLECTION = "/org/freedesktop/secrets/collection/login";
    private static final String PROMPT = "/org/freedesktop/secrets/prompt/u1";
    private static final Duration WAIT = Duration.ofSeconds(10);

    @TempDir
    Path folder;

    private IsolatedSessionBus bus;
    private DBusConnection desktop;
    private final List<DBusConnection> made = new CopyOnWriteArrayList<>();

    /** The service's side: its default keyring is locked, and unlocking it needs the prompt. */
    private static final class LockedService implements SecretService.Service {
        volatile String alias = COLLECTION;
        volatile String prompt = PROMPT;
        volatile List<DBusPath> unlockedAtOnce = List.of();

        @Override
        public SecretService.Pair<Variant<?>, DBusPath> OpenSession(String algorithm, Variant<?> input) {
            return new SecretService.Pair<>(new Variant<>(""), new DBusPath("/session"));
        }

        @Override
        public SecretService.Pair<List<DBusPath>, List<DBusPath>> SearchItems(Map<String, String> attributes) {
            return new SecretService.Pair<>(List.of(), List.of(new DBusPath(COLLECTION + "/1")));
        }

        @Override
        public Map<DBusPath, SecretService.Secret> GetSecrets(List<DBusPath> items, DBusPath session) {
            return Map.of();
        }

        @Override
        public SecretService.Pair<List<DBusPath>, DBusPath> Unlock(List<DBusPath> objects) {
            return new SecretService.Pair<>(unlockedAtOnce, new DBusPath(prompt));
        }

        @Override
        public DBusPath ReadAlias(String name) {
            return new DBusPath(SecretService.DEFAULT_ALIAS.equals(name) ? alias : SecretService.NO_PROMPT);
        }

        @Override
        public String getObjectPath() {
            return SecretService.SERVICE_PATH;
        }
    }

    /** The prompt: shows, and ends as the owner answers, or dismissed when the app takes it away. */
    private final class OwnersPrompt implements SecretService.Prompt {
        final Optional<Boolean> dismissedByOwner;
        final CountDownLatch shown = new CountDownLatch(1);
        final CompletableFuture<String> windowId = new CompletableFuture<>();
        final CountDownLatch takenAway = new CountDownLatch(1);

        OwnersPrompt(Optional<Boolean> dismissedByOwner) {
            this.dismissedByOwner = dismissedByOwner;
        }

        @Override
        public void Prompt(String window) {
            windowId.complete(window);
            shown.countDown();
            dismissedByOwner.ifPresent(this::complete);
        }

        @Override
        public void Dismiss() {
            takenAway.countDown();
            complete(true);
        }

        private void complete(boolean dismissed) {
            try {
                desktop.sendMessage(new SecretService.Prompt.Completed(PROMPT, dismissed,
                        new Variant<>(List.of(new DBusPath(COLLECTION)), "ao")));
            } catch (DBusException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public String getObjectPath() {
            return PROMPT;
        }
    }

    @BeforeEach
    void startAnIsolatedSessionBus() throws Exception {
        bus = IsolatedSessionBus.start(folder);
        desktop = connect();
    }

    @AfterEach
    void stopTheBus() {
        made.forEach(DBusConnection::disconnect);
        desktop.disconnect();
        bus.stop();
    }

    private DBusConnection connect() throws DBusException {
        return DBusConnectionBuilder.forAddress(bus.address()).withShared(false).build();
    }

    private SecretServiceKeychain keychain() {
        return new SecretServiceKeychain(() -> {
            var connection = connect();
            made.add(connection);
            return connection;
        }, "car-charge-planner");
    }

    private LockedService serve(OwnersPrompt prompt) throws DBusException {
        var service = new LockedService();
        desktop.exportObject(service);
        desktop.exportObject(prompt);
        desktop.requestBusName(SecretService.BUS_NAME);
        return service;
    }

    @Test
    void aReadOfTheLockedKeyringSaysLocked() throws Exception {
        serve(new OwnersPrompt(Optional.empty()));

        assertThatThrownBy(() -> keychain().read("index")).isInstanceOfSatisfying(KeychainException.class,
                e -> assertThat(e.kind()).isEqualTo(KeychainException.Kind.LOCKED));
    }

    @Test
    void theOwnerUnlocksItAtThePrompt() throws Exception {
        var prompt = new OwnersPrompt(Optional.of(false));
        serve(prompt);
        var keychain = keychain();

        var told = new AtomicInteger();

        assertThat(keychain.unlock(WAIT, told::incrementAndGet)).isEqualTo(UnlockOutcome.UNLOCKED);
        assertThat(prompt.windowId.getNow("not shown")).as("not over a window of the app").isEmpty();
        assertThat(told).as("the owner was told to look for the prompt").hasValue(1);
        keychain.close();
    }

    @Test
    void theOwnerDismissesThePrompt() throws Exception {
        serve(new OwnersPrompt(Optional.of(true)));
        var keychain = keychain();

        assertThat(keychain.unlock(WAIT, () -> { })).isEqualTo(UnlockOutcome.DISMISSED);
        keychain.close();
    }

    @Test
    void aPromptNotAnsweredInTimeIsTakenAway() throws Exception {
        var prompt = new OwnersPrompt(Optional.empty());
        serve(prompt);
        var keychain = keychain();

        assertThat(keychain.unlock(Duration.ofMillis(300), () -> { })).isEqualTo(UnlockOutcome.NOT_ANSWERED);
        assertThat(prompt.takenAway.await(WAIT.toSeconds(), TimeUnit.SECONDS)).as("dismissed by the app").isTrue();
        keychain.close();
    }

    @Test
    void stoppingTheWaitEndsItUnansweredAndTakesThePromptAway() throws Exception {
        var prompt = new OwnersPrompt(Optional.empty());
        serve(prompt);
        var keychain = keychain();
        var outcome = new CompletableFuture<UnlockOutcome>();
        Thread.ofVirtual().start(() -> {
            try {
                outcome.complete(keychain.unlock(Duration.ofMinutes(2), () -> { }));
            } catch (KeychainException | RuntimeException e) {
                outcome.completeExceptionally(e);
            }
        });
        assertThat(prompt.shown.await(WAIT.toSeconds(), TimeUnit.SECONDS)).isTrue();

        keychain.stopUnlocking();

        assertThat(outcome.get(WAIT.toSeconds(), TimeUnit.SECONDS)).isEqualTo(UnlockOutcome.NOT_ANSWERED);
        assertThat(prompt.takenAway.await(WAIT.toSeconds(), TimeUnit.SECONDS)).isTrue();
        keychain.close();
    }

    @Test
    void closingTheKeychainWhileItWaitsEndsTheWaitWithoutConnectingAgain() throws Exception {
        var prompt = new OwnersPrompt(Optional.empty());
        serve(prompt);
        var keychain = keychain();
        var outcome = new CompletableFuture<UnlockOutcome>();
        Thread.ofVirtual().start(() -> {
            try {
                outcome.complete(keychain.unlock(Duration.ofMinutes(2), () -> { }));
            } catch (KeychainException | RuntimeException e) {
                outcome.completeExceptionally(e);
            }
        });
        assertThat(prompt.shown.await(WAIT.toSeconds(), TimeUnit.SECONDS)).isTrue();

        keychain.close();

        assertThat(outcome.get(WAIT.toSeconds(), TimeUnit.SECONDS)).isEqualTo(UnlockOutcome.NOT_ANSWERED);
        assertThat(made).hasSize(1);
        assertThat(made.getFirst().isConnected()).isFalse();
    }

    @Test
    void aKeyringUnlockedWithoutAPromptIsUnlocked() throws Exception {
        var service = serve(new OwnersPrompt(Optional.empty()));
        service.prompt = SecretService.NO_PROMPT;
        service.unlockedAtOnce = List.of(new DBusPath(COLLECTION));
        var told = new AtomicInteger();

        assertThat(keychain().unlock(WAIT, told::incrementAndGet)).isEqualTo(UnlockOutcome.UNLOCKED);
        assertThat(told).as("no prompt to look for").hasValue(0);
    }

    @Test
    void aServiceThatNeitherUnlocksNorPromptsRefuses() throws Exception {
        var service = serve(new OwnersPrompt(Optional.empty()));
        service.prompt = SecretService.NO_PROMPT;

        assertThatThrownBy(() -> keychain().unlock(WAIT, () -> { })).isInstanceOfSatisfying(KeychainException.class,
                e -> assertThat(e.kind()).isEqualTo(KeychainException.Kind.REFUSED));
    }

    @Test
    void withoutADefaultKeyringThereIsNothingToUnlock() throws Exception {
        var service = serve(new OwnersPrompt(Optional.empty()));
        service.alias = SecretService.NO_PROMPT;

        assertThatThrownBy(() -> keychain().unlock(WAIT, () -> { })).isInstanceOfSatisfying(KeychainException.class,
                e -> assertThat(e.kind()).isEqualTo(KeychainException.Kind.REFUSED));
    }

    @Test
    void withoutASecretServiceThereIsNoKeychainToUnlock() {
        assertThatThrownBy(() -> keychain().unlock(WAIT, () -> { })).isInstanceOfSatisfying(KeychainException.class,
                e -> assertThat(e.kind()).isEqualTo(KeychainException.Kind.UNAVAILABLE));
    }
}

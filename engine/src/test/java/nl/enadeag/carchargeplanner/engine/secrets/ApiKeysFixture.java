package nl.enadeag.carchargeplanner.engine.secrets;

import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import nl.enadeag.carchargeplanner.engine.store.FilePrivacy;
import nl.enadeag.carchargeplanner.engine.store.PrivateFiles;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

/** What the tests of {@link ApiKeys} share: the keychains they use, the keys, the file and a clock to move on. */
abstract class ApiKeysFixture {

    static final PrivateFiles FILES = new PrivateFiles(FilePrivacy.NONE);

    static final Clock CLOCK = Clock.systemUTC();

    static final ProviderId OPEN_METEO = new ProviderId("open-meteo");
    static final Secrets KEYS = new Secrets(Map.of(
            OPEN_METEO, Map.of("api_key", "OM-SECRET-1"),
            new ProviderId("forecast-solar"), Map.of("api_key", "FS-SECRET-2")));

    @TempDir
    Path folder;

    final MemoryKeychain keychain = new MemoryKeychain();
    final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-07-03T07:00:00Z"));

    /** Refuses to store anything, as the Credential Manager does over ssh, without a logon session. */
    static final class FailingWrites extends MemoryKeychain {
        @Override
        public void write(String name, String value) throws KeychainException {
            throw new KeychainException(KeychainException.Kind.UNAVAILABLE, "A specified logon session does not exist (1312)");
        }
    }

    /** Says it stored a value and keeps nothing. */
    static final class Forgetful extends MemoryKeychain {
        @Override
        public void write(String name, String value) {
        }
    }

    /** Does not answer, as a Secret Service that hangs. */
    static final class Unreadable extends MemoryKeychain {
        @Override
        public Optional<String> read(String name) throws KeychainException {
            throw new KeychainException(KeychainException.Kind.UNAVAILABLE, "did not answer");
        }
    }

    SecretsFile file() {
        return new SecretsFile(folder.resolve("secrets.json"), FILES, CLOCK);
    }

    ApiKeys keys(Keychain keychain) {
        return keys(keychain, KeyFallback.PRIVATE_FILE);
    }

    ApiKeys keys(Keychain keychain, KeyFallback fallback) {
        return new ApiKeys(keychain, file(), fallback, new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                throw new UnsupportedOperationException("the keys need no time zone");
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        });
    }

    void later(Duration time) {
        now.set(now.get().plus(time));
    }

    /** Counts what it is asked, as the system's keychain takes its time for each call. */
    static class Counting extends MemoryKeychain {
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public Optional<String> read(String name) throws KeychainException {
            calls.incrementAndGet();
            return super.read(name);
        }

        @Override
        public void write(String name, String value) throws KeychainException {
            calls.incrementAndGet();
            super.write(name, value);
        }
    }

    /** A keychain that stops and starts answering, as a Secret Service that hangs for a while. */
    static final class Intermittent extends MemoryKeychain {
        volatile boolean away;

        @Override
        public Optional<String> read(String name) throws KeychainException {
            if (away) {
                throw new KeychainException(KeychainException.Kind.UNAVAILABLE, "did not answer");
            }
            return super.read(name);
        }
    }

    /** A keychain holding the keys, then locked. */
    LockableKeychain lockedWithTheKeys() throws SecretsProblem {
        var lockable = new LockableKeychain();
        keys(lockable).save(KEYS);
        lockable.locked = true;
        return lockable;
    }

    static final ProviderId FORECAST_SOLAR = new ProviderId("forecast-solar");
    static final KeyChanges NEW_OPEN_METEO_KEY =
            KeyChanges.entering(new Secrets(Map.of(OPEN_METEO, Map.of("api_key", "OM-NEW"))));

    /** Keeps each value for one read only, so what it holds as a whole differs from what was written. */
    static final class ForgetsAfterOneRead extends MemoryKeychain {
        @Override
        public Optional<String> read(String name) throws KeychainException {
            return name.equals(KeychainEntries.INDEX) ? super.read(name) : Optional.ofNullable(entries.remove(name));
        }
    }

    /** Answers the first question, about the index, and is locked from then on. */
    static final class LocksAfterTheFirstQuestion extends LockableKeychain {
        @Override
        public Optional<String> read(String name) throws KeychainException {
            var value = super.read(name);
            locked = true;
            return value;
        }
    }

    /** Usable, but refuses its first write as locked, as a keychain that holds none of the app's items yet. */
    static final class LockedForTheFirstItem extends LockableKeychain {
        private boolean refused;

        @Override
        public void write(String name, String value) throws KeychainException {
            if (!refused) {
                refused = true;
                locked = true;
            }
            super.write(name, value);
        }

        @Override
        public Optional<String> read(String name) throws KeychainException {
            return locked && name.equals(KeychainEntries.INDEX) ? Optional.empty() : super.read(name);
        }
    }

    /** Lists a key it does not answer for, as a Secret Service that hangs on one item. */
    static final class EntryThatDoesNotAnswer extends MemoryKeychain {
        @Override
        public Optional<String> read(String name) throws KeychainException {
            if (name.equals("forecast/forecast-solar/api_key")) {
                throw new KeychainException(KeychainException.Kind.UNAVAILABLE, "did not answer");
            }
            return super.read(name);
        }
    }

    static final Secrets WITH_UNKNOWN_PROVIDERS = new Secrets(Map.of(
            OPEN_METEO, Map.of("api_key", "OM-SECRET-1"),
            new ProviderId("leftover-a"), Map.of("some-link", "LEFTOVER-1"),
            new ProviderId("leftover-b"), Map.of("other-key", "LEFTOVER-2")));
    static final Secrets UNKNOWN_PROVIDERS_AFTER_A_CHANGE = new Secrets(Map.of(
            OPEN_METEO, Map.of("api_key", "OM-NEW"),
            new ProviderId("leftover-a"), Map.of("some-link", "LEFTOVER-1"),
            new ProviderId("leftover-b"), Map.of("other-key", "LEFTOVER-2")));

    static final String FORECAST_SOLAR_ENTRY = "forecast/forecast-solar/api_key";
    static final String OPEN_METEO_ENTRY = "forecast/open-meteo/api_key";
    static final ProviderId SOLCAST = new ProviderId("solcast");
    static final KeyChanges NEW_SOLCAST_KEY =
            KeyChanges.entering(new Secrets(Map.of(SOLCAST, Map.of("api_key", "SC-NEW"))));

    /** Fills the keychain with {@link #KEYS}, as a save would. */
    static <K extends MemoryKeychain> K holdingTheKeys(K keychain) {
        keychain.entries.put(KeychainEntries.INDEX, "[\"" + FORECAST_SOLAR_ENTRY + "\",\"" + OPEN_METEO_ENTRY + "\"]");
        keychain.entries.put(FORECAST_SOLAR_ENTRY, "FS-SECRET-2");
        keychain.entries.put(OPEN_METEO_ENTRY, "OM-SECRET-1");
        return keychain;
    }

    /** A keychain holding {@link #KEYS}, of which it cannot read Forecast.Solar's. */
    static UnreadableEntryKeychain withForecastSolarUnreadable() {
        return holdingTheKeys(new UnreadableEntryKeychain(FORECAST_SOLAR_ENTRY));
    }

    static String[] index(MemoryKeychain keychain) {
        return new JsonMapper().readValue(keychain.entries.get(KeychainEntries.INDEX), String[].class);
    }

    /** Answers the index the first time, as a usable keychain, and refuses it from then on. */
    static final class RefusesTheIndexAfterTheFirstRead extends MemoryKeychain {
        private final AtomicInteger indexReads = new AtomicInteger();

        @Override
        public Optional<String> read(String name) throws KeychainException {
            if (name.equals(KeychainEntries.INDEX) && indexReads.incrementAndGet() > 1) {
                throw new KeychainException(KeychainException.Kind.REFUSED, "the index cannot be decrypted");
            }
            return super.read(name);
        }
    }

    /** Has an entry it cannot read, and can be locked; the owner answers the prompt as the test says. */
    static class LockableWithAnUnreadableEntry extends UnreadableEntryKeychain {
        volatile boolean locked;
        /** An entry that is locked even while the rest is not, as an item of a locked collection. */
        volatile String lockedEntry;
        volatile UnlockOutcome answer = UnlockOutcome.NOT_ANSWERED;
        final AtomicInteger prompts = new AtomicInteger();

        LockableWithAnUnreadableEntry() {
            super(FORECAST_SOLAR_ENTRY);
        }

        @Override
        public Optional<String> read(String name) throws KeychainException {
            if (locked || name.equals(lockedEntry)) {
                throw new KeychainException(KeychainException.Kind.LOCKED, "the keychain is locked");
            }
            return super.read(name);
        }

        @Override
        public void write(String name, String value) throws KeychainException {
            if (locked) {
                throw new KeychainException(KeychainException.Kind.LOCKED, "the keychain is locked");
            }
            super.write(name, value);
        }

        @Override
        public UnlockOutcome unlock(Duration wait, Runnable prompting) {
            prompts.incrementAndGet();
            return answer;
        }
    }

    /** Has an entry it cannot read, and refuses to store the named entries. */
    static final class RefusesWrites extends UnreadableEntryKeychain {
        private final Set<String> refused;

        RefusesWrites(Set<String> refused) {
            super(FORECAST_SOLAR_ENTRY);
            this.refused = refused;
        }

        @Override
        public void write(String name, String value) throws KeychainException {
            if (refused.contains(name)) {
                throw new KeychainException(KeychainException.Kind.REFUSED, "not kept");
            }
            super.write(name, value);
        }
    }

    /** Refuses to store anything, as a store that answers but cannot encrypt. */
    static final class RefusingWrites extends MemoryKeychain {
        @Override
        public void write(String name, String value) throws KeychainException {
            throw new KeychainException(KeychainException.Kind.REFUSED, "the value cannot be encrypted");
        }
    }
}

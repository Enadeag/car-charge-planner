package nl.enadeag.carchargeplanner.engine.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * What every {@link Keychain} must do, as its Javadoc lists it: a platform's store passes when its test extends this
 * class and hands it a fresh, usable store per test. It checks only what holds for every store, on a store that works;
 * the failure paths (no store, one that does not answer, a refusal) and locking and unlocking, which only some stores
 * have, are each platform's own tests. A subclass whose store keeps its entries after the test, such as one in the
 * system's own keychain, removes the {@link #NAMES} again.
 */
public abstract class KeychainContractTest {

    /** The name the tests mostly use, as the engine names an API key. */
    protected static final String KEY = "forecast/open-meteo/api_key";

    private static final int THREADS = 8;

    /** The names that differ from {@link #KEY} only by an ending, or by a {@code -} for a {@code _}. */
    private static final List<String> NEAR_KEY = List.of("forecast/open-meteo/api_key2", "forecast/open-meteo/api",
            "forecast/open_meteo/api_key", "forecast/open-meteo/api-key");

    /** Every name a test here writes. */
    protected static final List<String> NAMES = Stream.of(
            Stream.of(KEY, KeychainEntries.INDEX, "forecast/forecast-solar/api_key", "forecast/my_provider-2/api_key"),
            NEAR_KEY.stream(),
            IntStream.range(0, THREADS).mapToObj(KeychainContractTest::threadName))
            .flatMap(names -> names).toList();

    /**
     * The store under test, the same one for each call within a test, empty at the start of it; the subclass closes it
     * after the test when it holds anything.
     *
     * @return the store
     * @throws Exception when it could not be made, such as a system service that did not start
     */
    protected abstract Keychain keychain() throws Exception;

    /** 2048 bytes in UTF-8, below the Credential Manager's limit of 2560 bytes: letters, digits and {@code /_-}. */
    private static String longValue() {
        var characters = "abcdefghijklmnopqrstuvwxyz0123456789/_-ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        return IntStream.range(0, 2048).mapToObj(i -> String.valueOf(characters.charAt(i % characters.length())))
                .collect(Collectors.joining());
    }

    /**
     * The values every store must give back exactly: a usual key, empty and blank texts, non-ASCII, quotes and
     * backslashes, every kind of line end, the index's own JSON and a long value.
     *
     * @return the values, one per test
     */
    static Stream<String> values() {
        return Stream.of("OM-SECRET-1", "", " ", "Zürich € ëß 日本 🔑", "\"quoted\" 'single' \\back\\slash",
                "one\ntwo\r\nthree\rfour\n", "\ttab and trailing space ", "[\"forecast/open-meteo/api_key\"]",
                longValue());
    }

    @ParameterizedTest
    @MethodSource("values")
    void everyValueIsReadBackExactlyAsItWasWritten(String value) throws Exception {
        keychain().write(KEY, value);

        assertThat(keychain().read(KEY)).contains(value);
    }

    @Test
    void aNameNeverWrittenIsNoEntry() throws Exception {
        assertThat(keychain().read(KEY)).isEmpty();
    }

    @Test
    void aWriteReplacesTheValue() throws Exception {
        keychain().write(KEY, "FIRST-KEY");
        keychain().write(KEY, "SECOND-KEY");

        assertThat(keychain().read(KEY)).contains("SECOND-KEY");
    }

    @Test
    void aDeletedEntryIsGoneAndDeletingItAgainDoesNothing() throws Exception {
        keychain().write(KEY, "OM-SECRET-1");

        keychain().delete(KEY);
        keychain().delete(KEY);

        assertThat(keychain().read(KEY)).isEmpty();
    }

    @Test
    void deletingANameNeverWrittenDoesNothing() throws Exception {
        keychain().delete(KEY);

        assertThat(keychain().read(KEY)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"index", "forecast/forecast-solar/api_key", "forecast/my_provider-2/api_key"})
    void everyNameTheEngineUsesKeepsItsOwnEntry(String name) throws Exception {
        keychain().write(KEY, "OM-SECRET-1");
        keychain().write(name, "kept apart");

        assertThat(keychain().read(name)).contains("kept apart");
        keychain().delete(KEY);
        assertThat(keychain().read(name)).contains("kept apart");
        keychain().delete(name);
        assertThat(keychain().read(name)).isEmpty();
    }

    @Test
    void namesThatDifferOnlyByAnEndingOrByADashOrUnderscoreAreEntriesOfTheirOwn() throws Exception {
        keychain().write(KEY, "the key");
        for (var name : NEAR_KEY) {
            keychain().write(name, "near " + name);
        }

        assertThat(keychain().read(KEY)).contains("the key");
        for (var name : NEAR_KEY) {
            assertThat(keychain().read(name)).as(name).contains("near " + name);
        }
        keychain().delete(KEY);
        assertThat(keychain().read(KEY)).isEmpty();
        for (var name : NEAR_KEY) {
            assertThat(keychain().read(name)).as(name).contains("near " + name);
        }
    }

    @Test
    void severalThreadsCanUseItAtOnce() throws Exception {
        var store = keychain();
        ExecutorService threads = Executors.newFixedThreadPool(THREADS);
        try {
            var done = new ArrayList<Future<Optional<String>>>();
            for (int i = 0; i < THREADS; i++) {
                var name = threadName(i);
                var value = "value-" + i;
                done.add(threads.submit((Callable<Optional<String>>) () -> {
                    store.write(name, "first");
                    store.write(name, value);
                    var read = store.read(name);
                    store.read(KEY);
                    return read;
                }));
            }
            for (int i = 0; i < THREADS; i++) {
                assertThat(done.get(i).get(30, TimeUnit.SECONDS)).contains("value-" + i);
            }
        } finally {
            threads.shutdownNow();
        }
    }

    private static String threadName(int thread) {
        return "forecast/thread-" + thread;
    }

    @Test
    void threadsWritingTheSameNameLeaveOneOfTheirValuesWholeNeverAMix() throws Exception {
        var store = keychain();
        var written = IntStream.range(0, THREADS).mapToObj(i -> longValue().replace('a', (char) ('A' + i))).toList();
        ExecutorService threads = Executors.newFixedThreadPool(THREADS);
        try {
            var done = new ArrayList<Future<?>>();
            for (var value : written) {
                done.add(threads.submit((Callable<Void>) () -> {
                    store.write(KEY, value);
                    return null;
                }));
            }
            for (var each : done) {
                each.get(30, TimeUnit.SECONDS);
            }
        } finally {
            threads.shutdownNow();
        }

        assertThat(store.read(KEY)).hasValueSatisfying(value -> assertThat(value).isIn(written));
    }

    @Test
    void onAnInterruptedThreadACallWorksOrThrowsAKeychainExceptionAndTheThreadStaysInterrupted() throws Exception {
        var store = keychain();
        store.write(KEY, "before");

        Thread.currentThread().interrupt();
        var writing = catchThrowable(() -> store.write(KEY, "after"));
        var reading = catchThrowable(() -> store.read(KEY));
        var stillInterrupted = Thread.interrupted();

        assertThat(stillInterrupted).as("the thread is still interrupted").isTrue();
        assertThat(writing).as("the write").satisfiesAnyOf(thrown -> assertThat(thrown).isNull(),
                thrown -> assertThat(thrown).isInstanceOf(KeychainException.class));
        assertThat(reading).as("the read").satisfiesAnyOf(thrown -> assertThat(thrown).isNull(),
                thrown -> assertThat(thrown).isInstanceOf(KeychainException.class));
        assertThat(store.read(KEY)).as("usable again").hasValueSatisfying(
                value -> assertThat(value).isIn("before", "after"));
    }

    @Test
    void closingTwiceIsAllowedAndACallAfterItWorksAsBefore() throws Exception {
        var store = keychain();
        store.write(KEY, "OM-SECRET-1");

        store.close();
        store.close();

        assertThat(store.read(KEY)).contains("OM-SECRET-1");
        store.write(KEY, "OM-SECRET-2");
        assertThat(store.read(KEY)).contains("OM-SECRET-2");
        store.close();
    }
}

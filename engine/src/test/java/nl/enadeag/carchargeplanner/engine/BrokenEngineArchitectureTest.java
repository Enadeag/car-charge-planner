package nl.enadeag.carchargeplanner.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastThatKnowsAProvider;
import nl.enadeag.carchargeplanner.engine.forecast.HttpGet;
import nl.enadeag.carchargeplanner.engine.forecast.providers.OpenMeteo;
import nl.enadeag.carchargeplanner.engine.secrets.Keychain;
import nl.enadeag.carchargeplanner.engine.secrets.NoKeychain;
import java.io.Console;
import java.io.InputStream;
import java.net.ProxySelector;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.Provider;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.junit.jupiter.api.Test;

/** Classes that break {@link EngineArchitectureTest}'s rules on purpose, which shows that the rules see them. */
class BrokenEngineArchitectureTest {

    /** Static state in fields of an interface type, beside constants that are not state. */
    static final class StaticStateBehindAnInterface {

        /** A list that changes, typed as the interface. */
        static final List<String> SEEN = new ArrayList<>();

        /** A constant that cannot change. */
        static final List<String> NAMES = List.of("Open-Meteo", "Forecast.Solar");

        /** A set that changes, made by a call rather than a constructor. */
        static final Set<String> ASKED = Collections.synchronizedSet(new java.util.HashSet<>());

        /** A constant set after a map that changes was made for it. */
        static final Map<String, Integer> SIZES;

        /** A map that changes, set in a static block. */
        static final Map<String, Integer> COUNTED;

        static {
            var sizes = new HashMap<String, Integer>();
            sizes.put("small", 1);
            SIZES = Map.copyOf(sizes);
            COUNTED = new HashMap<>();
        }

        private StaticStateBehindAnInterface() {
        }
    }

    /** A network of its own: it trusts every certificate, sends everything through a proxy and connects itself. */
    static final class NetworkOfItsOwn implements X509TrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }

        /** Sets it all up; never run. */
        Callable<InputStream> setUp(URL address, Provider provider) throws GeneralSecurityException {
            SSLContext.getInstance("TLS").init(null, new TrustManager[] {this}, null);
            Security.addProvider(provider);
            Security.insertProviderAt(provider, 1);
            Security.removeProvider(provider.getName());
            Security.setProperty("jdk.tls.disabledAlgorithms", "");
            ProxySelector.setDefault(null);
            System.setProperty("https.proxyHost", "proxy.example");
            System.setProperties(null);
            System.clearProperty("https.proxyHost");
            System.getProperties().setProperty("https.proxyHost", "proxy.example");
            return address::openStream;
        }
    }

    /**
     * A log of its own: it sets up java.util.logging, formats its records and writes to the console, also through
     * method references, which are accesses like a call.
     */
    static final class LogOfItsOwn extends Formatter {

        @Override
        public String format(LogRecord record) {
            return record.getMessage();
        }

        /** Tells of a failure; never run. */
        List<Object> failed(Exception failure) {
            Logger.getLogger("engine").addHandler(new ConsoleHandler());
            System.out.println("failed");
            System.err.println(failure.getMessage());
            failure.printStackTrace();
            failure.printStackTrace(System.err);
            Consumer<Throwable> trace = Throwable::printStackTrace;
            Supplier<Console> console = System::console;
            return List.of(trace, console);
        }
    }

    /**
     * Reads the system itself: its locale, a property and the environment. Through method references, as a call of
     * System.getProperty or getenv would break the tests' own rule, which looks for calls only; to this class's rules
     * a reference is the same access.
     */
    static final class SystemOfItsOwn {

        /** Reads it all; never run. */
        List<Object> read() {
            Locale.setDefault(Locale.ROOT);
            Function<String, String> property = System::getProperty;
            Function<String, String> variable = System::getenv;
            Supplier<Map<String, String>> environment = System::getenv;
            return List.of(Locale.getDefault(), Locale.getDefault(Locale.Category.FORMAT), property, variable,
                    environment);
        }
    }

    /**
     * Writes texts, numbers and times in the JVM's default locale, which is whatever the system's is, also through
     * method and constructor references.
     */
    static final class TextsInTheDefaultLocale {

        /** Writes them; never run. */
        List<Object> write(double kwh) {
            Function<String, String> lower = String::toLowerCase;
            Function<String, DecimalFormat> format = DecimalFormat::new;
            return List.of(String.format("%.1f kWh", kwh), "%.1f".formatted(kwh), "kwh".toUpperCase(),
                    NumberFormat.getInstance(), DateTimeFormatter.ofPattern("HH:mm"), new SimpleDateFormat("HH:mm"),
                    new DecimalFormat("0.0"), lower, format);
        }
    }

    /** Reads the system's clock instead of the one it is handed. */
    static final class ClockOfItsOwn {

        /** Reads it; never run. */
        List<Object> now() {
            Supplier<Clock> clock = Clock::systemUTC;
            LongSupplier timer = System::nanoTime;
            return List.of(Instant.now(), clock, System.nanoTime(), timer);
        }
    }

    /**
     * Threads of its own, made and asked for, and a sleep. The sleep is a method reference, as a call would break the
     * tests' own rule, which looks for calls only; to this class's rules a reference is the same access.
     */
    static final class ThreadsOfItsOwn {

        /** How Thread.sleep is referred to, as it throws. */
        interface Pause {

            /** Waits; never run. */
            void sleep(long millis) throws InterruptedException;
        }

        /** Makes them; never run. */
        List<Object> start() {
            Function<Runnable, Thread> thread = Thread::new;
            Supplier<ExecutorService> executor = Executors::newSingleThreadExecutor;
            Pause pause = Thread::sleep;
            return List.of(new Thread(() -> { }), Executors.newSingleThreadExecutor(), thread, executor, pause);
        }
    }

    /** A keychain of the engine's own, which each platform implements instead. */
    static final class KeychainOfItsOwn implements Keychain {

        @Override
        public Optional<String> read(String name) {
            return Optional.empty();
        }

        @Override
        public void write(String name, String value) {
        }

        @Override
        public void delete(String name) {
        }
    }

    /** A window of its own, which the engine cannot have. */
    static final class WindowOfItsOwn {

        /** Its size; never run. */
        java.awt.Dimension size() {
            return new java.awt.Dimension(800, 600);
        }

        /** A label in it; never run. */
        javax.swing.JComponent label() {
            return new javax.swing.JLabel("kWh");
        }
    }

    /** A part of the engine outside engine.forecast that makes a provider itself. */
    static final class PartThatKnowsAProvider {

        /** Makes the provider; never run. */
        Object provider(HttpGet http) {
            return new OpenMeteo(http);
        }
    }

    @Test
    void aLogOfItsOwnBreaksTheLoggingConsoleAndStackTraceRules() {
        var log = new ClassFileImporter().importClasses(LogOfItsOwn.class);

        assertThatThrownBy(() -> EngineArchitectureTest.theEngineUsesNoJavaUtilLogging.check(log))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("extends class <java.util.logging.Formatter>")
                .hasMessageContaining("Logger.getLogger(")
                .hasMessageContaining("ConsoleHandler.<init>(");
        assertThatThrownBy(() -> EngineArchitectureTest.theEngineWritesNothingToTheConsole.check(log))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("System.out")
                .hasMessageContaining("System.err")
                .hasMessageContaining("references method <java.lang.System.console()>");
        assertThatThrownBy(() -> EngineArchitectureTest.theEnginePrintsNoStackTrace.check(log))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Exception.printStackTrace()")
                .hasMessageContaining("Exception.printStackTrace(java.io.PrintStream)")
                .hasMessageContaining("references method <java.lang.Throwable.printStackTrace()>");
    }

    @Test
    void aClassThatReadsTheSystemBreaksTheLocaleAndSystemRules() {
        var system = new ClassFileImporter().importClasses(SystemOfItsOwn.class);

        assertThatThrownBy(() -> EngineArchitectureTest.theEngineLeavesTheSystemLocaleAlone.check(system))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Locale.setDefault(java.util.Locale)")
                .hasMessageContaining("Locale.getDefault()")
                .hasMessageContaining("Locale.getDefault(java.util.Locale$Category)");
        assertThatThrownBy(() -> EngineArchitectureTest.theEngineReadsNoSystemPropertyOrEnvironment.check(system))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("System.getProperty(java.lang.String)")
                .hasMessageContaining("System.getenv(java.lang.String)")
                .hasMessageContaining("System.getenv()");
    }

    @Test
    void textsInTheDefaultLocaleBreakTheRule() {
        var texts = new ClassFileImporter().importClasses(TextsInTheDefaultLocale.class);

        assertThatThrownBy(() -> EngineArchitectureTest.theEnginesTextsNumbersAndTimesNameTheirLocale.check(texts))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("String.format(")
                .hasMessageContaining("String.formatted(")
                .hasMessageContaining("String.toUpperCase()")
                .hasMessageContaining("NumberFormat.getInstance()")
                .hasMessageContaining("DateTimeFormatter.ofPattern(java.lang.String)")
                .hasMessageContaining("SimpleDateFormat.<init>(java.lang.String)")
                .hasMessageContaining("DecimalFormat.<init>(java.lang.String)")
                .hasMessageContaining("references method <java.lang.String.toLowerCase()>")
                .hasMessageContaining("references constructor <java.text.DecimalFormat.<init>(java.lang.String)>");
    }

    @Test
    void aClockOfItsOwnBreaksTheRule() {
        var clock = new ClassFileImporter().importClasses(ClockOfItsOwn.class);

        assertThatThrownBy(() -> EngineArchitectureTest.theEngineReadsNoClock.check(clock))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Instant.now()")
                .hasMessageContaining("Clock.systemUTC()")
                .hasMessageContaining("calls method <java.lang.System.nanoTime()>")
                .hasMessageContaining("references method <java.lang.System.nanoTime()>");
    }

    @Test
    void threadsOfItsOwnBreakTheThreadAndSleepRules() {
        var threads = new ClassFileImporter().importClasses(ThreadsOfItsOwn.class);

        assertThatThrownBy(() -> EngineArchitectureTest.theEngineOwnsNoThreads.check(threads))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("calls constructor <java.lang.Thread.<init>(java.lang.Runnable)>")
                .hasMessageContaining("calls method <java.util.concurrent.Executors.newSingleThreadExecutor()>")
                .hasMessageContaining("references constructor <java.lang.Thread.<init>(java.lang.Runnable)>")
                .hasMessageContaining("references method <java.util.concurrent.Executors.newSingleThreadExecutor()>");
        assertThatThrownBy(() -> EngineArchitectureTest.theEngineDoesNotSleep.check(threads))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("references method <java.lang.Thread.sleep(long)>");
    }

    @Test
    void aKeychainOfItsOwnBreaksThePortsRuleWhereNoKeychainDoesNot() {
        var keychains = new ClassFileImporter().importClasses(KeychainOfItsOwn.class, NoKeychain.class);

        assertThatThrownBy(() -> EngineArchitectureTest.theEnginesOnlyKeychainIsNoKeychain.check(keychains))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("was violated (1 times)")
                .hasMessageContaining("Class <" + KeychainOfItsOwn.class.getName() + "> does implement");
    }

    @Test
    void aWindowOfItsOwnBreaksTheRule() {
        var window = new ClassFileImporter().importClasses(WindowOfItsOwn.class);

        assertThatThrownBy(() -> EngineArchitectureTest.theEngineHasNoUserInterface.check(window))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("java.awt.Dimension")
                .hasMessageContaining("javax.swing.JLabel");
    }

    @Test
    void aPartOutsideTheForecastThatKnowsAProviderBreaksTheRule() {
        var part = new ClassFileImporter().importClasses(PartThatKnowsAProvider.class);

        assertThatThrownBy(() -> EngineArchitectureTest.theEngineReachesTheProvidersOnlyThroughTheForecast.check(part))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("PartThatKnowsAProvider")
                .hasMessageContaining("OpenMeteo");
    }

    @Test
    void aClassWithANetworkOfItsOwnBreaksTheNetworkAndTlsRules() {
        var network = new ClassFileImporter().importClasses(NetworkOfItsOwn.class);

        assertThatThrownBy(() -> EngineArchitectureTest.theEngineOpensNoConnectionOfItsOwn.check(network))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("URL.openStream()");
        assertThatThrownBy(() -> EngineArchitectureTest.theEngineLeavesTheChecksOfTlsAlone.check(network))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("X509TrustManager")
                .hasMessageContaining("SSLContext.init(")
                .hasMessageContaining("Security.addProvider(")
                .hasMessageContaining("Security.insertProviderAt(")
                .hasMessageContaining("Security.removeProvider(")
                .hasMessageContaining("Security.setProperty(");
        assertThatThrownBy(() -> EngineArchitectureTest.theEngineLeavesTheNetworkOfTheProcessAlone.check(network))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("ProxySelector.setDefault(")
                .hasMessageContaining("System.setProperty(")
                .hasMessageContaining("System.setProperties(")
                .hasMessageContaining("System.clearProperty(")
                .hasMessageContaining("System.getProperties(");
    }

    @Test
    void aForecastClassBesideProvidersThatKnowsAProviderBreaksTheRule() {
        var forecast = new ClassFileImporter().importClasses(ForecastThatKnowsAProvider.class);

        assertThatThrownBy(() -> EngineArchitectureTest.onlyProvidersKnowsTheProvidersClasses.check(forecast))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("ForecastThatKnowsAProvider")
                .hasMessageContaining("OpenMeteo");
    }

    @Test
    void aStaticListOrMapThatChangesBreaksTheRuleThoughItsTypeIsAnInterface() {
        var state = new ClassFileImporter().importClasses(StaticStateBehindAnInterface.class);

        assertThatThrownBy(() -> EngineArchitectureTest.theEngineHasNoStaticMutableObjects.check(state))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("StaticStateBehindAnInterface.SEEN")
                .hasMessageContaining("StaticStateBehindAnInterface.ASKED")
                .hasMessageContaining("StaticStateBehindAnInterface.COUNTED")
                .satisfies(failure -> assertThat(failure.getMessage())
                        .doesNotContain("StaticStateBehindAnInterface.NAMES")
                        .doesNotContain("StaticStateBehindAnInterface.SIZES>"));
    }
}

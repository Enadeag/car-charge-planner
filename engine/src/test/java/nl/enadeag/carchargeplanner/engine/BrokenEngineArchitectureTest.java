package nl.enadeag.carchargeplanner.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastThatKnowsAProvider;
import java.io.InputStream;
import java.net.ProxySelector;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.Provider;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
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

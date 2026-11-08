package nl.enadeag.carchargeplanner;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.io.InputStream;
import java.net.ProxySelector;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.Provider;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.concurrent.Callable;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.junit.jupiter.api.Test;

/** Classes that break {@link ArchitectureTest}'s rules on purpose, which shows that the rules see them. */
class BrokenArchitectureTest {

    /** A network of its own beside the HttpGet: it trusts every certificate and sends everything through a proxy. */
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
    void aClassWithANetworkOfItsOwnBreaksTheTlsAndNetworkRules() {
        var network = new ClassFileImporter().importClasses(NetworkOfItsOwn.class);

        assertThatThrownBy(() -> ArchitectureTest.onlyTheHttpGetMayTouchTheChecksOfTls.check(network))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("X509TrustManager")
                .hasMessageContaining("SSLContext.init(")
                .hasMessageContaining("Security.addProvider(")
                .hasMessageContaining("Security.insertProviderAt(")
                .hasMessageContaining("Security.removeProvider(")
                .hasMessageContaining("Security.setProperty(");
        assertThatThrownBy(() -> ArchitectureTest.onlyTheHttpGetMayChangeTheNetworkOfTheProcess.check(network))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("ProxySelector.setDefault(")
                .hasMessageContaining("System.setProperty(")
                .hasMessageContaining("System.setProperties(")
                .hasMessageContaining("System.clearProperty(")
                .hasMessageContaining("System.getProperties(");
    }
}

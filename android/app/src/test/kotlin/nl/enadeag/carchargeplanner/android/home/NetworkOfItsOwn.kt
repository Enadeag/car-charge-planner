package nl.enadeag.carchargeplanner.android.home

import java.io.InputStream
import java.net.ProxySelector
import java.net.URL
import java.security.Provider
import java.security.Security
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/** Logic that breaks `AppArchitectureTest`'s rules on the network and TLS on purpose; it is never run. */
internal class NetworkOfItsOwn : X509TrustManager {

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

    /** Trusts every certificate, sends everything through a proxy and opens the address itself. */
    fun setUp(address: URL, provider: Provider): InputStream {
        SSLContext.getInstance("TLS").init(null, arrayOf(this), null)
        Security.addProvider(provider)
        Security.insertProviderAt(provider, 1)
        Security.removeProvider(provider.name)
        Security.setProperty("jdk.tls.disabledAlgorithms", "")
        ProxySelector.setDefault(null)
        System.setProperty("https.proxyHost", "proxy.example")
        System.setProperties(null)
        System.clearProperty("https.proxyHost")
        System.getProperties().setProperty("https.proxyHost", "proxy.example")
        return address.openStream()
    }
}

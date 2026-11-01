package nl.enadeag.carchargeplanner.android

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaAccess
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import nl.enadeag.carchargeplanner.android.logging.LogcatServiceProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The rules for the app's own classes, Java and Kotlin alike (its main code, not its tests), as
 * `EngineArchitectureTest` has them for the engine (ADR 0010): one class opens network connections, [AndroidHttpGet],
 * which keeps the engine's contract on timeouts, size and redirects; no class weakens the checks of TLS; and the app
 * logs through slf4j, whose provider is the one class that writes to Logcat.
 */
class AppArchitectureTest {

    private companion object {

        const val APP = "nl.enadeag.carchargeplanner.android"

        val HTTP_GET: String = AndroidHttpGet::class.java.name

        const val LOGCAT_SINK = "$APP.logging.LogcatSink"

        // AGP puts each variant's local tests in folders named after it, such as debugUnitTest, which ArchUnit's own
        // DoNotIncludeTests does not know
        val MAIN_CODE_ONLY = ImportOption { location -> !location.contains("UnitTest/") }

        val APP_CLASSES: JavaClasses by lazy {
            ClassFileImporter().withImportOption(MAIN_CODE_ONLY).importPackages(APP)
        }

        val NETWORK_TYPES = setOf(
            "java.net.URLConnection", "java.net.HttpURLConnection", "java.net.JarURLConnection",
            "javax.net.ssl.HttpsURLConnection",
            "java.net.Socket", "java.net.ServerSocket", "java.net.DatagramSocket", "java.net.MulticastSocket",
            "javax.net.SocketFactory", "javax.net.ssl.SSLSocketFactory", "javax.net.ssl.SSLSocket",
            "java.nio.channels.SocketChannel", "java.nio.channels.ServerSocketChannel",
            "java.nio.channels.DatagramChannel", "java.nio.channels.AsynchronousSocketChannel",
            "java.nio.channels.AsynchronousServerSocketChannel",
            "android.app.DownloadManager", "android.net.SSLCertificateSocketFactory",
        )

        val NETWORK_PACKAGES = arrayOf(
            "java.net.http..", "okhttp3..", "com.squareup.okhttp..", "android.webkit..", "android.net.http..",
            "org.apache.http..", "org.chromium.net..", "io.ktor.client..", "retrofit2..",
        )

        val OPENS_A_CONNECTION = DescribedPredicate.describe<JavaClass>(
            "open network connections (URLConnection, sockets, socket channels, DownloadManager)") { type ->
            type.name in NETWORK_TYPES
        }

        // an access, not only a call, so a method reference such as url::openStream is caught as well
        val CONNECT_THROUGH_A_URL_OR_NETWORK = DescribedPredicate.describe<JavaAccess<*>>(
            "connect through URL.openConnection/openStream/getContent, Kotlin's URL.readText/readBytes or "
                + "android.net.Network") { access ->
            val owner = access.targetOwner.name
            owner == "java.net.URL" && access.name in setOf("openConnection", "openStream", "getContent")
                || owner == "kotlin.io.TextStreamsKt" && access.name in setOf("readText", "readBytes")
                || owner == "android.net.Network"
                && access.name in setOf("openConnection", "getSocketFactory", "bindSocket")
        }

        val TLS_CHECKS = setOf(
            "javax.net.ssl.TrustManager", "javax.net.ssl.X509TrustManager", "javax.net.ssl.X509ExtendedTrustManager",
            "javax.net.ssl.TrustManagerFactory", "javax.net.ssl.HostnameVerifier",
            "android.net.SSLCertificateSocketFactory", "android.net.http.X509TrustManagerExtensions",
        )

        val CHECK_TLS = DescribedPredicate.describe<JavaClass>(
            "check certificates or host names (trust managers, host name verifiers)") { type ->
            type.name in TLS_CHECKS
        }

        val TAMPER_WITH_TLS = DescribedPredicate.describe<JavaAccess<*>>(
            "set up TLS of their own (SSLContext.init or setDefault, an HttpsURLConnection's socket factory or "
                + "host name verifier)") { access ->
            val owner = access.targetOwner.name
            owner == "javax.net.ssl.SSLContext" && access.name in setOf("init", "setDefault")
                || owner == "javax.net.ssl.HttpsURLConnection" && access.name in setOf("setSSLSocketFactory",
                "setDefaultSSLSocketFactory", "setHostnameVerifier", "setDefaultHostnameVerifier")
        }

        val THE_HTTP_GET = DescribedPredicate.describe<JavaClass>("are the app's HttpGet or nested in it") { type ->
            type.name == HTTP_GET || type.name.startsWith("$HTTP_GET$")
        }
    }

    @Test
    fun theRulesSeeTheAppsJavaAndKotlinClassesAndNotItsTests() {
        assertThat(APP_CLASSES.contain(AndroidHttpGet::class.java)).`as`("the HttpGet, in Java").isTrue()
        assertThat(APP_CLASSES.contain(LOGCAT_SINK)).`as`("the Logcat sink, in Java").isTrue()
        assertThat(APP_CLASSES.contain(LogcatServiceProvider::class.java)).isTrue()
        assertThat(APP_CLASSES.contain(MainActivity::class.java)).`as`("the activity, in Kotlin").isTrue()
        assertThat(APP_CLASSES.contain(AppArchitectureTest::class.java)).`as`("this test").isFalse()
    }

    @Test
    fun onlyTheHttpGetOpensNetworkConnections() {
        noClasses().that(DescribedPredicate.not(THE_HTTP_GET))
            .should().dependOnClassesThat(OPENS_A_CONNECTION)
            .orShould().dependOnClassesThat().resideInAnyPackage(*NETWORK_PACKAGES)
            .orShould().accessTargetWhere(CONNECT_THROUGH_A_URL_OR_NETWORK)
            .because("every request goes through the HttpGet the app hands the engine, which keeps its contract on "
                + "redirects, timeouts and size (ADR 0010)")
            .check(APP_CLASSES)
    }

    @Test
    fun noClassChangesHowTlsIsChecked() {
        noClasses()
            .should().dependOnClassesThat(CHECK_TLS)
            .orShould().accessTargetWhere(TAMPER_WITH_TLS)
            .because("the app trusts the certificates and checks the host names as Android does; its own trust "
                + "manager or host name verifier is how an app lets a forged certificate through")
            .check(APP_CLASSES)
    }

    @Test
    fun theAppLogsThroughSlf4jAndOnlyItsSinkWritesToLogcat() {
        noClasses().that().doNotHaveFullyQualifiedName(LOGCAT_SINK)
            .should().dependOnClassesThat().haveFullyQualifiedName("android.util.Log")
            .orShould().dependOnClassesThat().resideInAPackage("java.util.logging..")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("java.lang.System\$Logger")
            .because("the app logs through slf4j, as the engine does, so its records and the engine's go to Logcat "
                + "one way, at the build's level")
            .check(APP_CLASSES)
    }
}

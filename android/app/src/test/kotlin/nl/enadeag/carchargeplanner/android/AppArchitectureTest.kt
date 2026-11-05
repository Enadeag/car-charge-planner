package nl.enadeag.carchargeplanner.android

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.AccessTarget
import com.tngtech.archunit.core.domain.JavaAccess
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.domain.JavaMethodCall
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import nl.enadeag.carchargeplanner.android.logging.LogcatServiceProvider
import nl.enadeag.carchargeplanner.android.store.EngineAccess
import nl.enadeag.carchargeplanner.android.ui.ScreenThatPlansARound
import nl.enadeag.carchargeplanner.android.ui.ScreenThatReadsTheSettingsFile
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * The rules for the app's own classes, Java and Kotlin alike (its main code, not its tests), as
 * `EngineArchitectureTest` has them for the engine (ADR 0010): one class opens network connections, [AndroidHttpGet],
 * which keeps the engine's contract on timeouts, size and redirects; no class weakens the checks of TLS; the app
 * logs through slf4j, whose provider is the one class that writes to Logcat; the screens reach the engine only
 * through the suspend interface that runs it off the main thread; and they leave the planning round, and when the next
 * notice is due, to the work in the background.
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

        val CHANGE_THE_NETWORK_OF_THE_PROCESS = DescribedPredicate.describe<JavaAccess<*>>(
            "change how every connection of the process works (the default proxy selector, authenticator, cookie "
                + "handler or response cache, URL's stream handlers, URLConnection's content handlers, following "
                + "redirects for every HttpURLConnection)") { access ->
            val owner = access.targetOwner.name
            owner in setOf("java.net.ProxySelector", "java.net.Authenticator", "java.net.CookieHandler",
                "java.net.ResponseCache") && access.name == "setDefault"
                || owner == "java.net.URL" && access.name == "setURLStreamHandlerFactory"
                || owner == "java.net.URLConnection" && access.name == "setContentHandlerFactory"
                || owner == "java.net.HttpURLConnection" && access.name == "setFollowRedirects"
        }

        val WRITE_PAST_SLF4J = DescribedPredicate.describe<JavaAccess<*>>(
            "write to the console (System.out, System.err, Kotlin's print and println) or print a stack trace there "
                + "(printStackTrace())") { access ->
            val owner = access.targetOwner.name
            owner == "java.lang.System" && access.name in setOf("out", "err")
                || owner == "kotlin.io.ConsoleKt" && access.name in setOf("print", "println")
                || access.name == "printStackTrace"
                && (access.target as? AccessTarget.CodeUnitAccessTarget)?.rawParameterTypes?.isEmpty() == true
        }

        val THE_HTTP_GET = DescribedPredicate.describe<JavaClass>("are the app's HttpGet or nested in it") { type ->
            type.name == HTTP_GET || type.name.startsWith("$HTTP_GET$")
        }

        val SCREENS = DescribedPredicate.describe<JavaClass>("are in a ui package or are activities") { type ->
            "ui" in type.packageName.split('.') || type.isAssignableTo("android.app.Activity")
        }

        /** What blocks or reaches the disk, the keychain or the network; the engine's data types are not here. */
        val READS_OR_WRITES = setOf(
            "nl.enadeag.carchargeplanner.engine.Engine",
            "nl.enadeag.carchargeplanner.engine.secrets.ApiKeys",
            "nl.enadeag.carchargeplanner.engine.secrets.Keychain",
            "nl.enadeag.carchargeplanner.engine.secrets.SecretsFile",
            "nl.enadeag.carchargeplanner.engine.config.ConfigFile",
            "nl.enadeag.carchargeplanner.engine.store.StoredFile",
            AndroidPlatform::class.java.name,
            "$APP.secrets.EncryptedKeychain",
            "$APP.secrets.AndroidKeystoreKeys",
            "$APP.secrets.KeychainKeys",
        )

        val THE_ENGINE_ITSELF = DescribedPredicate.describe<JavaClass>(
            "are the engine, the API keys, the keychain, the settings file or the platform's parts") { type ->
            type.name in READS_OR_WRITES
        }

        val SCREENS_REACH_THE_ENGINE_ONLY_THROUGH_ITS_SUSPEND_INTERFACE =
            noClasses().that(SCREENS).should().dependOnClassesThat(THE_ENGINE_ITSELF)
                .because("they may block for about a second, so the screens call them through EngineAccess, which "
                    + "runs them off the main thread (#313); the app's Application is the one place that makes them")

        val CALL_THE_ROUND = DescribedPredicate.describe<JavaMethodCall>(
            "call EngineAccess.round or EngineAccess.nextNoticeDue") { call ->
            call.targetOwner.isAssignableTo(EngineAccess::class.java) && call.name in setOf("round", "nextNoticeDue")
        }

        val SCREENS_LEAVE_THE_ROUND_TO_THE_BACKGROUND_WORK =
            noClasses().that(SCREENS).should().callMethodWhere(CALL_THE_ROUND)
                .because("a round tells the owner what is due, once, while a screen shows an outlook and tells "
                    + "nobody; the round and the next due notice are for the work in the background")
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
    fun theScreensRuleSeesTheAppsScreensButNotItsApplication() {
        assertThat(SCREENS.test(APP_CLASSES.get(MainActivity::class.java))).`as`("the activity").isTrue()
        assertThat(SCREENS.test(APP_CLASSES.get("$APP.ui.AppScreenKt"))).`as`("a screen, in Kotlin").isTrue()
        assertThat(SCREENS.test(APP_CLASSES.get(CarChargePlannerApp::class.java))).`as`("the Application").isFalse()
        assertThat(SCREENS.test(APP_CLASSES.get(AppEngine::class.java))).`as`("the engine's builder").isFalse()
    }

    @Test
    fun theScreensReachTheEngineOnlyThroughItsSuspendInterface() {
        SCREENS_REACH_THE_ENGINE_ONLY_THROUGH_ITS_SUSPEND_INTERFACE.check(APP_CLASSES)
    }

    @Test
    fun aScreenThatCallsTheEngineItselfBreaksTheRule() {
        val screen = ClassFileImporter().importClasses(ScreenThatReadsTheSettingsFile::class.java)

        assertThatThrownBy { SCREENS_REACH_THE_ENGINE_ONLY_THROUGH_ITS_SUSPEND_INTERFACE.check(screen) }
            .isInstanceOf(AssertionError::class.java).hasMessageContaining("ConfigFile")
    }

    @Test
    fun theScreensLeaveTheRoundToTheBackgroundWork() {
        SCREENS_LEAVE_THE_ROUND_TO_THE_BACKGROUND_WORK.check(APP_CLASSES)
    }

    @Test
    fun aScreenThatMakesARoundBreaksTheRule() {
        val screen = ClassFileImporter().importClasses(ScreenThatPlansARound::class.java)

        assertThatThrownBy { SCREENS_LEAVE_THE_ROUND_TO_THE_BACKGROUND_WORK.check(screen) }
            .isInstanceOf(AssertionError::class.java).hasMessageContaining("ScreenThatPlansARound.plan")
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
    fun noClassChangesTheNetworkOfTheWholeApp() {
        noClasses()
            .should().accessTargetWhere(CHANGE_THE_NETWORK_OF_THE_PROCESS)
            .because("such a setting reaches every connection, AndroidHttpGet's too, around the contract it keeps on "
                + "redirects and what it sends")
            .check(APP_CLASSES)
    }

    @Test
    fun theAppLogsThroughSlf4jAndOnlyItsSinkWritesToLogcat() {
        noClasses().that().doNotHaveFullyQualifiedName(LOGCAT_SINK)
            .should().dependOnClassesThat().haveFullyQualifiedName("android.util.Log")
            .orShould().dependOnClassesThat().resideInAPackage("java.util.logging..")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("java.lang.System\$Logger")
            .orShould().accessTargetWhere(WRITE_PAST_SLF4J)
            .because("the app logs through slf4j, as the engine does, so its records and the engine's go to Logcat "
                + "one way, at the build's level")
            .check(APP_CLASSES)
    }

    @Test
    fun theScreensDrawTheirButtonsThroughTheAppsOwnWhichAreTallEnough() {
        val materialButtons = DescribedPredicate.describe<JavaAccess<*>>("call Material's buttons themselves") {
            access -> access.targetOwner.name == "androidx.compose.material3.ButtonKt"
                && access.name.substringBefore('$') in setOf("Button", "OutlinedButton", "TextButton",
                    "ElevatedButton", "FilledTonalButton")
        }
        noClasses().that().doNotHaveFullyQualifiedName("$APP.ui.components.ButtonsKt")
            .should().accessTargetWhere(materialButtons)
            .because("Material draws its buttons at 40 dp, and every control is drawn at 48 dp at least "
                + "(docs/accessibility.md): AppButton, AppOutlinedButton and AppTextButton do that")
            .check(APP_CLASSES)
    }
}

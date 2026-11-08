package nl.enadeag.carchargeplanner.android

import androidx.lifecycle.SavedStateHandle
import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.AccessTarget
import com.tngtech.archunit.core.domain.JavaAccess
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.domain.JavaField
import com.tngtech.archunit.core.domain.JavaFieldAccess
import com.tngtech.archunit.core.domain.JavaMethodCall
import com.tngtech.archunit.core.domain.JavaModifier
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices
import java.time.Clock
import nl.enadeag.carchargeplanner.android.logging.LogcatServiceProvider
import nl.enadeag.carchargeplanner.android.store.EngineAccess
import nl.enadeag.carchargeplanner.android.store.EngineOnDispatcher
import nl.enadeag.carchargeplanner.android.background.WorkThatCallsTheEngineItself
import nl.enadeag.carchargeplanner.android.background.WorkThatCastsToTheApp
import nl.enadeag.carchargeplanner.android.background.WorkThatPlansARound
import nl.enadeag.carchargeplanner.android.backgroundwork.WorkBesideTheBackgroundPackage
import nl.enadeag.carchargeplanner.android.store.StoreThatCallsTheEngineItself
import nl.enadeag.carchargeplanner.android.home.LogicThatHoldsTheApiKeys
import nl.enadeag.carchargeplanner.android.home.LogicThatKnowsAScreen
import nl.enadeag.carchargeplanner.android.home.LogicThatMakesThreads
import nl.enadeag.carchargeplanner.android.home.LogicThatPlansARound
import nl.enadeag.carchargeplanner.android.home.LogicThatWritesAFile
import nl.enadeag.carchargeplanner.android.home.LogicThatReadsTheClock
import nl.enadeag.carchargeplanner.android.home.NetworkOfItsOwn
import nl.enadeag.carchargeplanner.android.ui.ScreenAccessThatPlansInALambda
import nl.enadeag.carchargeplanner.android.ui.ScreenOfTheLogicThatKnowsIt
import nl.enadeag.carchargeplanner.android.ui.ScreenThatPlansARound
import nl.enadeag.carchargeplanner.android.ui.ScreenThatRoundsANumber
import nl.enadeag.carchargeplanner.android.ui.background.ScreenWorkThatPlansARound
import nl.enadeag.carchargeplanner.android.ui.ScreenThatReadsTheSettingsFile
import nl.enadeag.carchargeplanner.engine.forecast.HttpGet
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * The rules for the app's own classes, Java and Kotlin alike (its main code, apart from the one rule for the tests'
 * sleeps), as
 * `EngineArchitectureTest` has them for the engine (ADR 0010): one class opens network connections, [AndroidHttpGet],
 * which keeps the engine's contract on timeouts, size and redirects; no class weakens the checks of TLS; the app
 * logs through slf4j, whose provider is the one class that writes to Logcat; the screens and their logic reach the
 * engine only through the suspend interface that runs it off the main thread, which only the composition root and
 * `store.EngineOnDispatcher` get past; and only the work in the background, in the app's
 * `background` package, makes the planning round and asks when the next notice is due, through that interface. And
 * ADR 0005's wiring rules, as the desktop's `ArchitectureTest` has them (ADR 0010): only the Application reads the
 * system's clock and the phone's time zone, only the composition root and the HttpGet make threads, and no class keeps
 * static mutable state. As ADR 0001 has it for every client, only the keychain writes a file and what handles a
 * request's address logs nothing. The app's parts, one package each below the root, have no cycles, only the screens
 * know the screens, and only Android knows the Application.
 */
class AppArchitectureTest {

    private companion object {

        const val APP = "nl.enadeag.carchargeplanner.android"

        val HTTP_GET: String = AndroidHttpGet::class.java.name

        const val LOGCAT_SINK = "$APP.logging.LogcatSink"

        // AGP puts each variant's local tests in folders named after it, such as debugUnitTest, which ArchUnit's own
        // DoNotIncludeTests does not know
        val MAIN_CODE_ONLY = ImportOption { location -> !location.contains("UnitTest/") }

        // the classes of the local tests, which MAIN_CODE_ONLY leaves out
        val TEST_CODE_ONLY = ImportOption { location -> location.contains("UnitTest/") }

        val TEST_CLASSES: JavaClasses by lazy {
            ClassFileImporter().withImportOption(TEST_CODE_ONLY).importPackages(APP)
        }

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
                + "host name verifier, the security providers or properties)") { access ->
            val owner = access.targetOwner.name
            owner == "javax.net.ssl.SSLContext" && access.name in setOf("init", "setDefault")
                || owner == "javax.net.ssl.HttpsURLConnection" && access.name in setOf("setSSLSocketFactory",
                "setDefaultSSLSocketFactory", "setHostnameVerifier", "setDefaultHostnameVerifier")
                || owner == "java.security.Security" && access.name in setOf("setProperty", "addProvider",
                "insertProviderAt", "removeProvider")
        }

        val CHANGE_THE_NETWORK_OF_THE_PROCESS = DescribedPredicate.describe<JavaAccess<*>>(
            "change how every connection of the process works (the default proxy selector, authenticator, cookie "
                + "handler or response cache, URL's stream handlers, URLConnection's content handlers, following "
                + "redirects for every HttpURLConnection, a system property or the live set System.getProperties hands "
                + "out, as ArchUnit cannot read which and the TLS and proxy ones reach every connection)") { access ->
            val owner = access.targetOwner.name
            owner in setOf("java.net.ProxySelector", "java.net.Authenticator", "java.net.CookieHandler",
                "java.net.ResponseCache") && access.name == "setDefault"
                || owner == "java.net.URL" && access.name == "setURLStreamHandlerFactory"
                || owner == "java.net.URLConnection" && access.name == "setContentHandlerFactory"
                || owner == "java.net.HttpURLConnection" && access.name == "setFollowRedirects"
                || owner == "java.lang.System"
                && access.name in setOf("setProperty", "setProperties", "clearProperty", "getProperties")
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

        val ONLY_THE_HTTP_GET_OPENS_CONNECTIONS = noClasses().that(DescribedPredicate.not(THE_HTTP_GET))
            .should().dependOnClassesThat(OPENS_A_CONNECTION)
            .orShould().dependOnClassesThat().resideInAnyPackage(*NETWORK_PACKAGES)
            .orShould().accessTargetWhere(CONNECT_THROUGH_A_URL_OR_NETWORK)
            .because("every request goes through the HttpGet the app hands the engine, which keeps its contract on "
                + "redirects, timeouts and size (ADR 0010)")

        val NO_CLASS_CHANGES_HOW_TLS_IS_CHECKED = noClasses()
            .should().dependOnClassesThat(CHECK_TLS)
            .orShould().accessTargetWhere(TAMPER_WITH_TLS)
            .because("the app trusts the certificates and checks the host names as Android does; its own trust "
                + "manager or host name verifier is how an app lets a forged certificate through")

        val NO_CLASS_CHANGES_THE_NETWORK_OF_THE_APP = noClasses()
            .should().accessTargetWhere(CHANGE_THE_NETWORK_OF_THE_PROCESS)
            .because("such a setting reaches every connection, AndroidHttpGet's too, around the contract it keeps on "
                + "redirects and what it sends")

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

        const val BACKGROUND_WORK = "$APP.background"

        val ENGINE_ON_DISPATCHER: String = EngineOnDispatcher::class.java.name

        const val ENGINE = "nl.enadeag.carchargeplanner.engine.Engine"

        val ROUND_METHODS = setOf("round", "nextNoticeDue")

        /** The app's own background package or one inside it, not any package that has a `background` part. */
        fun inTheBackgroundWork(type: JavaClass) =
            type.packageName == BACKGROUND_WORK || type.packageName.startsWith("$BACKGROUND_WORK.")

        /**
         * Whether a call comes from the called method's own continuation or lambda, which Kotlin nests in the class
         * that has the method under the method's name: a suspend function's continuation calls it again on resuming,
         * which is no round of its own.
         */
        fun fromTheMethodItself(call: JavaMethodCall, owner: String) =
            call.originOwner.name.startsWith("$owner$${call.name}$")

        // through the interface or any class that has it, such as EngineOnDispatcher; the engine's own methods only
        // from the lambda that EngineOnDispatcher's method of the same name runs on its dispatcher
        val MAKE_A_ROUND_OUT_OF_PLACE = DescribedPredicate.describe<JavaMethodCall>(
            "call EngineAccess.round or EngineAccess.nextNoticeDue outside the package $BACKGROUND_WORK, or "
                + "Engine.round or Engine.nextNoticeDue outside EngineOnDispatcher's own round and nextNoticeDue") {
            call ->
            call.name in ROUND_METHODS && (
                call.targetOwner.isAssignableTo(EngineAccess::class.java) && !inTheBackgroundWork(call.originOwner)
                    && !fromTheMethodItself(call, call.targetOwner.name)
                    || call.targetOwner.name == ENGINE && !fromTheMethodItself(call, ENGINE_ON_DISPATCHER))
        }

        /** The class Kotlin makes for [ScreenThatPlansARound.planInALambda]'s lambda. */
        val SCREENS_LAMBDA = ScreenThatPlansARound::class.java.name + "\$planInALambda\$1"

        /** The files of the dashboard's models, which hold its rules and know nothing of Android. */
        val MODEL_FILES = setOf("Dashboard.kt", "DayModel.kt", "DayMapper.kt", "GridAdviceModel.kt", "ListedTrip.kt",
            "ComingAway.kt", "SolarChart.kt", "TripAdviceModel.kt")

        /** The other files in the dashboard's package: entries, notices and the like, which may use Android. */
        val NOT_MODEL_FILES = setOf("AwayEntry.kt", "BatteryEntry.kt", "TripEntry.kt", "ChangeQueue.kt",
            "HomeNotice.kt", "HomeOutlook.kt", "NotificationCard.kt")

        val DASHBOARD_MODELS = DescribedPredicate.describe<JavaClass>(
            "are the dashboard's models in $APP.home (${MODEL_FILES.sorted().joinToString()})") { type ->
            type.packageName == "$APP.home" && type.source.flatMap { it.fileName }.orElse("") in MODEL_FILES
        }

        // the Compose compiler marks every class of the module with this annotation, which nothing reads but Compose
        const val COMPOSE_STABILITY = "androidx.compose.runtime.internal.StabilityInferred"

        val ANDROID = DescribedPredicate.describe<JavaClass>("are Android's, AndroidX's or Compose's") { type ->
            (type.packageName.startsWith("android.") || type.packageName.startsWith("androidx."))
                && type.name != COMPOSE_STABILITY
        }

        val ONLY_THE_BACKGROUND_WORK_MAKES_A_ROUND =
            noClasses().should().callMethodWhere(MAKE_A_ROUND_OUT_OF_PLACE)
                .because("a round tells the owner what is due, once, while a screen or its logic shows an outlook "
                    + "and tells nobody; the round and the next due notice are for the work in the background, in the "
                    + "app's background package (#362), which reaches the engine through EngineAccess")

        /** Whether a class is one of these or nested in one, such as a lambda Kotlin makes inside it. */
        fun partOf(type: JavaClass, names: Collection<String>) =
            names.any { name -> type.name == name || type.name.startsWith("$name$") }

        val ROOT: String = CarChargePlannerApp::class.java.name

        /** The composition root and the parts it makes the engine and the platform's seams with. */
        val ROOT_PARTS = setOf(ROOT, AppEngine::class.java.name, AndroidPlatform::class.java.name)

        val READ_THE_SYSTEM_CLOCK = DescribedPredicate.describe<JavaAccess<*>>(
            "read the system's clock or time zone") { access ->
            val owner = access.targetOwner.name
            val name = access.name
            val takesAClock = (access.target as? AccessTarget.CodeUnitAccessTarget)?.rawParameterTypes
                ?.any { it.name == Clock::class.java.name } == true
            name == "now" && owner.startsWith("java.time.") && !takesAClock
                || owner == Clock::class.java.name && name in setOf("systemUTC", "systemDefaultZone", "system")
                || owner == "java.time.ZoneId" && name == "systemDefault"
                || owner == "java.util.TimeZone" && name == "getDefault"
                || owner == "java.lang.System" && name == "currentTimeMillis"
                || owner == "android.os.SystemClock"
                && name in setOf("currentTimeMillis", "elapsedRealtime", "elapsedRealtimeNanos", "uptimeMillis")
        }

        val ONLY_THE_ROOT_READS_THE_CLOCK =
            noClasses().that(DescribedPredicate.describe("are not the composition root") { type ->
                !partOf(type, setOf(ROOT))
            })
                .should().accessTargetWhere(READ_THE_SYSTEM_CLOCK)
                .because("the clock and the phone's time zone are handed in by the Application, so a test can set "
                    + "the time and the engine, the notifications, the alarm and the screens see one (ADR 0005, "
                    + "ADR 0010)")

        val START_A_THREAD = DescribedPredicate.describe<JavaAccess<*>>(
            "start a thread or make an executor or a dispatcher of their own") { access ->
            val owner = access.targetOwner
            owner.name == "java.lang.Thread" && access.name in setOf("<init>", "ofVirtual", "ofPlatform",
                "startVirtualThread")
                || owner.name == "java.util.concurrent.Executors"
                || owner.name == "java.util.concurrent.ForkJoinPool" && access.name in setOf("<init>", "commonPool")
                || owner.isAssignableTo("java.util.Timer") && access.name == "<init>"
                || owner.isAssignableTo("java.util.concurrent.ThreadPoolExecutor") && access.name == "<init>"
                || owner.name == "android.os.HandlerThread" && access.name == "<init>"
                || owner.name == "kotlin.concurrent.ThreadsKt"
                || owner.name.startsWith("kotlinx.coroutines.ThreadPoolDispatcherKt")
                || owner.name == "kotlinx.coroutines.ExecutorsKt" && access.name == "from"
        }

        val THREAD_OWNERS = ROOT_PARTS + HTTP_GET

        val ONLY_THE_ROOT_AND_THE_HTTP_GET_OWN_THREADS =
            noClasses().that(DescribedPredicate.describe("are not the composition root or the HttpGet") { type ->
                !partOf(type, THREAD_OWNERS)
            })
                .should().accessTargetWhere(START_A_THREAD)
                .because("the root hands out the threads and dispatchers the parts run on (ADR 0005, ADR 0010); "
                    + "AndroidHttpGet runs each request on a pool of its own, which keeps its timeouts")

        /** The calls, of Kotlin's or of `java.util`, that hand back a mutable collection or map. */
        val MAKE_A_MUTABLE_COLLECTION = DescribedPredicate.describe<JavaMethodCall>(
            "make a mutable collection or map") { call ->
            val owner = call.targetOwner.name
            owner.startsWith("kotlin.collections.") && call.name in setOf("mutableListOf", "arrayListOf",
                "mutableSetOf", "hashSetOf", "linkedSetOf", "sortedSetOf", "mutableMapOf", "hashMapOf", "linkedMapOf",
                "sortedMapOf", "toMutableList", "toMutableSet", "toMutableMap", "toCollection")
                || owner == "java.util.Collections" && (call.name.startsWith("synchronized")
                    || call.name.startsWith("checked") || call.name == "newSetFromMap")
                || owner == "java.util.concurrent.ConcurrentHashMap" && call.name == "newKeySet"
        }

        fun isCollectionOrMap(type: JavaClass) =
            type.isAssignableTo(java.util.Collection::class.java) || type.isAssignableTo(java.util.Map::class.java)

        /**
         * Whether a static field of a collection or map interface, such as a Kotlin `MutableList`, gets a mutable one
         * where its class's static initializer sets it. Of the collections and maps the initializer makes after the
         * line that sets the static field before it, up to the line that sets this one, the last line's decide: a
         * mutable one made there (a constructor, which Kotlin's `mutableListOf()` without values is too, or a call of
         * [MAKE_A_MUTABLE_COLLECTION]) and no other call there that hands back a collection or map, such as
         * `toList()`. ArchUnit gives no order within a line, so a mutable collection made from another on the same
         * line is not seen: `ArrayList(listOf(...))`, `listOf(...).toMutableList()`, `filter { }.toMutableList()` and
         * the like.
         */
        fun madeMutable(field: JavaField): Boolean {
            val initializer = field.owner.staticInitializer.orElse(null)
            if (!field.rawType.isInterface || initializer == null || !isCollectionOrMap(field.rawType)) return false
            val sets = initializer.fieldAccesses
                .filter { it.accessType == JavaFieldAccess.AccessType.SET && it.targetOwner == field.owner }
            val line = sets.filter { it.name == field.name }.maxOfOrNull { it.lineNumber } ?: return false
            val after = sets.map { it.lineNumber }.filter { it < line }.maxOrNull() ?: 0
            val mutable = initializer.constructorCallsFromSelf
                .filter { !it.targetOwner.isInterface && isCollectionOrMap(it.targetOwner) }.map { it.lineNumber } +
                initializer.methodCallsFromSelf.filter { MAKE_A_MUTABLE_COLLECTION.test(it) }.map { it.lineNumber }
            val others = initializer.methodCallsFromSelf
                .filter { !MAKE_A_MUTABLE_COLLECTION.test(it) && isCollectionOrMap(it.target.rawReturnType) }
                .map { it.lineNumber }
            val last = (mutable + others).filter { it in (after + 1)..line }.maxOrNull() ?: return false
            return last in mutable && last !in others
        }

        /** What may change while a static field keeps pointing at it. */
        val HOLDS_A_MUTABLE_OBJECT = DescribedPredicate.describe<JavaField>(
            "hold a mutable collection (also one of an interface type, such as a MutableList, given a new one where "
                + "it is set), an atomic, a builder, an array, a lock or a mutable flow") { field ->
            val type = field.rawType
            type.isArray
                || type.packageName.startsWith("java.util.concurrent.atomic")
                || type.packageName == "java.util.concurrent.locks"
                || type.isAssignableTo(StringBuilder::class.java) || type.isAssignableTo(StringBuffer::class.java)
                || !type.isInterface && (type.isAssignableTo(java.util.Collection::class.java)
                    || type.isAssignableTo(java.util.Map::class.java))
                || type.isAssignableTo(java.util.Queue::class.java)
                || type.isAssignableTo(java.util.concurrent.ConcurrentMap::class.java)
                || type.name in setOf("kotlinx.coroutines.sync.Mutex", "kotlinx.coroutines.sync.Semaphore",
                    "kotlinx.coroutines.flow.MutableStateFlow", "kotlinx.coroutines.flow.MutableSharedFlow")
                || madeMutable(field)
        }

        /** The resource IDs, which the build leaves not final, so a library's are known only once the app is built. */
        val R_CLASS = "$APP.R"

        /** The lambdas the Compose compiler keeps once per file, made when first asked for, in fields not final. */
        const val COMPOSABLE_SINGLETONS = "ComposableSingletons$"

        /**
         * The static state the app keeps on purpose, each with its reason: the keychain's lock per file, which every
         * keychain on that file in the process shares, also one a test makes beside the app's, so two never write the
         * file at once.
         */
        val DELIBERATE_STATICS = setOf("$APP.secrets.EncryptedKeychain.LOCKS")

        val STATIC_FIELDS = DescribedPredicate.describe<JavaField>(
            "are static, not made by a compiler, not resource IDs and not named as deliberate") { field ->
            JavaModifier.STATIC in field.modifiers && JavaModifier.SYNTHETIC !in field.modifiers
                && !partOf(field.owner, setOf(R_CLASS))
                && !field.owner.name.substringAfterLast('.').startsWith(COMPOSABLE_SINGLETONS)
                && field.fullName !in DELIBERATE_STATICS
        }

        val NO_STATIC_MUTABLE_STATE = noFields().that(STATIC_FIELDS)
            .should(ArchCondition.from(DescribedPredicate.describe<JavaField>("be static mutable state: not final, "
                + "or holding a mutable collection, an atomic, a builder, an array, a lock or a mutable flow") {
                field -> JavaModifier.FINAL !in field.modifiers || HOLDS_A_MUTABLE_OBJECT.test(field)
            }))
            .because("dependencies come in through constructors from the Application, never through global state "
                + "(ADR 0005, ADR 0010): a Kotlin top-level var or lock is such state, and a static outlives the "
                + "Application that a Robolectric test makes for each test; ArchUnit sees what a field of an "
                + "interface type holds only where the static initializer makes it, not a mutable one a function "
                + "hands back, nor one made from another collection on the same line, such as "
                + "listOf(..).toMutableList() or ArrayList(listOf(..))")

        /** The packages of the platform's parts that the engine is made of: the keychain and the log. */
        val PLATFORM_PARTS = setOf("$APP.secrets", "$APP.logging")

        val MAY_HOLD_THE_ENGINE_ITSELF = DescribedPredicate.describe<JavaClass>(
            "are the composition root, EngineOnDispatcher or the platform's keychain or log") { type ->
            partOf(type, ROOT_PARTS + ENGINE_ON_DISPATCHER) || type.packageName in PLATFORM_PARTS
        }

        val ONLY_THE_ROOT_AND_THE_STORE_HOLD_THE_ENGINE_ITSELF =
            noClasses().that(DescribedPredicate.not(MAY_HOLD_THE_ENGINE_ITSELF))
                .should().dependOnClassesThat(THE_ENGINE_ITSELF)
                .because("they may block for about a second, so the screens and their logic, which run on the main "
                    + "thread, call them through EngineAccess, which runs them off it (#313); the app's Application "
                    + "is the one place that makes them, and EngineOnDispatcher the one that calls them")

        val CHANGE_FILES = DescribedPredicate.describe<JavaAccess<*>>(
            "write, move or delete a file (through Files, File, Kotlin's file functions or the Context)") { access ->
            val owner = access.targetOwner.name
            owner == "java.nio.file.Files" && access.name in setOf("write", "writeString", "newOutputStream",
                "newBufferedWriter", "createFile", "createTempFile", "createTempDirectory", "createDirectory",
                "createDirectories", "move", "copy", "delete", "deleteIfExists", "newByteChannel", "setOwner",
                "setAttribute", "setLastModifiedTime", "setPosixFilePermissions", "getFileAttributeView",
                "createSymbolicLink", "createLink")
                || owner == "java.io.File" && access.name in setOf("delete", "deleteOnExit", "renameTo",
                "createNewFile", "createTempFile", "mkdir", "mkdirs", "setWritable", "setReadable", "setExecutable",
                "setLastModified")
                // Kotlin calls a function with a default argument through its name$default
                || owner.startsWith("kotlin.io.FilesKt") && access.name.substringBefore('$') in setOf("writeText",
                "writeBytes", "appendText", "appendBytes", "copyTo", "copyRecursively", "deleteRecursively", "writer",
                "printWriter", "bufferedWriter", "outputStream")
                || owner == "android.content.Context" && access.name in setOf("openFileOutput", "deleteFile")
        }

        val WRITE_TO_A_FILE = DescribedPredicate.describe<JavaClass>(
            "write to a file themselves (FileOutputStream, FileWriter, RandomAccessFile, a channel)") { type ->
            type.name in setOf("java.io.FileOutputStream", "java.io.FileWriter", "java.io.RandomAccessFile")
                || type.packageName == "java.nio.channels"
        }

        const val KEYCHAIN = "$APP.secrets"

        val ONLY_THE_KEYCHAIN_CHANGES_FILES = noClasses().that().resideOutsideOfPackage(KEYCHAIN)
            .should().accessTargetWhere(CHANGE_FILES)
            .orShould().dependOnClassesThat(WRITE_TO_A_FILE)
            .because("no client writes the engine's files, which the engine writes through the PrivateFiles it is "
                + "handed (ADR 0001, ADR 0010); the keychain keeps a file of its own beside them, and the phone's "
                + "own preferences go through AndroidX's DataStore")

        val HANDLE_REQUEST_ADDRESSES = DescribedPredicate.describe<JavaClass>(
            "make or read the address of a request to a provider") { type ->
            type.accessesFromSelf.any { access -> access.targetOwner.name == HttpGet.Request::class.java.name }
        }

        val REQUEST_ADDRESSES_NEVER_REACH_A_LOG = noClasses().that(HANDLE_REQUEST_ADDRESSES)
            .should().dependOnClassesThat().resideInAPackage("org.slf4j..")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("android.util.Log")
            .orShould().accessTargetWhere(WRITE_PAST_SLF4J)
            .orShould().callMethodWhere(DescribedPredicate.describe("print a stack trace") { call ->
                call.name == "printStackTrace" && call.targetOwner.isAssignableTo(Throwable::class.java)
            })
            .because("an address can hold an API key and the home's coordinates, so what makes or sends one logs "
                + "nothing, as in the engine and the desktop (ADR 0001)")

        const val SCREENS = "$APP.ui"

        val THE_LOGIC_KNOWS_NO_SCREENS = noClasses().that().resideOutsideOfPackages("$SCREENS..", APP)
            .should().dependOnClassesThat().resideInAPackage("$SCREENS..")
            .because("the logic, the store, the work in the background and the platform's parts are used and tested "
                + "without the screens, which build on them; the Application wires the one screen part the engine "
                + "tells the owner through, the notifications")

        val NO_CYCLES_BETWEEN_THE_APPS_PARTS = slices().matching("$APP.(*)..")
            .should().beFreeOfCycles()
            .because("the app's parts depend one way, so each can be read and tested alone, as the desktop's (ADR "
                + "0005); the root package, which wires them, is not one of them")

        val ONLY_ANDROID_KNOWS_THE_APPLICATION = noClasses().that().resideOutsideOfPackage(APP)
            .should().dependOnClassesThat().haveFullyQualifiedName(ROOT)
            .because("the parts get what they need through their constructors; what Android makes itself, the worker "
                + "and the receivers, finds its parts through an interface of its own package, BackgroundHost, "
                + "which the Application is (ADR 0005)")
    }

    @Test
    fun onlyTheRootReadsTheClock() {
        ONLY_THE_ROOT_READS_THE_CLOCK.check(APP_CLASSES)
    }

    @Test
    fun aClassThatReadsTheClockItselfBreaksTheRule() {
        val reader = ClassFileImporter().importClasses(LogicThatReadsTheClock::class.java)

        assertThatThrownBy { ONLY_THE_ROOT_READS_THE_CLOCK.check(reader) }
            .isInstanceOf(AssertionError::class.java)
            .hasMessageContaining("Clock.systemUTC()")
            .hasMessageContaining("ZoneId.systemDefault()")
            .hasMessageContaining("Instant.now()")
            .hasMessageContaining("SystemClock.elapsedRealtime()")
    }

    @Test
    fun onlyTheRootAndTheHttpGetOwnThreads() {
        ONLY_THE_ROOT_AND_THE_HTTP_GET_OWN_THREADS.check(APP_CLASSES)
    }

    @Test
    fun aClassThatMakesAThreadOrAnExecutorBreaksTheRule() {
        val maker = ClassFileImporter().importClasses(LogicThatMakesThreads::class.java)

        assertThatThrownBy { ONLY_THE_ROOT_AND_THE_HTTP_GET_OWN_THREADS.check(maker) }
            .isInstanceOf(AssertionError::class.java)
            .hasMessageContaining("Thread.<init>(")
            .hasMessageContaining("Executors.newSingleThreadExecutor()")
            .hasMessageContaining("ThreadPoolDispatcherKt.newSingleThreadContext(")
    }

    @Test
    fun noClassKeepsStaticMutableState() {
        NO_STATIC_MUTABLE_STATE.check(APP_CLASSES)
    }

    @Test
    fun aTopLevelVarOrLockIsStaticMutableState() {
        val state = ClassFileImporter().importClasses(Class.forName("$APP.home.TopLevelStateKt"))

        assertThatThrownBy { NO_STATIC_MUTABLE_STATE.check(state) }
            .isInstanceOf(AssertionError::class.java)
            .hasMessageContaining("TopLevelStateKt.counted")
            .hasMessageContaining("TopLevelStateKt.ONE_AT_A_TIME")
            .hasMessageContaining("TopLevelStateKt.seen")
            .hasMessageContaining("TopLevelStateKt.asked")
            .hasMessageContaining("TopLevelStateKt.counts")
            .satisfies({ failure ->
                assertThat(failure.message).doesNotContain("TopLevelStateKt.LIMIT")
                    .doesNotContain("TopLevelStateKt.names")
            })
    }

    @Test
    fun onlyTheKeychainChangesFiles() {
        ONLY_THE_KEYCHAIN_CHANGES_FILES.check(APP_CLASSES)
    }

    @Test
    fun logicThatWritesAFileBreaksTheRule() {
        val logic = ClassFileImporter().importClasses(LogicThatWritesAFile::class.java)

        assertThatThrownBy { ONLY_THE_KEYCHAIN_CHANGES_FILES.check(logic) }
            .isInstanceOf(AssertionError::class.java)
            .hasMessageContaining("FilesKt.writeText")
            .hasMessageContaining("File.delete()")
            .hasMessageContaining("Files.move(")
    }

    @Test
    fun requestAddressesNeverReachALog() {
        REQUEST_ADDRESSES_NEVER_REACH_A_LOG.check(APP_CLASSES)
    }

    @Test
    fun aGetThatLogsItsAddressBreaksTheRule() {
        val get = ClassFileImporter().importClasses(GetThatLogsItsAddress::class.java)

        assertThatThrownBy { REQUEST_ADDRESSES_NEVER_REACH_A_LOG.check(get) }
            .isInstanceOf(AssertionError::class.java).hasMessageContaining("org.slf4j.Logger")
    }

    @Test
    fun theLogicKnowsNoScreens() {
        THE_LOGIC_KNOWS_NO_SCREENS.check(APP_CLASSES)
    }

    @Test
    fun logicThatKnowsAScreenBreaksTheRule() {
        val logic = ClassFileImporter().importClasses(LogicThatKnowsAScreen::class.java)

        assertThatThrownBy { THE_LOGIC_KNOWS_NO_SCREENS.check(logic) }
            .isInstanceOf(AssertionError::class.java).hasMessageContaining("ScreenOfTheLogicThatKnowsIt")
    }

    @Test
    fun theAppsPartsHaveNoCycles() {
        NO_CYCLES_BETWEEN_THE_APPS_PARTS.check(APP_CLASSES)
    }

    @Test
    fun twoPartsThatKnowEachOtherBreakTheRule() {
        val cycle = ClassFileImporter().importClasses(LogicThatKnowsAScreen::class.java,
            ScreenOfTheLogicThatKnowsIt::class.java)

        assertThatThrownBy { NO_CYCLES_BETWEEN_THE_APPS_PARTS.check(cycle) }
            .isInstanceOf(AssertionError::class.java).hasMessageContaining("Cycle detected")
    }

    @Test
    fun onlyAndroidKnowsTheApplication() {
        ONLY_ANDROID_KNOWS_THE_APPLICATION.check(APP_CLASSES)
    }

    @Test
    fun workThatCastsToTheApplicationBreaksTheRule() {
        val work = ClassFileImporter().importClasses(WorkThatCastsToTheApp::class.java)

        assertThatThrownBy { ONLY_ANDROID_KNOWS_THE_APPLICATION.check(work) }
            .isInstanceOf(AssertionError::class.java).hasMessageContaining("CarChargePlannerApp")
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
    fun theEngineRuleLetsTheRootTheStoreAndThePlatformsPartsHoldTheEngineAndNothingElse() {
        fun mayHold(name: String) = MAY_HOLD_THE_ENGINE_ITSELF.test(APP_CLASSES.get(name))

        assertThat(mayHold(CarChargePlannerApp::class.java.name)).`as`("the Application").isTrue()
        assertThat(mayHold(AppEngine::class.java.name)).`as`("the engine's builder").isTrue()
        assertThat(mayHold(AndroidPlatform::class.java.name)).`as`("the platform's parts").isTrue()
        assertThat(mayHold(ENGINE_ON_DISPATCHER)).`as`("the suspend interface's one implementation").isTrue()
        assertThat(mayHold("$APP.secrets.EncryptedKeychain")).`as`("the keychain").isTrue()
        assertThat(mayHold(MainActivity::class.java.name)).`as`("the activity").isFalse()
        assertThat(mayHold("$APP.ui.AppScreenKt")).`as`("a screen, in Kotlin").isFalse()
        assertThat(mayHold("$APP.home.HomeOutlook")).`as`("the home's logic").isFalse()
        assertThat(mayHold("$APP.store.Saves")).`as`("another class of the store").isFalse()
    }

    @Test
    fun onlyTheRootAndTheStoreHoldTheEngineItself() {
        ONLY_THE_ROOT_AND_THE_STORE_HOLD_THE_ENGINE_ITSELF.check(APP_CLASSES)
    }

    @Test
    fun aScreenThatCallsTheEngineItselfBreaksTheRule() {
        val screen = ClassFileImporter().importClasses(ScreenThatReadsTheSettingsFile::class.java)

        assertThatThrownBy { ONLY_THE_ROOT_AND_THE_STORE_HOLD_THE_ENGINE_ITSELF.check(screen) }
            .isInstanceOf(AssertionError::class.java).hasMessageContaining("ConfigFile")
    }

    @Test
    fun logicOutsideTheScreensThatHoldsTheApiKeysBreaksTheRuleToo() {
        val logic = ClassFileImporter().importClasses(LogicThatHoldsTheApiKeys::class.java)

        assertThatThrownBy { ONLY_THE_ROOT_AND_THE_STORE_HOLD_THE_ENGINE_ITSELF.check(logic) }
            .isInstanceOf(AssertionError::class.java).hasMessageContaining("ApiKeys")
    }

    @Test
    fun onlyTheBackgroundWorkMakesARound() {
        ONLY_THE_BACKGROUND_WORK_MAKES_A_ROUND.check(APP_CLASSES)
    }

    @Test
    fun aRoundOrTheNextDueNoticeOutsideTheBackgroundWorkBreaksTheRule() {
        val screen = ClassFileImporter().importClasses(ScreenThatPlansARound::class.java,
            Class.forName(SCREENS_LAMBDA))

        assertThatThrownBy { ONLY_THE_BACKGROUND_WORK_MAKES_A_ROUND.check(screen) }
            .isInstanceOf(AssertionError::class.java)
            .hasMessageContaining("ScreenThatPlansARound.plan(")
            .hasMessageContaining("ScreenThatPlansARound.due(")
            .hasMessageContaining("ScreenThatPlansARound.planOnTheDispatcher(")
            .hasMessageContaining("${SCREENS_LAMBDA}.invokeSuspend(")
    }

    @Test
    fun aLambdaNestedInAClassThatHasEngineAccessIsNoContinuationOfItsRound() {
        val lambda = ScreenAccessThatPlansInALambda::class.java.name + "\$planLater\$1"
        val screen = ClassFileImporter().importClasses(Class.forName(lambda))

        assertThatThrownBy { ONLY_THE_BACKGROUND_WORK_MAKES_A_ROUND.check(screen) }
            .isInstanceOf(AssertionError::class.java).hasMessageContaining("$lambda.invokeSuspend(")
    }

    @Test
    fun logicOutsideTheScreensThatMakesARoundBreaksTheRuleToo() {
        val logic = ClassFileImporter().importClasses(LogicThatPlansARound::class.java)

        assertThatThrownBy { ONLY_THE_BACKGROUND_WORK_MAKES_A_ROUND.check(logic) }
            .isInstanceOf(AssertionError::class.java).hasMessageContaining("LogicThatPlansARound.plan(")
    }

    @Test
    fun aBackgroundPackageOutsideTheAppsOwnBreaksTheRule() {
        val work = ClassFileImporter().importClasses(ScreenWorkThatPlansARound::class.java)

        assertThatThrownBy { ONLY_THE_BACKGROUND_WORK_MAKES_A_ROUND.check(work) }
            .isInstanceOf(AssertionError::class.java).hasMessageContaining("ScreenWorkThatPlansARound.plan(")
    }

    @Test
    fun aPackageNamedLikeTheBackgroundPackageBreaksTheRule() {
        val work = ClassFileImporter().importClasses(WorkBesideTheBackgroundPackage::class.java)

        assertThatThrownBy { ONLY_THE_BACKGROUND_WORK_MAKES_A_ROUND.check(work) }
            .isInstanceOf(AssertionError::class.java).hasMessageContaining("WorkBesideTheBackgroundPackage.plan(")
    }

    @Test
    fun anotherClassOfTheStoreThatCallsTheEngineItselfBreaksTheRule() {
        val store = ClassFileImporter().importClasses(StoreThatCallsTheEngineItself::class.java)

        assertThatThrownBy { ONLY_THE_BACKGROUND_WORK_MAKES_A_ROUND.check(store) }
            .isInstanceOf(AssertionError::class.java).hasMessageContaining("StoreThatCallsTheEngineItself.plan(")
    }

    @Test
    fun theBackgroundWorkMayMakeARoundThroughEngineAccess() {
        ONLY_THE_BACKGROUND_WORK_MAKES_A_ROUND.check(ClassFileImporter().importClasses(WorkThatPlansARound::class.java))
    }

    @Test
    fun theBackgroundWorkThatCallsTheEngineItselfBreaksTheRule() {
        val work = ClassFileImporter().importClasses(WorkThatCallsTheEngineItself::class.java)

        assertThatThrownBy { ONLY_THE_BACKGROUND_WORK_MAKES_A_ROUND.check(work) }
            .isInstanceOf(AssertionError::class.java)
            .hasMessageContaining("WorkThatCallsTheEngineItself.plan(")
            .hasMessageContaining("Engine.round(")
            .hasMessageContaining("Engine.nextNoticeDue(")
    }

    @Test
    fun onlyEngineOnDispatcherCallsTheEnginesRoundItself() {
        val calls = APP_CLASSES.flatMap { type -> type.methodCallsFromSelf }
            .filter { call -> call.targetOwner.name == ENGINE && call.name in ROUND_METHODS }

        assertThat(calls).`as`("the calls the rule lets through").isNotEmpty
            .allSatisfy { call ->
                assertThat(call.originOwner.name).startsWith("$ENGINE_ON_DISPATCHER$${call.name}$")
            }
    }

    @Test
    fun roundingANumberIsNoRound() {
        ONLY_THE_BACKGROUND_WORK_MAKES_A_ROUND.check(
            ClassFileImporter().importClasses(ScreenThatRoundsANumber::class.java))
    }

    @Test
    fun onlyTheHttpGetOpensNetworkConnections() {
        ONLY_THE_HTTP_GET_OPENS_CONNECTIONS.check(APP_CLASSES)
    }

    @Test
    fun noClassChangesHowTlsIsChecked() {
        NO_CLASS_CHANGES_HOW_TLS_IS_CHECKED.check(APP_CLASSES)
    }

    @Test
    fun noClassChangesTheNetworkOfTheWholeApp() {
        NO_CLASS_CHANGES_THE_NETWORK_OF_THE_APP.check(APP_CLASSES)
    }

    @Test
    fun aClassWithANetworkOfItsOwnBreaksTheNetworkAndTlsRules() {
        val network = ClassFileImporter().importClasses(NetworkOfItsOwn::class.java)

        assertThatThrownBy { ONLY_THE_HTTP_GET_OPENS_CONNECTIONS.check(network) }
            .isInstanceOf(AssertionError::class.java)
            .hasMessageContaining("URL.openStream()")
        assertThatThrownBy { NO_CLASS_CHANGES_HOW_TLS_IS_CHECKED.check(network) }
            .isInstanceOf(AssertionError::class.java)
            .hasMessageContaining("X509TrustManager")
            .hasMessageContaining("SSLContext.init(")
            .hasMessageContaining("Security.addProvider(")
        assertThatThrownBy { NO_CLASS_CHANGES_THE_NETWORK_OF_THE_APP.check(network) }
            .isInstanceOf(AssertionError::class.java)
            .hasMessageContaining("ProxySelector.setDefault(")
            .hasMessageContaining("System.setProperty(")
            .hasMessageContaining("System.getProperties(")
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

    @Test
    fun theDashboardModelsRuleSeesTheModelsButNotTheEntries() {
        assertThat(DASHBOARD_MODELS.test(APP_CLASSES.get("$APP.home.TripAdviceModel"))).`as`("the trip advice")
            .isTrue()
        assertThat(DASHBOARD_MODELS.test(APP_CLASSES.get("$APP.home.TripAdviceModel\$Plan\$Grid")))
            .`as`("a class inside it").isTrue()
        assertThat(DASHBOARD_MODELS.test(APP_CLASSES.get("$APP.home.Dashboard"))).`as`("the dashboard").isTrue()
        assertThat(DASHBOARD_MODELS.test(APP_CLASSES.get("$APP.home.BatteryEntry"))).`as`("an entry").isFalse()
    }

    @Test
    fun everyFileOfTheDashboardsPackageIsNamedAModelOrNot() {
        val files = APP_CLASSES.filter { it.packageName == "$APP.home" }
            .mapNotNull { type -> type.source.flatMap { it.fileName }.orElse(null) }.toSet()

        assertThat(files).`as`("a new file in home/ goes on MODEL_FILES or NOT_MODEL_FILES")
            .isEqualTo(MODEL_FILES + NOT_MODEL_FILES)
    }

    @Test
    fun onlyWaitingSleepsInTheTests() {
        assertThat(TEST_CLASSES.map { it.name }).`as`("the tests are imported").contains("$APP.Waiting")

        noClasses().that().doNotHaveFullyQualifiedName("$APP.Waiting")
            .should().callMethod(Thread::class.java, "sleep", Long::class.javaPrimitiveType)
            .orShould().callMethod(Thread::class.java, "sleep", Long::class.javaPrimitiveType,
                Int::class.javaPrimitiveType)
            .orShould().callMethod(Thread::class.java, "sleep", java.time.Duration::class.java)
            .because("a test waits for a signal or polls through Waiting, and sets the clock; a sleep is slow when it "
                + "is long and flaky when it is short (CONTRIBUTING.md, ADR 0005, as the desktop's rule has it); the "
                + "instrumented tests, which run on a device, are not in this rule")
            .check(TEST_CLASSES)
    }

    @Test
    fun theAndroidRuleSeesAndroidXButNotTheComposeCompilersMark() {
        val importer = ClassFileImporter()

        assertThat(ANDROID.test(importer.importClass(SavedStateHandle::class.java))).`as`("AndroidX").isTrue()
        assertThat(ANDROID.test(importer.importClass(Class.forName(COMPOSE_STABILITY)))).`as`("the mark").isFalse()
    }

    @Test
    fun theDashboardModelsKnowNothingOfAndroid() {
        noClasses().that(DASHBOARD_MODELS)
            .should().dependOnClassesThat(ANDROID)
            .because("they hold the dashboard's rules, which the desktop may share (#341); the words and the drawing "
                + "are the screens'")
            .check(APP_CLASSES)
    }
}

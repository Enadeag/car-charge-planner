package nl.enadeag.carchargeplanner.engine;

import static com.tngtech.archunit.lang.conditions.ArchConditions.have;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.util.Set;

/**
 * The rules of ADR 0001 about the engine's own parts: parts that depend one way, a pure planner, a domain model
 * without its file format, files written in one place, and no translations; and the rules of ADR 0010 that the engine
 * uses no {@code java.net.http}, which Android lacks, opens no connection of its own beside its HttpGet, leaves the
 * checks of TLS and the network settings of the process alone, logs
 * through slf4j, sets no POSIX file modes, which it leaves to the platform's FilePrivacy, reaches no keychain
 * through D-Bus or native calls, which it leaves to the platform's Keychain, and starts no programs and declares no
 * native methods, which each platform does for itself. They run when the engine is built alone;
 * the rules about the boundary with its clients, and those for every class, are in the desktop module's
 * {@code ArchitectureTest}, which sees both sides.
 */
@AnalyzeClasses(packages = "nl.enadeag.carchargeplanner.engine", importOptions = ImportOption.DoNotIncludeTests.class)
class EngineArchitectureTest {

    private static final String ENGINE = EngineArchitectureTest.class.getPackageName();

    @ArchTest
    static final ArchRule engineHasNoTranslations = noClasses()
            .that().resideInAPackage("..engine..")
            .should().dependOnClassesThat().haveFullyQualifiedName("java.util.ResourceBundle")
            .because("the engine reports kinds and details; a client says them in the owner's language");

    @ArchTest
    static final ArchRule theEngineDoesNotUseJavasHttpClient = noClasses()
            .that().resideInAPackage(ENGINE + "..")
            .should().dependOnClassesThat().resideInAPackage("java.net.http..")
            .because("Android has no java.net.http; each platform hands the engine an HttpGet of its own (ADR 0010)");

    /** The same list as the Android app's {@code AppArchitectureTest}, without Android's own classes. */
    private static final Set<String> NETWORK_TYPES = Set.of(
            "java.net.URLConnection", "java.net.HttpURLConnection", "java.net.JarURLConnection",
            "javax.net.ssl.HttpsURLConnection",
            "java.net.Socket", "java.net.ServerSocket", "java.net.DatagramSocket", "java.net.MulticastSocket",
            "javax.net.SocketFactory", "javax.net.ssl.SSLSocketFactory", "javax.net.ssl.SSLSocket",
            "java.nio.channels.SocketChannel", "java.nio.channels.ServerSocketChannel",
            "java.nio.channels.DatagramChannel", "java.nio.channels.AsynchronousSocketChannel",
            "java.nio.channels.AsynchronousServerSocketChannel");

    private static final DescribedPredicate<JavaClass> OPEN_A_CONNECTION = DescribedPredicate.describe(
            "open network connections (URLConnection, sockets, socket channels)",
            type -> NETWORK_TYPES.contains(type.getName()));

    // an access, not only a call, so a method reference such as url::openStream is caught as well
    private static final DescribedPredicate<JavaAccess<?>> CONNECT_THROUGH_A_URL = DescribedPredicate.describe(
            "connect through URL.openConnection, openStream or getContent",
            access -> access.getTargetOwner().getName().equals("java.net.URL")
                    && Set.of("openConnection", "openStream", "getContent").contains(access.getName()));

    @ArchTest
    static final ArchRule theEngineOpensNoConnectionOfItsOwn = noClasses()
            .that().resideInAPackage(ENGINE + "..")
            .should().dependOnClassesThat(OPEN_A_CONNECTION)
            .orShould().accessTargetWhere(CONNECT_THROUGH_A_URL)
            .because("every request goes through the HttpGet the platform hands in, which keeps its contract on "
                    + "redirects, timeouts and size (ADR 0010)");

    /** The types that check certificates and host names, as in the Android app's {@code AppArchitectureTest}. */
    private static final Set<String> TLS_CHECKS = Set.of(
            "javax.net.ssl.TrustManager", "javax.net.ssl.X509TrustManager", "javax.net.ssl.X509ExtendedTrustManager",
            "javax.net.ssl.TrustManagerFactory", "javax.net.ssl.HostnameVerifier");

    private static final DescribedPredicate<JavaClass> CHECK_TLS = DescribedPredicate.describe(
            "check certificates or host names (trust managers, host name verifiers)",
            type -> TLS_CHECKS.contains(type.getName()));

    private static final DescribedPredicate<JavaAccess<?>> TAMPER_WITH_TLS = DescribedPredicate.describe(
            "set up TLS of their own (SSLContext.init or setDefault, an HttpsURLConnection's socket factory or host "
                    + "name verifier)",
            access -> {
                var owner = access.getTargetOwner().getName();
                var name = access.getName();
                return owner.equals("javax.net.ssl.SSLContext") && Set.of("init", "setDefault").contains(name)
                        || owner.equals("javax.net.ssl.HttpsURLConnection") && Set.of("setSSLSocketFactory",
                                "setDefaultSSLSocketFactory", "setHostnameVerifier", "setDefaultHostnameVerifier")
                                .contains(name);
            });

    @ArchTest
    static final ArchRule theEngineLeavesTheChecksOfTlsAlone = noClasses()
            .that().resideInAPackage(ENGINE + "..")
            .should().dependOnClassesThat(CHECK_TLS)
            .orShould().accessTargetWhere(TAMPER_WITH_TLS)
            .because("the engine runs inside both apps, which trust the certificates and check the host names as "
                    + "their platform does; an own trust manager or host name verifier lets a forged certificate "
                    + "through (ADR 0010)");

    private static final DescribedPredicate<JavaAccess<?>> CHANGE_THE_NETWORK_OF_THE_PROCESS =
            DescribedPredicate.describe("change how every connection of the process works (the default proxy "
                    + "selector, authenticator, cookie handler or response cache, URL's stream handlers, "
                    + "URLConnection's content handlers, following redirects for every HttpURLConnection)",
                    access -> {
                        var owner = access.getTargetOwner().getName();
                        var name = access.getName();
                        return Set.of("java.net.ProxySelector", "java.net.Authenticator", "java.net.CookieHandler",
                                "java.net.ResponseCache").contains(owner) && name.equals("setDefault")
                                || owner.equals("java.net.URL") && name.equals("setURLStreamHandlerFactory")
                                || owner.equals("java.net.URLConnection") && name.equals("setContentHandlerFactory")
                                || owner.equals("java.net.HttpURLConnection") && name.equals("setFollowRedirects");
                    });

    @ArchTest
    static final ArchRule theEngineLeavesTheNetworkOfTheProcessAlone = noClasses()
            .that().resideInAPackage(ENGINE + "..")
            .should().accessTargetWhere(CHANGE_THE_NETWORK_OF_THE_PROCESS)
            .because("such a setting reaches every connection of the app the engine runs in, its HttpGet's too, "
                    + "around the contract that keeps on redirects and what it sends (ADR 0010)");

    @ArchTest
    static final ArchRule theEngineLogsThroughSlf4j = noClasses()
            .that().resideInAPackage(ENGINE + "..")
            .should().dependOnClassesThat().haveFullyQualifiedName("java.lang.System$Logger")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("java.lang.System$Logger$Level")
            .orShould().callMethod(System.class, "getLogger", String.class)
            .orShould().callMethod(System.class, "getLogger", String.class, java.util.ResourceBundle.class)
            .because("the engine logs through slf4j, which each platform binds to its own log, instead of "
                    + "System.Logger (ADR 0010)");

    private static final DescribedPredicate<JavaClass> POSIX_FILE_ATTRIBUTES = DescribedPredicate.describe(
            "are POSIX file attributes, permissions or views (java.nio.file.attribute.Posix*)",
            type -> type.getPackageName().equals("java.nio.file.attribute")
                    && type.getSimpleName().startsWith("Posix"));

    // a call such as Files.setPosixFilePermissions(p, Files.getPosixFilePermissions(q)) names no Posix type
    private static final DescribedPredicate<JavaMethodCall> USE_POSIX_FILES_METHODS = DescribedPredicate.describe(
            "read or set POSIX permissions through Files",
            call -> call.getTargetOwner().getName().equals("java.nio.file.Files") && call.getName().contains("Posix"));

    @ArchTest
    static final ArchRule theEngineLeavesFilePrivacyToThePlatform = noClasses()
            .that().resideInAPackage(ENGINE + "..")
            .should().dependOnClassesThat(POSIX_FILE_ATTRIBUTES)
            .orShould().callMethodWhere(USE_POSIX_FILES_METHODS)
            .because("Android may have no POSIX file attributes and keeps its app storage private already; each "
                    + "platform makes a file private through the FilePrivacy it hands in (ADR 0010)");

    // the engine's pom has no such library and compiles with --release 17, which has no java.lang.foreign: this rule
    // is the fence behind those, for a dependency or a release that would let one in
    @ArchTest
    static final ArchRule theEngineReachesNoKeychainOfItsOwn = noClasses()
            .that().resideInAPackage(ENGINE + "..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.freedesktop..", "java.lang.foreign..", "com.sun.jna..", "com.github.hypfvieh..")
            .because("each platform reaches its own keychain (D-Bus, native calls, the Android Keystore) and hands "
                    + "the engine a Keychain; the engine keeps its secrets only through that (ADR 0010)");

    // an access, not only a call, so a method reference such as Runtime.getRuntime()::exec is caught as well
    private static final DescribedPredicate<JavaAccess<?>> RUN_A_PROGRAM = DescribedPredicate.describe(
            "start a program through Runtime.exec",
            access -> access.getTargetOwner().isEquivalentTo(Runtime.class) && access.getName().equals("exec"));

    private static final DescribedPredicate<JavaClass> NATIVE_METHODS = DescribedPredicate.describe(
            "native methods",
            type -> type.getMethods().stream().anyMatch(method -> method.getModifiers().contains(JavaModifier.NATIVE)));

    @ArchTest
    static final ArchRule theEngineStartsNoProgramsAndCallsNoNativeCode = noClasses()
            .that().resideInAPackage(ENGINE + "..")
            .should().dependOnClassesThat().haveFullyQualifiedName("java.lang.ProcessBuilder")
            .orShould().accessTargetWhere(RUN_A_PROGRAM)
            .orShould(have(NATIVE_METHODS))
            .because("the engine starts no programs and calls no native code, which Android does otherwise or "
                    + "not at all; each platform does that for itself, behind a seam it hands in (ADR 0010)");

    private static final DescribedPredicate<JavaClass> DOMAIN_MODEL = DescribedPredicate.describe(
            "are the domain model: the planner, the settings, and the engine's public records and enums",
            type -> type.getPackageName().endsWith(".engine.plan") || type.getPackageName().endsWith(".engine.setting")
                    || type.getPackageName().contains(".engine") && !type.getPackageName().endsWith(".engine.store")
                    && (type.isRecord() || type.isEnum()) && type.getModifiers().contains(JavaModifier.PUBLIC));

    @ArchTest
    static final ArchRule theDomainModelDoesNotKnowItsFileFormat = noClasses()
            .that(DOMAIN_MODEL)
            .should().dependOnClassesThat().resideInAnyPackage("tools.jackson..", "com.fasterxml.jackson..")
            .because("each stored file reads and writes its own JSON by hand, beside the store that keeps it");

    @ArchTest
    static final ArchRule noCyclesBetweenTheEngineParts = slices()
            .matching(ENGINE + ".(*)..")
            .should().beFreeOfCycles()
            .because("the engine's parts depend one way, so each can be read and tested alone (ADR 0001)");

    @ArchTest
    static final ArchRule engineSubPackagesDoNotReachUpIntoTheEngine = noClasses()
            .that().resideInAPackage(ENGINE + "..")
            .and().resideOutsideOfPackage(ENGINE)
            .should().dependOnClassesThat().resideInAPackage(ENGINE)
            .because("Engine and Outlook put the parts together; a part knowing them would be a cycle (ADR 0001)");

    @ArchTest
    static final ArchRule thePlanDoesNotKnowWhereItsInputsComeFrom = noClasses()
            .that().resideInAPackage(ENGINE + ".plan..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    ENGINE + ".forecast.providers..", ENGINE + ".secrets..", ENGINE + ".notify..")
            .because("the planner is pure: a forecast goes in and windows come out (ADR 0001)");

    private static final DescribedPredicate<JavaMethodCall> CHANGE_FILES = DescribedPredicate.describe(
            "write, move or delete a file",
            call -> call.getTargetOwner().isEquivalentTo(java.nio.file.Files.class)
                    && Set.of("write", "writeString", "newOutputStream", "newBufferedWriter", "createFile",
                            "createTempFile", "createTempDirectory", "createDirectory", "createDirectories", "move",
                            "copy", "delete", "deleteIfExists", "newByteChannel", "setOwner", "setAttribute",
                            "getFileAttributeView", "createSymbolicLink", "createLink").contains(call.getName()));

    @ArchTest
    static final ArchRule theEngineWritesFilesOnlyInItsStore = noClasses()
            .that().resideInAPackage(ENGINE + "..")
            .and().resideOutsideOfPackage(ENGINE + ".store..")
            .should().callMethodWhere(CHANGE_FILES)
            .orShould().dependOnClassesThat().haveFullyQualifiedName("java.io.FileOutputStream")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("java.io.FileWriter")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("java.io.RandomAccessFile")
            .orShould().dependOnClassesThat().resideInAPackage("java.nio.channels..")
            .because("the engine owns its files and keeps each written in one safe way, by StoredFile and "
                    + "PrivateFiles (ADR 0001)");
}

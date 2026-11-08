package nl.enadeag.carchargeplanner.engine;

import static com.tngtech.archunit.lang.conditions.ArchConditions.have;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noCodeUnits;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.AccessTarget;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.time.Clock;
import java.util.Arrays;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastProvider;
import nl.enadeag.carchargeplanner.engine.forecast.HttpGet;
import nl.enadeag.carchargeplanner.engine.store.StoredFile;

/**
 * The rules of ADR 0001 about the engine's own parts: parts that depend one way, a pure planner, a domain model
 * without its file format, files written in one place, and no translations; and the rules of ADR 0010 that the engine
 * uses no {@code java.net.http}, which Android lacks, opens no connection of its own beside its HttpGet, leaves the
 * checks of TLS and the network settings of the process alone, logs
 * through slf4j, sets no POSIX file modes, which it leaves to the platform's FilePrivacy, reaches no keychain
 * through D-Bus or native calls, which it leaves to the platform's Keychain, and starts no programs and declares no
 * native methods, which each platform does for itself. And the rules of ADR 0005 for the engine's own classes, which
 * the desktop's {@code ArchitectureTest} holds for the desktop's: no clock, sleep, thread or static mutable state of
 * its own, no request address in a log, no caught {@code NullPointerException}, no {@code Optional} field or
 * parameter, and every provider in its package. They run when the engine is built alone; the rules about the boundary
 * with its clients, and those for every class, are in the desktop module's {@code ArchitectureTest}, which sees both
 * sides.
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
                    + "name verifier, the security providers or properties)",
            access -> {
                var owner = access.getTargetOwner().getName();
                var name = access.getName();
                return owner.equals("javax.net.ssl.SSLContext") && Set.of("init", "setDefault").contains(name)
                        || owner.equals("javax.net.ssl.HttpsURLConnection") && Set.of("setSSLSocketFactory",
                                "setDefaultSSLSocketFactory", "setHostnameVerifier", "setDefaultHostnameVerifier")
                                .contains(name)
                        || owner.equals("java.security.Security") && Set.of("setProperty", "addProvider",
                                "insertProviderAt", "removeProvider").contains(name);
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
                    + "URLConnection's content handlers, following redirects for every HttpURLConnection, a system "
                    + "property, as ArchUnit cannot read which and the TLS and proxy ones reach every connection)",
                    access -> {
                        var owner = access.getTargetOwner().getName();
                        var name = access.getName();
                        return Set.of("java.net.ProxySelector", "java.net.Authenticator", "java.net.CookieHandler",
                                "java.net.ResponseCache").contains(owner) && name.equals("setDefault")
                                || owner.equals("java.net.URL") && name.equals("setURLStreamHandlerFactory")
                                || owner.equals("java.net.URLConnection") && name.equals("setContentHandlerFactory")
                                || owner.equals("java.net.HttpURLConnection") && name.equals("setFollowRedirects")
                                || owner.equals("java.lang.System")
                                && Set.of("setProperty", "setProperties", "clearProperty").contains(name);
                    });

    @ArchTest
    static final ArchRule theEngineLeavesTheNetworkOfTheProcessAlone = noClasses()
            .that().resideInAPackage(ENGINE + "..")
            .should().accessTargetWhere(CHANGE_THE_NETWORK_OF_THE_PROCESS)
            .because("such a setting reaches every connection of the app the engine runs in, its HttpGet's too, "
                    + "around the contract that keeps on redirects and what it sends (ADR 0010)");

    // the rules of ADR 0005 that the desktop's ArchitectureTest holds for its own classes, here for the engine's

    /** The call names the exception's own class, a subclass of Throwable, whichever overload is called. */
    private static final DescribedPredicate<JavaMethodCall> PRINT_A_STACK_TRACE = DescribedPredicate.describe(
            "print a stack trace",
            call -> call.getName().equals("printStackTrace") && call.getTargetOwner().isAssignableTo(Throwable.class));

    private static final DescribedPredicate<JavaClass> HANDLE_REQUEST_ADDRESSES = DescribedPredicate.describe(
            "make or read the address of a request to a provider",
            type -> type.getAccessesFromSelf().stream()
                    .anyMatch(access -> access.getTargetOwner().isEquivalentTo(HttpGet.Request.class)));

    @ArchTest
    static final ArchRule requestAddressesNeverReachALog = noClasses()
            .that().resideInAPackage(ENGINE + "..").and(HANDLE_REQUEST_ADDRESSES)
            .should().dependOnClassesThat().haveFullyQualifiedName(System.Logger.class.getName())
            .orShould().dependOnClassesThat().resideInAnyPackage("java.util.logging..", "org.slf4j..")
            .orShould().accessField(System.class, "out")
            .orShould().accessField(System.class, "err")
            .orShould().callMethodWhere(PRINT_A_STACK_TRACE)
            .because("an address can hold an API key and the home's coordinates, so what makes or sends one logs "
                    + "nothing; ProviderConformance checks what the providers' errors say");

    // an access, not only a call, so a method reference such as Clock::systemUTC is caught as well
    private static final DescribedPredicate<JavaAccess<?>> READ_THE_SYSTEM_CLOCK = DescribedPredicate.describe(
            "read the system's clock or zone",
            access -> {
                var owner = access.getTargetOwner().getName();
                var name = access.getName();
                var takesAClock = access.getTarget() instanceof AccessTarget.CodeUnitAccessTarget target
                        && target.getRawParameterTypes().stream().anyMatch(type -> type.isEquivalentTo(Clock.class));
                return name.equals("now") && owner.startsWith("java.time.") && !takesAClock
                        || owner.equals(Clock.class.getName()) && Set.of("systemUTC", "systemDefaultZone", "system")
                                .contains(name)
                        || owner.equals("java.time.ZoneId") && name.equals("systemDefault")
                        || owner.equals("java.util.TimeZone") && name.equals("getDefault")
                        || owner.equals(System.class.getName()) && name.equals("currentTimeMillis");
            });

    /**
     * The one default clock left: the time a file that cannot be used is set aside with, until its callers pass one.
     */
    private static final DescribedPredicate<JavaAccess<?>> STORED_FILES_DEFAULT = DescribedPredicate.describe(
            "are not StoredFile's constructor without a clock",
            access -> access.getOrigin().getOwner().isEquivalentTo(StoredFile.class)
                    && access.getOrigin().isConstructor()
                    && access.getOrigin().getRawParameterTypes().size() == 3);

    @ArchTest
    static final ArchRule theEngineReadsNoClock = noClasses()
            .that().resideInAPackage(ENGINE + "..")
            .should().accessTargetWhere(READ_THE_SYSTEM_CLOCK.and(DescribedPredicate.not(STORED_FILES_DEFAULT)))
            .because("each app hands the engine its clock and the home's zone, so a test can set the time (ADR "
                    + "0005); StoredFile's constructor without a clock, which the engine's files use, is the one "
                    + "default left, for the name of a file set aside");

    @ArchTest
    static final ArchRule theEngineDoesNotSleep = noClasses()
            .that().resideInAPackage(ENGINE + "..")
            .should().callMethod(Thread.class, "sleep", long.class)
            .orShould().callMethod(Thread.class, "sleep", long.class, int.class)
            .orShould().callMethod(Thread.class, "sleep", java.time.Duration.class)
            .because("waiting is done with a signal or a deadline that is handed in (ADR 0005)");

    private static final DescribedPredicate<JavaMethodCall> START_A_THREAD = DescribedPredicate.describe(
            "start a thread or make an executor",
            call -> {
                var owner = call.getTargetOwner();
                return owner.isEquivalentTo(Thread.class) && Set.of("ofVirtual", "ofPlatform", "startVirtualThread")
                        .contains(call.getName())
                        || owner.isEquivalentTo(java.util.concurrent.Executors.class)
                        || owner.isEquivalentTo(java.util.concurrent.ForkJoinPool.class)
                                && call.getName().equals("commonPool");
            });

    @ArchTest
    static final ArchRule theEngineOwnsNoThreads = noClasses()
            .that().resideInAPackage(ENGINE + "..")
            .should().callMethodWhere(START_A_THREAD)
            .orShould().callConstructorWhere(DescribedPredicate.describe("make a thread, a timer or a pool",
                    call -> call.getTargetOwner().isAssignableTo(Thread.class)
                            || call.getTargetOwner().isAssignableTo(java.util.Timer.class)
                            || call.getTargetOwner().isAssignableTo(java.util.concurrent.ThreadPoolExecutor.class)
                            || call.getTargetOwner().isAssignableTo(java.util.concurrent.ForkJoinPool.class)))
            .because("each app hands the engine the executor it runs on (ADR 0005, ADR 0010)");

    @ArchTest
    static final ArchRule theEngineHasNoStaticMutableState = fields()
            .that().areStatic()
            .and().areDeclaredInClassesThat().resideInAPackage(ENGINE + "..")
            .should().beFinal()
            .because("dependencies come in through constructors, never through global state (ADR 0005)");

    /** The calls of {@code java.util} that hand back a mutable collection or map. */
    private static final DescribedPredicate<JavaMethodCall> MAKE_A_MUTABLE_COLLECTION = DescribedPredicate.describe(
            "make a mutable collection or map",
            call -> {
                var owner = call.getTargetOwner().getName();
                var name = call.getName();
                return owner.equals("java.util.Collections") && (name.startsWith("synchronized")
                        || name.startsWith("checked") || name.equals("newSetFromMap"))
                        || owner.equals("java.util.concurrent.ConcurrentHashMap") && name.equals("newKeySet");
            });

    /**
     * Whether a static field of a collection or map interface gets a mutable one where its class's static initializer
     * sets it. Of the collections and maps the initializer makes after the line that sets the static field before it,
     * up to the line that sets this one, the last line's decide: a mutable one made there (a constructor, or a call
     * of {@link #MAKE_A_MUTABLE_COLLECTION}) and no other call there that hands back a collection or map, such as
     * {@code Map.copyOf}. ArchUnit gives no order within a line, so {@code new ArrayList<>(List.of(...))} on one line
     * is not seen.
     */
    private static boolean madeMutable(JavaField field) {
        var type = field.getRawType();
        var initializer = field.getOwner().getStaticInitializer();
        if (!type.isInterface() || initializer.isEmpty() || !isCollectionOrMap(type)) {
            return false;
        }
        var sets = initializer.get().getFieldAccesses().stream()
                .filter(access -> access.getAccessType() == JavaFieldAccess.AccessType.SET
                        && access.getTargetOwner().equals(field.getOwner()))
                .toList();
        var line = sets.stream().filter(access -> access.getName().equals(field.getName()))
                .mapToInt(JavaAccess::getLineNumber).max();
        if (line.isEmpty()) {
            return false;
        }
        int after = sets.stream().mapToInt(JavaAccess::getLineNumber)
                .filter(set -> set < line.getAsInt()).max().orElse(0);
        var mutable = java.util.stream.Stream.concat(
                initializer.get().getConstructorCallsFromSelf().stream()
                        .filter(call -> !call.getTargetOwner().isInterface()
                                && isCollectionOrMap(call.getTargetOwner())),
                initializer.get().getMethodCallsFromSelf().stream().filter(MAKE_A_MUTABLE_COLLECTION))
                .map(JavaAccess::getLineNumber).toList();
        var others = initializer.get().getMethodCallsFromSelf().stream()
                .filter(call -> !MAKE_A_MUTABLE_COLLECTION.test(call)
                        && isCollectionOrMap(call.getTarget().getRawReturnType()))
                .map(JavaAccess::getLineNumber).toList();
        var last = java.util.stream.Stream.concat(mutable.stream(), others.stream())
                .filter(at -> at > after && at <= line.getAsInt()).mapToInt(Integer::intValue).max();
        return last.isPresent() && mutable.contains(last.getAsInt()) && !others.contains(last.getAsInt());
    }

    private static boolean isCollectionOrMap(JavaClass type) {
        return type.isAssignableTo(java.util.Collection.class) || type.isAssignableTo(java.util.Map.class);
    }

    private static final DescribedPredicate<JavaField> HOLDS_A_MUTABLE_OBJECT = DescribedPredicate.describe(
            "hold a mutable collection (also one of an interface type, such as a List, given a new ArrayList where it "
                    + "is set), an atomic, a builder or an array",
            field -> {
                var type = field.getRawType();
                var concrete = !type.isInterface();
                return type.isArray()
                        || type.getPackageName().startsWith("java.util.concurrent.atomic")
                        || type.isAssignableTo(StringBuilder.class) || type.isAssignableTo(StringBuffer.class)
                        || concrete && (type.isAssignableTo(java.util.Collection.class)
                                || type.isAssignableTo(java.util.Map.class))
                        || type.isAssignableTo(java.util.Queue.class)
                        || type.isAssignableTo(java.util.concurrent.ConcurrentMap.class)
                        || madeMutable(field);
            });

    @ArchTest
    static final ArchRule theEngineHasNoStaticMutableObjects = noFields()
            .that().areStatic()
            .and().doNotHaveModifier(JavaModifier.SYNTHETIC)
            .and().areDeclaredInClassesThat().resideInAPackage(ENGINE + "..")
            .should(have(HOLDS_A_MUTABLE_OBJECT))
            .because("a final field is still global state when what it holds can change (ADR 0005); ArchUnit sees "
                    + "what a field of an interface type holds only where the static initializer makes it, not a "
                    + "mutable one a method hands back, such as Collectors.toList's");

    private static final ArchCondition<JavaCodeUnit> CATCH_NULL_POINTER =
            new ArchCondition<>("catch NullPointerException") {
                @Override
                public void check(JavaCodeUnit codeUnit, ConditionEvents events) {
                    for (var block : codeUnit.getTryCatchBlocks()) {
                        if (block.getCaughtThrowables().stream()
                                .anyMatch(caught -> caught.isEquivalentTo(NullPointerException.class))) {
                            events.add(SimpleConditionEvent.satisfied(block,
                                    codeUnit.getFullName() + " catches NullPointerException in "
                                            + block.getSourceCodeLocation()));
                        }
                    }
                }
            };

    @ArchTest
    static final ArchRule theEngineCatchesNoNullPointer = noCodeUnits()
            .that().areDeclaredInClassesThat().resideInAPackage(ENGINE + "..")
            .should(CATCH_NULL_POINTER)
            .because("a null is checked where it may come, not caught after it went wrong");

    /** A record's components are its fields too, and there an {@code Optional} is part of the data. */
    @ArchTest
    static final ArchRule theEngineHasNoOptionalFields = noFields()
            .that().doNotHaveModifier(JavaModifier.SYNTHETIC)
            .and().areDeclaredInClassesThat().resideInAPackage(ENGINE + "..")
            .and().areDeclaredInClassesThat().areNotRecords()
            .should().haveRawType(Optional.class)
            .because("a field holds a null object, a no-op callback or a private value that may be null, never an "
                    + "Optional (CONTRIBUTING)");

    private static final Set<Class<?>> OPTIONALS =
            Set.of(Optional.class, OptionalDouble.class, OptionalInt.class, OptionalLong.class);

    private static final DescribedPredicate<JavaCodeUnit> TAKES_AN_OPTIONAL = DescribedPredicate.describe(
            "an Optional parameter", codeUnit -> codeUnit.getRawParameterTypes().stream()
                    .anyMatch(type -> OPTIONALS.stream().anyMatch(type::isEquivalentTo)));

    /** A record's canonical constructor takes its components, and there an {@code Optional} is part of the data. */
    private static final DescribedPredicate<JavaCodeUnit> RECORD_CANONICAL_CONSTRUCTOR = DescribedPredicate.describe(
            "a record's canonical constructor", codeUnit -> codeUnit.isConstructor() && codeUnit.getOwner().isRecord()
                    && Arrays.equals(codeUnit.getRawParameterTypes().stream().map(JavaClass::getName).toArray(),
                            Arrays.stream(codeUnit.getOwner().reflect().getRecordComponents())
                                    .map(component -> component.getType().getName()).toArray()));

    @ArchTest
    static final ArchRule theEngineTakesNoOptionalParameters = noCodeUnits()
            .that().doNotHaveModifier(JavaModifier.SYNTHETIC)
            .and().areDeclaredInClassesThat().resideInAPackage(ENGINE + "..")
            .and(DescribedPredicate.not(RECORD_CANONICAL_CONSTRUCTOR))
            .should(have(TAKES_AN_OPTIONAL))
            .because("a method or constructor takes a value or a type that names the none case, or comes in two; "
                    + "Optional is for return values and record components (CONTRIBUTING)");

    @ArchTest
    static final ArchRule forecastProvidersAreInTheirPackage = classes()
            .that().implement(ForecastProvider.class)
            .and().areNotInterfaces()
            .should().resideInAPackage(ENGINE + ".forecast.providers..")
            .because("a provider is one class beside the others, so adding one is adding a file (ADR 0001)");

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

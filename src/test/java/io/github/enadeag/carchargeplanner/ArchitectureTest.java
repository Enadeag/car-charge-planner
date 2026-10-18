package io.github.enadeag.carchargeplanner;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.conditions.ArchConditions;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import io.github.enadeag.carchargeplanner.desktop.Notifications;
import io.github.enadeag.carchargeplanner.desktop.StartAtLogin;
import io.github.enadeag.carchargeplanner.desktop.Tray;
import io.github.enadeag.carchargeplanner.engine.forecast.ForecastProvider;
import io.github.enadeag.carchargeplanner.engine.forecast.HttpGet;
import io.github.enadeag.carchargeplanner.engine.forecast.JdkHttpGet;
import io.github.enadeag.carchargeplanner.engine.secrets.Keychain;
import io.github.enadeag.carchargeplanner.engine.secrets.NoKeychain;
import java.time.Clock;
import java.util.Locale;
import java.util.Set;

/**
 * The rules of ADR 0001 and ADR 0005: an engine without user interface, platforms that do not know each other, one
 * composition root that wires everything, and no global state, clocks, threads or system reads outside the places
 * that own them. A rule that older code still breaks is frozen in {@code src/test/archunit_store}: what is stored
 * there may be fixed, and nothing new may join it.
 */
@AnalyzeClasses(packages = "io.github.enadeag.carchargeplanner", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule engineHasNoUserInterfaceOrDesktopBus = noClasses()
            .that().resideInAPackage("..engine..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "javafx..", "java.awt..", "javax.swing..", "org.freedesktop..", "atlantafx..")
            .because("the engine also has to run on a machine without a desktop (ADR 0001)");

    @ArchTest
    static final ArchRule engineDoesNotKnowItsClients = noClasses()
            .that().resideInAPackage("..engine..")
            .should().dependOnClassesThat().resideInAnyPackage("..desktop..", "..platform..");

    @ArchTest
    static final ArchRule desktopDoesNotKnowThePlatforms = noClasses()
            .that().resideInAPackage("..desktop..")
            .should().dependOnClassesThat().resideInAPackage("..platform..")
            .because("the composition root picks the platform's parts");

    @ArchTest
    static final ArchRule singleInstanceKnowsOnlyTheJdk = noClasses()
            .that().resideInAPackage("..instance..")
            .should().dependOnClassesThat().resideOutsideOfPackages("java..", "jdk.net..", "..instance..")
            .because("the composition root wires the single-instance guard to the app; it runs before anything else");

    @ArchTest
    static final ArchRule engineHasNoTranslations = noClasses()
            .that().resideInAPackage("..engine..")
            .should().dependOnClassesThat().haveFullyQualifiedName("java.util.ResourceBundle")
            .because("the engine reports kinds and details; a client says them in the owner's language");

    @ArchTest
    static final ArchRule onlyTheCompositionRootReadsTheSystemLocale = noClasses()
            .that().doNotHaveFullyQualifiedName(Main.class.getName())
            .should().callMethod(Locale.class, "getDefault")
            .orShould().callMethod(Locale.class, "getDefault", Locale.Category.class)
            .orShould().callMethod(Locale.class, "setDefault", Locale.class)
            .orShould().callMethod(Locale.class, "setDefault", Locale.Category.class, Locale.class)
            .because("the chosen language is handed to whatever writes texts, numbers or times; the JVM's default is not");

    @ArchTest
    static final ArchRule onlyTheHostReadsTheSystem = noClasses()
            .that().doNotHaveFullyQualifiedName(Host.class.getName())
            .and().doNotHaveFullyQualifiedName(Os.class.getName())
            .should().callMethod(System.class, "getProperty", String.class)
            .orShould().callMethod(System.class, "getProperty", String.class, String.class)
            .orShould().callMethod(System.class, "getenv")
            .orShould().callMethod(System.class, "getenv", String.class)
            .because("the system and its user are read once, as the app starts, and handed on");

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

    /** The call names the exception's own class, a subclass of Throwable, whichever overload is called. */
    private static final DescribedPredicate<JavaMethodCall> PRINT_A_STACK_TRACE = DescribedPredicate.describe(
            "print a stack trace",
            call -> call.getName().equals("printStackTrace") && call.getTargetOwner().isAssignableTo(Throwable.class));

    private static final DescribedPredicate<JavaMethodCall> PRINT_A_STACK_TRACE_TO_THE_CONSOLE =
            DescribedPredicate.describe("print a stack trace to the console, taking no argument",
                    call -> PRINT_A_STACK_TRACE.test(call) && call.getTarget().getRawParameterTypes().isEmpty());

    private static final DescribedPredicate<JavaClass> HANDLE_REQUEST_ADDRESSES = DescribedPredicate.describe(
            "make or read the address of a request to a provider",
            type -> type.getAccessesFromSelf().stream()
                    .anyMatch(access -> access.getTargetOwner().isEquivalentTo(HttpGet.Request.class)));

    @ArchTest
    static final ArchRule requestAddressesNeverReachALog = noClasses()
            .that(HANDLE_REQUEST_ADDRESSES)
            .should().dependOnClassesThat().haveFullyQualifiedName(System.Logger.class.getName())
            .orShould().dependOnClassesThat().resideInAnyPackage("java.util.logging..", "org.slf4j..")
            .orShould().accessField(System.class, "out")
            .orShould().accessField(System.class, "err")
            .orShould().callMethodWhere(PRINT_A_STACK_TRACE)
            .because("an address can hold an API key and the home's coordinates, so what makes or sends one logs "
                    + "nothing; ProviderConformance checks what the providers' errors say");

    @ArchTest
    static final ArchRule noStaticMutableState = fields()
            .that().areStatic()
            .and().areDeclaredInClassesThat().doNotHaveFullyQualifiedName(Main.class.getName())
            .should().beFinal()
            .because("dependencies come in through constructors, never through global state (ADR 0005)");

    private static final String ROOT = Main.class.getPackageName();

    private static final String ENGINE = ROOT + ".engine";

    // package structure

    @ArchTest
    static final ArchRule noCyclesBetweenTheTopLevelPackages = slices()
            .matching(ROOT + ".(*)..")
            .should().beFreeOfCycles()
            .because("the engine, the desktop, the platforms and the single-instance guard are built and read apart "
                    + "(ADR 0001, ADR 0005)");

    @ArchTest
    static final ArchRule noCyclesBetweenTheEngineParts = slices()
            .matching(ENGINE + ".(*)..")
            .should().beFreeOfCycles()
            .because("the engine's parts depend one way, so each can be read and tested alone (ADR 0001)");

    @ArchTest
    static final ArchRule noCyclesBetweenTheDesktopParts = slices()
            .matching(ROOT + ".desktop.(*)..")
            .should().beFreeOfCycles()
            .because("the desktop's parts depend one way, so each can be read and tested alone (ADR 0005)");

    @ArchTest
    static final ArchRule thePlatformsDoNotKnowEachOther = slices()
            .matching(ROOT + ".platform.(*)..")
            .should().notDependOnEachOther()
            .because("a platform's parts are picked by the composition root; none may need another platform's");

    @ArchTest
    static final ArchRule onlyTheCompositionRootDependsOnThePlatforms = noClasses()
            .that().resideOutsideOfPackages(ROOT, "..platform..")
            .should().dependOnClassesThat().resideInAPackage("..platform..")
            .because("the composition root picks the platform's parts and hands them on as the ports (ADR 0005)");

    @ArchTest
    static final ArchRule foreignMemoryIsOnlyForWindows = noClasses()
            .that().resideOutsideOfPackage("..platform.windows..")
            .should().dependOnClassesThat().resideInAPackage("java.lang.foreign..")
            .because("calls into Windows are the one place native memory is touched (ADR 0005)");

    @ArchTest
    static final ArchRule engineSubPackagesDoNotReachUpIntoTheEngine = noClasses()
            .that().resideInAPackage(ENGINE + "..")
            .and().resideOutsideOfPackage(ENGINE)
            .should().dependOnClassesThat().resideInAPackage(ENGINE)
            .because("Engine and Outlook put the parts together; a part knowing them would be a cycle (ADR 0001)");

    @ArchTest
    static final ArchRule engineAndDesktopDoNotKnowTheCompositionRoot = noClasses()
            .that().resideInAnyPackage("..engine..", "..desktop..")
            .should().dependOnClassesThat().resideInAPackage(ROOT)
            .because("the composition root wires them; they get what they need through their constructors (ADR 0005)");

    @ArchTest
    static final ArchRule providersAreOnlyReachedThroughTheForecast = noClasses()
            .that().resideOutsideOfPackages(ENGINE + ".forecast..", ROOT)
            .should().dependOnClassesThat().resideInAPackage(ENGINE + ".forecast.providers..")
            .because("a provider is chosen by engine.forecast; the plan and the clients know only the forecast");

    @ArchTest
    static final ArchRule thePlanDoesNotKnowWhereItsInputsComeFrom = noClasses()
            .that().resideInAPackage(ENGINE + ".plan..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    ENGINE + ".forecast.providers..", ENGINE + ".secrets..", ENGINE + ".notify..")
            .because("the planner is pure: a forecast goes in and windows come out (ADR 0001)");

    @ArchTest
    static final ArchRule theLayersOnlyPointDownwards = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Root").definedBy(ROOT)
            .layer("Platform").definedBy("..platform..")
            .layer("Desktop").definedBy("..desktop..")
            .layer("Instance").definedBy("..instance..")
            .layer("Engine").definedBy("..engine..")
            .whereLayer("Root").mayNotBeAccessedByAnyLayer()
            .whereLayer("Platform").mayOnlyBeAccessedByLayers("Root")
            .whereLayer("Desktop").mayOnlyBeAccessedByLayers("Root", "Platform")
            .whereLayer("Instance").mayOnlyBeAccessedByLayers("Root")
            .whereLayer("Engine").mayOnlyBeAccessedByLayers("Root", "Platform", "Desktop")
            .because("the composition root sits on the platforms, which implement the desktop's ports, which sit on "
                    + "the engine; the single-instance guard is used by the root alone (ADR 0001, ADR 0005)");

    // clocks, threads, logging and output

    private static final DescribedPredicate<JavaMethodCall> READ_THE_SYSTEM_CLOCK = DescribedPredicate.describe(
            "read the system's clock or zone",
            call -> {
                var owner = call.getTargetOwner().getName();
                var name = call.getName();
                var takesAClock = call.getTarget().getRawParameterTypes().stream()
                        .anyMatch(type -> type.isEquivalentTo(Clock.class));
                return name.equals("now") && owner.startsWith("java.time.") && !takesAClock
                        || owner.equals(Clock.class.getName()) && Set.of("systemUTC", "systemDefaultZone", "system")
                                .contains(name)
                        || owner.equals("java.time.ZoneId") && name.equals("systemDefault")
                        || owner.equals("java.util.TimeZone") && name.equals("getDefault")
                        || owner.equals(System.class.getName()) && name.equals("currentTimeMillis");
            });

    @ArchTest
    static final ArchRule onlyTheCompositionRootReadsTheClock = FreezingArchRule.freeze(noClasses()
            .that().resideOutsideOfPackage(ROOT)
            .should().callMethodWhere(READ_THE_SYSTEM_CLOCK)
            .because("the clock and the zone are handed in, so a test can set the time (ADR 0005); the stored "
                    + "defaults of StoredFile and ApiKeys are frozen until their callers pass a clock"));

    @ArchTest
    static final ArchRule nothingSleepsInMainCode = FreezingArchRule.freeze(noClasses()
            .should().callMethod(Thread.class, "sleep", long.class)
            .orShould().callMethod(Thread.class, "sleep", long.class, int.class)
            .orShould().callMethod(Thread.class, "sleep", java.time.Duration.class)
            .because("waiting is done with a signal or a deadline that is handed in; the single-instance guard's "
                    + "pause between two looks at another copy's lock is frozen (ADR 0005)"));

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
    static final ArchRule onlyTheRootThePlatformsAndTheGuardOwnThreads = noClasses()
            .that().resideOutsideOfPackages(ROOT, "..platform..", "..instance..")
            .should().callMethodWhere(START_A_THREAD)
            .orShould().callConstructorWhere(DescribedPredicate.describe("make a thread, a timer or a pool",
                    call -> call.getTargetOwner().isAssignableTo(Thread.class)
                            || call.getTargetOwner().isAssignableTo(java.util.Timer.class)
                            || call.getTargetOwner().isAssignableTo(java.util.concurrent.ThreadPoolExecutor.class)
                            || call.getTargetOwner().isAssignableTo(java.util.concurrent.ForkJoinPool.class)))
            .because("the engine and the desktop get their executors from the composition root (ADR 0005); the "
                    + "platforms and the single-instance guard run the calls and the listener they own");

    @ArchTest
    static final ArchRule nothingPrintsAStackTraceToTheConsole = noClasses()
            .should().callMethodWhere(PRINT_A_STACK_TRACE_TO_THE_CONSOLE)
            .because("the console is for Main's answers; no class, not even LogLine, writes a trace there "
                    + "(ADR 0005)");

    @ArchTest
    static final ArchRule onlyTheLogLinePrintsAStackTrace = noClasses()
            .that().doNotHaveFullyQualifiedName(LogLine.class.getName())
            .should().callMethodWhere(PRINT_A_STACK_TRACE)
            .because("a failure goes to the log, which knows what it may show; only LogLine, which writes the log, "
                    + "prints a trace, to a writer of its own (ADR 0005)");

    @ArchTest
    static final ArchRule onlyTheRootConfiguresJavaUtilLogging = noClasses()
            .that().resideOutsideOfPackage(ROOT)
            .should().dependOnClassesThat().resideInAPackage("java.util.logging..")
            .because("the app's log is set up once, by AppLog; everything else logs through System.Logger (ADR 0005)");

    @ArchTest
    static final ArchRule onlyTheRootWritesToTheConsole = noClasses()
            .that().resideOutsideOfPackage(ROOT)
            .should().accessField(System.class, "out")
            .orShould().accessField(System.class, "err")
            .because("the command line's answers are printed by Main; the rest logs (ADR 0005)");

    // what is confined to one place

    private static DescribedPredicate<JavaClass> notPartOf(String topLevelClassName) {
        return DescribedPredicate.describe("are not part of " + topLevelClassName,
                type -> !type.getName().equals(topLevelClassName) && !type.getName().startsWith(topLevelClassName + "$"));
    }

    @ArchTest
    static final ArchRule theDesktopBusIsOnlyForLinux = noClasses()
            .that().resideOutsideOfPackages("..platform.linux..", ROOT)
            .should().dependOnClassesThat().resideInAPackage("org.freedesktop..")
            .because("D-Bus is how the Linux platform talks to the tray, notifications and keyring (ADR 0005)");

    @ArchTest
    static final ArchRule onlyOneClassMakesHttpCalls = noClasses()
            .that().resideOutsideOfPackage(ROOT)
            .and(notPartOf(JdkHttpGet.class.getName()))
            .should().dependOnClassesThat().resideInAPackage("java.net.http..")
            .because("every provider asks through the HttpGet port, so a test can answer instead (ADR 0001)");

    @ArchTest
    static final ArchRule jacksonIsOnlyForStoredFiles = noClasses()
            .that().resideOutsideOfPackage(ENGINE + "..")
            .and(notPartOf(ROOT + ".desktop.AppSettingsFile"))
            .should().dependOnClassesThat().resideInAnyPackage("tools.jackson..", "com.fasterxml.jackson..")
            .because("the engine's files and the desktop's app settings are the only JSON the app reads and writes "
                    + "(ADR 0001)");

    @ArchTest
    static final ArchRule theDesktopWritesFilesOnlyForItsAppSettings = noClasses()
            .that().resideInAPackage("..desktop..")
            .and().doNotHaveFullyQualifiedName(ROOT + ".desktop.AppSettingsFile")
            .should().dependOnClassesThat().haveFullyQualifiedName("java.nio.file.Files")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("java.io.FileOutputStream")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("java.io.FileWriter")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("java.io.RandomAccessFile")
            .orShould().dependOnClassesThat().resideInAPackage("java.nio.channels..")
            .because("no client writes the engine's files; the desktop keeps its own app settings (ADR 0001)");

    private static final DescribedPredicate<JavaMethodCall> CHANGE_FILES = DescribedPredicate.describe(
            "write, move or delete a file",
            call -> call.getTargetOwner().isEquivalentTo(java.nio.file.Files.class)
                    && Set.of("write", "writeString", "newOutputStream", "newBufferedWriter", "createFile",
                            "createTempFile", "createTempDirectory", "createDirectory", "createDirectories", "move",
                            "copy", "delete", "deleteIfExists", "newByteChannel", "setPosixFilePermissions",
                            "setOwner", "setAttribute", "getFileAttributeView", "createSymbolicLink",
                            "createLink").contains(call.getName()));

    @ArchTest
    static final ArchRule theEngineWritesFilesOnlyInItsStore = noClasses()
            .that().resideInAPackage(ENGINE + "..")
            .and().resideOutsideOfPackage(ENGINE + ".store..")
            // SecretsFile.makePrivate() tightens the permissions of the fallback secrets file, nothing more
            .and().doNotHaveFullyQualifiedName(ENGINE + ".secrets.SecretsFile")
            .should().callMethodWhere(CHANGE_FILES)
            .orShould().dependOnClassesThat().haveFullyQualifiedName("java.io.FileOutputStream")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("java.io.FileWriter")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("java.io.RandomAccessFile")
            .orShould().dependOnClassesThat().resideInAPackage("java.nio.channels..")
            .because("the engine owns its files and keeps each written in one safe way, by StoredFile and "
                    + "PrivateFiles (ADR 0001)");

    // texts, numbers and times are written in a chosen language

    private static boolean hasLocale(java.util.List<JavaClass> types) {
        return types.stream().anyMatch(type -> type.isEquivalentTo(Locale.class)
                || type.isEquivalentTo(Locale.Category.class)
                || type.getName().equals("java.text.DecimalFormatSymbols")
                || type.getName().equals("java.text.DateFormatSymbols"));
    }

    private static final DescribedPredicate<JavaMethodCall> USE_THE_DEFAULT_LOCALE = DescribedPredicate.describe(
            "format or change case in the JVM's default locale",
            call -> {
                var ownerType = call.getTargetOwner();
                var owner = ownerType.getName();
                var name = call.getName();
                var types = call.getTarget().getRawParameterTypes();
                var hasLocale = hasLocale(types);
                return owner.equals(String.class.getName()) && !hasLocale
                                && Set.of("toLowerCase", "toUpperCase", "format", "formatted").contains(name)
                        || (owner.equals("java.text.NumberFormat") || owner.equals("java.text.DateFormat"))
                                && name.endsWith("Instance") && !hasLocale
                        || owner.equals("java.time.format.DateTimeFormatter") && name.equals("ofPattern")
                                && types.size() == 1
                        || (ownerType.isAssignableTo(java.io.PrintStream.class)
                                || ownerType.isAssignableTo(java.io.PrintWriter.class)
                                || owner.equals("java.util.Formatter"))
                                && Set.of("printf", "format").contains(name) && !hasLocale;
            });

    private static final DescribedPredicate<com.tngtech.archunit.core.domain.JavaConstructorCall>
            MAKE_A_FORMAT_IN_THE_DEFAULT_LOCALE = DescribedPredicate.describe(
                    "make a format that uses the JVM's default locale",
                    call -> Set.of("java.text.SimpleDateFormat", "java.text.DecimalFormat",
                            "java.text.MessageFormat", "java.util.Formatter")
                            .contains(call.getTargetOwner().getName())
                            && !hasLocale(call.getTarget().getRawParameterTypes()));

    @ArchTest
    static final ArchRule textsNumbersAndTimesNameTheirLocale = noClasses()
            .should().callMethodWhere(USE_THE_DEFAULT_LOCALE)
            .orShould().callConstructorWhere(MAKE_A_FORMAT_IN_THE_DEFAULT_LOCALE)
            .because("the chosen language is handed to whatever writes them; use Locale.ROOT for what a machine "
                    + "reads (ADR 0005)");

    // global state

    private static final DescribedPredicate<JavaField> HOLDS_A_MUTABLE_OBJECT = DescribedPredicate.describe(
            "hold a mutable collection, an atomic, a builder, an array or a JavaFX property",
            field -> {
                var type = field.getRawType();
                var concrete = !type.isInterface();
                return type.isArray()
                        || type.getPackageName().startsWith("java.util.concurrent.atomic")
                        || type.getPackageName().startsWith("javafx.beans.property")
                        || type.getPackageName().startsWith("javafx.collections")
                        || type.isAssignableTo(StringBuilder.class) || type.isAssignableTo(StringBuffer.class)
                        || concrete && (type.isAssignableTo(java.util.Collection.class)
                                || type.isAssignableTo(java.util.Map.class))
                        || type.isAssignableTo(java.util.Queue.class)
                        || type.isAssignableTo(java.util.concurrent.ConcurrentMap.class);
            });

    @ArchTest
    static final ArchRule noStaticMutableObjects = noFields()
            .that().areStatic()
            .and().doNotHaveModifier(JavaModifier.SYNTHETIC)
            .and().areDeclaredInClassesThat().doNotHaveFullyQualifiedName(Main.class.getName())
            .should(ArchConditions.have(HOLDS_A_MUTABLE_OBJECT))
            .because("a final field is still global state when what it holds can change (ADR 0005)");

    // names

    @ArchTest
    static final ArchRule forecastProvidersAreInTheirPackage = classes()
            .that().implement(ForecastProvider.class)
            .and().areNotInterfaces()
            .should().resideInAPackage(ENGINE + ".forecast.providers..")
            .because("a provider is one class beside the others, so adding one is adding a file (ADR 0001)");

    @ArchTest
    static final ArchRule portsAreImplementedInThePlatforms = classes()
            .that().implement(Tray.class).or().implement(Notifications.class).or().implement(StartAtLogin.class)
            .or().implement(Keychain.class)
            .and().areNotInterfaces()
            .and().doNotHaveFullyQualifiedName(NoKeychain.class.getName())
            .should().resideInAPackage("..platform..")
            .because("the desktop and the engine own the ports; each platform implements them (ADR 0005)");
}

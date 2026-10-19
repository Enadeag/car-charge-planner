package nl.enadeag.carchargeplanner;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noCodeUnits;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.lang.conditions.ArchConditions;
import com.tngtech.archunit.library.dependencies.SliceAssignment;
import com.tngtech.archunit.library.dependencies.SliceIdentifier;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import nl.enadeag.carchargeplanner.desktop.app.AppSettingsFile;
import nl.enadeag.carchargeplanner.desktop.system.Notifications;
import nl.enadeag.carchargeplanner.desktop.system.StartAtLogin;
import nl.enadeag.carchargeplanner.desktop.system.Tray;
import nl.enadeag.carchargeplanner.engine.forecast.ForecastProvider;
import nl.enadeag.carchargeplanner.engine.forecast.HttpGet;
import nl.enadeag.carchargeplanner.engine.forecast.JdkHttpGet;
import nl.enadeag.carchargeplanner.engine.secrets.Keychain;
import nl.enadeag.carchargeplanner.engine.secrets.NoKeychain;
import nl.enadeag.carchargeplanner.host.Host;
import nl.enadeag.carchargeplanner.host.Os;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The rules of ADR 0001 and ADR 0005: an engine without user interface, platforms that do not know each other, one
 * composition root that wires everything, and no global state, clocks, threads or system reads outside the places
 * that own them. A rule that older code still breaks is frozen in {@code src/test/archunit_store}: what is stored
 * there may be fixed, and nothing new may join it.
 */
@AnalyzeClasses(packages = "nl.enadeag.carchargeplanner", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    /** The dashboard, the texts, the app settings, the theme and Settings: what no platform may reach. */
    private static final DescribedPredicate<JavaClass> DESKTOP_OUTSIDE_ITS_SYSTEM =
            resideInAPackage("..desktop..").and(not(resideInAPackage("..desktop.system..")))
                    .as("are in the desktop but not in desktop.system");

    @ArchTest
    static final ArchRule engineHasNoUserInterfaceOrDesktopBus = noClasses()
            .that().resideInAPackage("..engine..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "javafx..", "java.awt..", "javax.swing..", "org.freedesktop..", "atlantafx..")
            .because("the engine also has to run on a machine without a desktop (ADR 0001)");

    @ArchTest
    static final ArchRule theSettingsFormsLogicHasNoUserInterface = noClasses()
            .that().resideInAPackage("..desktop.settings.form..")
            .should().dependOnClassesThat().resideInAnyPackage("javafx..", "java.awt..", "javax.swing..", "atlantafx..")
            .because("the form logic is tested without JavaFX, so the desktop mutation run, which leaves out the "
                    + "JavaFX tests, covers it");

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
    static final ArchRule thePlatformsKnowOnlyTheDesktopSystem = noClasses()
            .that().resideInAPackage("..platform..")
            .should().dependOnClassesThat(DESKTOP_OUTSIDE_ITS_SYSTEM)
            .because("a platform implements the ports and draws the icons of desktop.system; the dashboard, the texts, "
                    + "the app settings and the theme are the desktop's own (ADR 0005)");

    @ArchTest
    static final ArchRule theDesktopSystemKnowsNoOtherDesktopPart = noClasses()
            .that().resideInAPackage("..desktop.system..")
            .should().dependOnClassesThat(DESKTOP_OUTSIDE_ITS_SYSTEM)
            .because("the platforms build on desktop.system alone, so it must not bring the rest of the desktop along");

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

    /** The packages that make up the composition root: {@code Main} and the parts it starts the app with. */
    private static final List<String> OUTER_PARTS = List.of("start", "cli", "platforms", "logging", "host");

    /** The composition root, as package identifiers: the root package itself and its own subpackages. */
    private static final String[] OUTER = Stream.concat(Stream.of(ROOT),
            OUTER_PARTS.stream().map(part -> ROOT + "." + part + "..")).toArray(String[]::new);

    private static final String START = ROOT + ".start..";

    private static final String CLI = ROOT + ".cli..";

    private static final String PLATFORMS = ROOT + ".platforms..";

    private static final String LOGGING = ROOT + ".logging..";

    private static final String HOST = ROOT + ".host..";

    private static final String ENGINE = ROOT + ".engine";

    // package structure

    /** The root package, with {@code Main} alone, as one slice, and each package right below it as another. */
    private static final SliceAssignment TOP_LEVEL_PACKAGES = new SliceAssignment() {
        @Override
        public SliceIdentifier getIdentifierOf(JavaClass type) {
            var name = type.getPackageName();
            if (name.equals(ROOT)) {
                return SliceIdentifier.of("the root package");
            }
            if (name.startsWith(ROOT + ".")) {
                return SliceIdentifier.of(name.substring(ROOT.length() + 1).split("\\.")[0]);
            }
            return SliceIdentifier.ignore();
        }

        @Override
        public String getDescription() {
            return "the root package and each package right below it";
        }
    };

    @ArchTest
    static final ArchRule noCyclesBetweenTheTopLevelPackages = slices()
            .assignedFrom(TOP_LEVEL_PACKAGES)
            .should().beFreeOfCycles()
            .because("the engine, the desktop, the platforms, the single-instance guard and the composition root's "
                    + "parts are built and read apart (ADR 0001, ADR 0005)");

    @ArchTest
    static final ArchRule theCompositionRootsPartsPointOneWay = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Main").definedBy(ROOT)
            .layer("Start").definedBy(START)
            .layer("Command line").definedBy(CLI)
            .layer("Platforms").definedBy(PLATFORMS)
            .layer("Logging").definedBy(LOGGING)
            .layer("Host").definedBy(HOST)
            .whereLayer("Main").mayNotBeAccessedByAnyLayer()
            .whereLayer("Start").mayOnlyBeAccessedByLayers("Main")
            .whereLayer("Command line").mayOnlyBeAccessedByLayers("Main", "Start")
            .whereLayer("Platforms").mayOnlyBeAccessedByLayers("Main", "Start", "Command line")
            .whereLayer("Logging").mayOnlyBeAccessedByLayers("Main", "Start", "Command line")
            .whereLayer("Host").mayOnlyBeAccessedByLayers("Main", "Start", "Command line", "Platforms", "Logging")
            .because("Main starts the app or runs a command; the app uses the command line's exit codes; both build "
                    + "on each system's parts, the log and the host, and the host, which those build on, knows none of "
                    + "them");

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
            .that().resideOutsideOfPackages(ROOT, PLATFORMS, CLI, "..platform..")
            .should().dependOnClassesThat().resideInAPackage("..platform..")
            .because("the composition root picks the platform's parts and hands them on as the ports (ADR 0005); "
                    + "platforms makes each system's parts, and cli the Linux menu entry");

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
            .should().dependOnClassesThat().resideInAnyPackage(OUTER)
            .because("the composition root wires them; they get what they need through their constructors (ADR 0005)");

    @ArchTest
    static final ArchRule providersAreOnlyReachedThroughTheForecast = noClasses()
            .that().resideOutsideOfPackages(ENGINE + ".forecast..", ROOT, START)
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
            .layer("Root").definedBy(OUTER)
            .layer("Platform").definedBy("..platform..")
            .layer("Desktop").definedBy(DESKTOP_OUTSIDE_ITS_SYSTEM)
            .layer("Desktop system").definedBy("..desktop.system..")
            .layer("Instance").definedBy("..instance..")
            .layer("Engine").definedBy("..engine..")
            .whereLayer("Root").mayNotBeAccessedByAnyLayer()
            .whereLayer("Platform").mayOnlyBeAccessedByLayers("Root")
            .whereLayer("Desktop").mayOnlyBeAccessedByLayers("Root")
            .whereLayer("Desktop system").mayOnlyBeAccessedByLayers("Root", "Platform", "Desktop")
            .whereLayer("Instance").mayOnlyBeAccessedByLayers("Root")
            .whereLayer("Engine").mayOnlyBeAccessedByLayers("Root", "Platform", "Desktop")
            .because("the composition root sits on the platforms, which implement the ports of desktop.system; the "
                    + "rest of the desktop sits on those ports and on the engine; the single-instance guard is used by "
                    + "the root alone (ADR 0001, ADR 0005)");

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
            .that().resideOutsideOfPackages(ROOT, START)
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
            .that().resideOutsideOfPackages(ROOT, START, "..platform..", "..instance..")
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
            .that().doNotHaveFullyQualifiedName(ROOT + ".logging.LogLine")
            .should().callMethodWhere(PRINT_A_STACK_TRACE)
            .because("a failure goes to the log, which knows what it may show; only LogLine, which writes the log, "
                    + "prints a trace, to a writer of its own (ADR 0005)");

    @ArchTest
    static final ArchRule onlyTheRootConfiguresJavaUtilLogging = noClasses()
            .that().resideOutsideOfPackages(ROOT, START, LOGGING)
            .should().dependOnClassesThat().resideInAPackage("java.util.logging..")
            .because("the app's log is set up once, by AppLog; everything else logs through System.Logger (ADR 0005)");

    @ArchTest
    static final ArchRule onlyTheLogHandlesAndFormatsRecords = noClasses()
            .that().resideOutsideOfPackage(LOGGING)
            .should().dependOnClassesThat().areAssignableTo(java.util.logging.Handler.class)
            .orShould().dependOnClassesThat().areAssignableTo(java.util.logging.Formatter.class)
            .because("the log file, its format and the terminal are set up in logging alone, so every record is "
                    + "written the same way (ADR 0005)");

    @ArchTest
    static final ArchRule onlyTheRootWritesToTheConsole = noClasses()
            .that().resideOutsideOfPackages(ROOT, START)
            .should().accessField(System.class, "out")
            .orShould().accessField(System.class, "err")
            .because("the command line's answers are printed by Main, and by start when a second start ends; the "
                    + "rest logs (ADR 0005)");

    // what is confined to one place

    private static DescribedPredicate<JavaClass> notPartOf(String topLevelClassName) {
        return DescribedPredicate.describe("are not part of " + topLevelClassName,
                type -> !type.getName().equals(topLevelClassName) && !type.getName().startsWith(topLevelClassName + "$"));
    }

    @ArchTest
    static final ArchRule theDesktopBusIsOnlyForLinux = noClasses()
            .that().resideOutsideOfPackage("..platform.linux..")
            .should().dependOnClassesThat().resideInAPackage("org.freedesktop..")
            .because("D-Bus is how the Linux platform talks to the tray, notifications and keyring (ADR 0005)");

    @ArchTest
    static final ArchRule onlyOneClassMakesHttpCalls = noClasses()
            .that().resideOutsideOfPackages(ROOT, START)
            .and(notPartOf(JdkHttpGet.class.getName()))
            .should().dependOnClassesThat().resideInAPackage("java.net.http..")
            .because("every provider asks through the HttpGet port, so a test can answer instead (ADR 0001)");

    @ArchTest
    static final ArchRule jacksonIsOnlyForStoredFiles = noClasses()
            .that().resideOutsideOfPackage(ENGINE + "..")
            .and(notPartOf(AppSettingsFile.class.getName()))
            .should().dependOnClassesThat().resideInAnyPackage("tools.jackson..", "com.fasterxml.jackson..")
            .because("the engine's files and the desktop's app settings are the only JSON the app reads and writes "
                    + "(ADR 0001)");

    @ArchTest
    static final ArchRule theDesktopWritesFilesOnlyForItsAppSettings = noClasses()
            .that().resideInAPackage("..desktop..")
            .and().doNotHaveFullyQualifiedName(AppSettingsFile.class.getName())
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

    /** Throwable is left to {@code CatchClausesTest}: javac catches it itself for every try-with-resources. */
    @ArchTest
    static final ArchRule noCatchingNullPointer = noCodeUnits()
            .should(CATCH_NULL_POINTER)
            .because("a null is checked where it may come, not caught after it went wrong");

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

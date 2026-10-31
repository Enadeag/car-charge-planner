package nl.enadeag.carchargeplanner.engine;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.base.DescribedPredicate;
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
 * uses no {@code java.net.http}, which Android lacks, opens no connection of its own beside its HttpGet, and logs
 * through slf4j. They run when the engine is built alone; the rules about the boundary with its clients, and those for
 * every class, are in the desktop module's {@code ArchitectureTest}, which sees both sides.
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

    @ArchTest
    static final ArchRule theEngineOpensNoConnectionOfItsOwn = noClasses()
            .that().resideInAPackage(ENGINE + "..")
            .should().dependOnClassesThat().haveFullyQualifiedName("java.net.URLConnection")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("java.net.HttpURLConnection")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("javax.net.ssl.HttpsURLConnection")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("java.net.Socket")
            .orShould().callMethod("java.net.URL", "openConnection")
            .orShould().callMethod("java.net.URL", "openStream")
            .because("every request goes through the HttpGet the platform hands in, which keeps its contract on "
                    + "redirects, timeouts and size (ADR 0010)");

    @ArchTest
    static final ArchRule theEngineLogsThroughSlf4j = noClasses()
            .that().resideInAPackage(ENGINE + "..")
            .should().dependOnClassesThat().haveFullyQualifiedName("java.lang.System$Logger")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("java.lang.System$Logger$Level")
            .orShould().callMethod(System.class, "getLogger", String.class)
            .orShould().callMethod(System.class, "getLogger", String.class, java.util.ResourceBundle.class)
            .because("the engine logs through slf4j, which each platform binds to its own log, instead of "
                    + "System.Logger (ADR 0010)");

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
}

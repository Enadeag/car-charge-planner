package io.github.enadeag.carchargeplanner;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import io.github.enadeag.carchargeplanner.engine.forecast.HttpGet;
import java.util.Locale;

/** The rules of ADR 0005: an engine without user interface, and no global state outside the composition root. */
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
            .orShould().callMethod(Throwable.class, "printStackTrace")
            .because("an address can hold an API key and the home's coordinates, so what makes or sends one logs "
                    + "nothing; ProviderConformance checks what the providers' errors say");

    @ArchTest
    static final ArchRule noStaticMutableState = fields()
            .that().areStatic()
            .and().areDeclaredInClassesThat().doNotHaveFullyQualifiedName(Main.class.getName())
            .should().beFinal()
            .because("dependencies come in through constructors, never through global state (ADR 0005)");
}

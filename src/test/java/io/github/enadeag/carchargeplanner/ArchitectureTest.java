package io.github.enadeag.carchargeplanner;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

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
    static final ArchRule noStaticMutableState = fields()
            .that().areStatic()
            .and().areDeclaredInClassesThat().doNotHaveFullyQualifiedName(Main.class.getName())
            .should().beFinal()
            .because("dependencies come in through constructors, never through global state (ADR 0005)");
}

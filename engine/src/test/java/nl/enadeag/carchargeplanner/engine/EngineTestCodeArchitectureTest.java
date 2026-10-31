package nl.enadeag.carchargeplanner.engine;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * The house rules of CONTRIBUTING.md for the engine's tests, the test helpers the desktop shares among them: no
 * sleeping, and no reading of the real home folder or environment, the same rules as the desktop module's
 * {@code TestCodeArchitectureTest} but with nothing frozen; and, as in {@code EngineArchitectureTest}, no
 * {@code java.net.http}.
 */
@AnalyzeClasses(packages = "nl.enadeag.carchargeplanner", importOptions = ImportOption.OnlyIncludeTests.class)
class EngineTestCodeArchitectureTest {

    @ArchTest
    static final ArchRule testsDoNotSleep = noClasses()
            .should().callMethod(Thread.class, "sleep", long.class)
            .orShould().callMethod(Thread.class, "sleep", long.class, int.class)
            .orShould().callMethod(Thread.class, "sleep", java.time.Duration.class)
            .because("a test waits for a signal or sets the clock; a sleep is slow when it is long and flaky when it "
                    + "is short (CONTRIBUTING.md, ADR 0005)");

    @ArchTest
    static final ArchRule theEngineTestsDoNotUseJavasHttpClient = noClasses()
            .should().dependOnClassesThat().resideInAPackage("java.net.http..")
            .because("the engine's tests run on Android's Java too, which has no java.net.http (ADR 0010)");

    @ArchTest
    static final ArchRule testsDoNotReadTheRealHomeOrEnvironment = noClasses()
            .that(DescribedPredicate.describe("do not start a JVM",
                    type -> type.getDirectDependenciesFromSelf().stream()
                            .noneMatch(dependency -> dependency.getTargetClass().isEquivalentTo(ProcessBuilder.class))))
            .should().callMethod(System.class, "getProperty", String.class)
            .orShould().callMethod(System.class, "getProperty", String.class, String.class)
            .orShould().callMethod(System.class, "getProperties")
            .orShould().callMethod(System.class, "getenv")
            .orShould().callMethod(System.class, "getenv", String.class)
            .because("a test works in a folder of its own and is handed what it needs, never the owner's home or "
                    + "environment; ArchUnit cannot read which property, so only a test that starts a JVM may read "
                    + "one, to find that JVM and its class path");
}

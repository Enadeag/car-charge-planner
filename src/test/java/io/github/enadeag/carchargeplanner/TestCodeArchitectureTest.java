package io.github.enadeag.carchargeplanner;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.freeze.FreezingArchRule;

/**
 * The house rules of CONTRIBUTING.md for the tests themselves: no sleeping, and no reading of the real home folder
 * or environment. The sleeps that are still there are frozen in {@code src/test/archunit_store}: they may be removed,
 * and none may be added.
 */
@AnalyzeClasses(packages = "io.github.enadeag.carchargeplanner", importOptions = ImportOption.OnlyIncludeTests.class)
class TestCodeArchitectureTest {

    @ArchTest
    static final ArchRule testsDoNotSleep = FreezingArchRule.freeze(noClasses()
            .should().callMethod(Thread.class, "sleep", long.class)
            .orShould().callMethod(Thread.class, "sleep", long.class, int.class)
            .orShould().callMethod(Thread.class, "sleep", java.time.Duration.class)
            .because("a test waits for a signal or sets the clock; a sleep is slow when it is long and flaky when it "
                    + "is short (CONTRIBUTING.md, ADR 0005)"));

    @ArchTest
    static final ArchRule testsDoNotReadTheRealHomeOrEnvironment = noClasses()
            .that(DescribedPredicate.describe("do not start a JVM",
                    type -> type.getDirectDependenciesFromSelf().stream()
                            .noneMatch(dependency -> dependency.getTargetClass().isEquivalentTo(ProcessBuilder.class))))
            .should().callMethod(System.class, "getProperty", String.class)
            .orShould().callMethod(System.class, "getProperty", String.class, String.class)
            .orShould().callMethod(System.class, "getProperties")
            .orShould().callMethod(System.class, "getenv", String.class)
            .because("a test works in a folder of its own and is handed what it needs, never the owner's home or "
                    + "environment; ArchUnit cannot read which property, so only a test that starts a JVM may read "
                    + "one, to find that JVM and its class path");
}

package nl.enadeag.carchargeplanner.buildlogic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class InstrumentedResultsCheckTest {

    private static final String PASSED = "<testsuite tests=\"1\"><testcase name=\"a\" classname=\"A\"/></testsuite>";

    @Test
    void resultsWithATestAndNoFailurePass() {
        assertThat(InstrumentedResultsCheck.resultProblems(Map.of("TEST-a.xml", PASSED, "other.xml", "<x/>")))
                .isEmpty();
    }

    @Test
    void aFailureInAnyResultFileFails() {
        var failed = "<testsuite><testcase name=\"b\" classname=\"A\"><failure>trace</failure></testcase></testsuite>";

        assertThat(InstrumentedResultsCheck.resultProblems(Map.of("TEST-a.xml", PASSED, "TEST-b.xml", failed)))
                .containsExactly("a failure in TEST-b.xml");
    }

    @Test
    void resultsWithoutATestCaseFail() {
        assertThat(InstrumentedResultsCheck.resultProblems(Map.of("TEST-a.xml", "<testsuite tests=\"0\"/>")))
                .containsExactly("no test ran");
        assertThat(InstrumentedResultsCheck.resultProblems(Map.of())).containsExactly("no test ran");
    }

    @Test
    void aTestClassTheRunnerDoesNotNameFails() {
        var sources = Map.of(
                "NamedTest.java", "class NamedTest { @Test public void a() {} }",
                "ForgottenTest.java", "class ForgottenTest { @Test public void a() {} }",
                "QualifiedTest.java", "class QualifiedTest { @org.junit.Test public void a() {} }",
                "Helper.java", "class Helper { @Tested int a; }",
                "Runner.java", "Class<?>[] SUITE = {NamedTest.class};");

        assertThat(InstrumentedResultsCheck.unnamedTestClasses(sources, sources.get("Runner.java")))
                .containsExactlyInAnyOrder(
                        "ForgottenTest has a test but the runner's suite does not name it, so it never runs",
                        "QualifiedTest has a test but the runner's suite does not name it, so it never runs");
    }

    @Test
    void aClassNamedOnlyAsPartOfAnotherNameCountsAsUnnamed() {
        var sources = Map.of("AppTest.java", "class AppTest { @Test public void a() {} }");

        assertThat(InstrumentedResultsCheck.unnamedTestClasses(sources, "{MinifiedAppTest.class}")).hasSize(1);
    }
}

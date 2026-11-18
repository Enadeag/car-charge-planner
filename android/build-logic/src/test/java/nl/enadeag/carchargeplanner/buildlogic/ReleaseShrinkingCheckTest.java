package nl.enadeag.carchargeplanner.buildlogic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ReleaseShrinkingCheckTest {

    private static final String KEEP_LINES = "-keepattributes SourceFile,LineNumberTable\n";
    private static final String RULES = "# names stay\n-dontobfuscate\n" + KEEP_LINES + "-dontoptimize\n";
    private static final String NO_LINES =
            "no rule file keeps SourceFile,LineNumberTable, so frames would lose their files and lines";

    @Test
    void aShrunkBuildThatKeepsItsNamesPasses() {
        assertThat(ReleaseShrinkingCheck.problems(true, List.of("-keep class a.B", RULES))).isEmpty();
    }

    @Test
    void aBuildThatIsNotMinifiedFails() {
        assertThat(ReleaseShrinkingCheck.problems(false, List.of(RULES))).containsExactly("isMinifyEnabled is false");
    }

    @Test
    void aBuildWithoutDontobfuscateFailsAlsoWhenOnlyACommentOrAnotherWordHasIt() {
        var renaming = List.of(KEEP_LINES + "-keepattributes Signature # -dontobfuscate\n-dontobfuscated\n",
                "-dontoptimize\n");

        assertThat(ReleaseShrinkingCheck.problems(true, renaming))
                .containsExactly("no rule file says -dontobfuscate, so R8 would rename the classes");
        assertThat(ReleaseShrinkingCheck.problems(true, List.of("-dontoptimize", KEEP_LINES)))
                .containsExactly("no rule file says -dontobfuscate, so R8 would rename the classes");
    }

    @Test
    void dontshrinkInAnyRuleFileFails() {
        assertThat(ReleaseShrinkingCheck.problems(true, List.of(RULES, "-keep class a.B\r\n  -dontshrink  \r\n")))
                .containsExactly("a rule file says -dontshrink");
    }

    @Test
    void aBuildWithoutDontoptimizeFails() {
        assertThat(ReleaseShrinkingCheck.problems(true, List.of("-dontobfuscate\n# -dontoptimize\n" + KEEP_LINES)))
                .containsExactly("no rule file says -dontoptimize, so R8 would number the lines anew");
    }

    @Test
    void theOptionsCountOnALineWithOtherOptionsAndInAnyRuleFile() {
        assertThat(ReleaseShrinkingCheck.problems(true, List.of("-dontwarn a.B -dontobfuscate", "-dontoptimize",
                "-keepattributes Signature,SourceFile -keepattributes LineNumberTable -dontwarn c.D")))
                .isEmpty();
    }

    @Test
    void aBuildThatKeepsOnlyOneOfTheSourceFileAndItsLinesFails() {
        var withoutLines = "-dontobfuscate -dontoptimize\n-keepattributes SourceFile # ,LineNumberTable\n";
        var linesOfAnotherOption = "-dontobfuscate -dontoptimize -keepattributes SourceFile -dontwarn LineNumberTable";

        assertThat(ReleaseShrinkingCheck.problems(true, List.of(withoutLines))).containsExactly(NO_LINES);
        assertThat(ReleaseShrinkingCheck.problems(true, List.of(linesOfAnotherOption))).containsExactly(NO_LINES);
    }

    @Test
    void everyProblemIsNamedAtOnce() {
        assertThat(ReleaseShrinkingCheck.problems(false, List.of("-dontshrink"))).containsExactly(
                "isMinifyEnabled is false",
                "a rule file says -dontshrink",
                "no rule file says -dontobfuscate, so R8 would rename the classes",
                "no rule file says -dontoptimize, so R8 would number the lines anew",
                NO_LINES);
    }

    @Test
    void noRuleFileAtAllFails() {
        assertThat(ReleaseShrinkingCheck.problems(true, List.of())).containsExactly(
                "no rule file says -dontobfuscate, so R8 would rename the classes",
                "no rule file says -dontoptimize, so R8 would number the lines anew",
                NO_LINES);
    }
}

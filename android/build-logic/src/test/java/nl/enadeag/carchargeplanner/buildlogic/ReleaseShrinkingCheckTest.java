package nl.enadeag.carchargeplanner.buildlogic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ReleaseShrinkingCheckTest {

    private static final String RULES =
            "# names stay\n-dontobfuscate\n-keepattributes SourceFile,LineNumberTable\n-dontoptimize\n";

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
        var renaming = List.of("-keepattributes SourceFile # -dontobfuscate\n-dontobfuscated\n-dontoptimize\n");

        assertThat(ReleaseShrinkingCheck.problems(true, renaming))
                .containsExactly("no rule file says -dontobfuscate, so R8 would rename the classes");
        assertThat(ReleaseShrinkingCheck.problems(true, List.of("-dontoptimize")))
                .containsExactly("no rule file says -dontobfuscate, so R8 would rename the classes");
    }

    @Test
    void dontshrinkInAnyRuleFileFails() {
        assertThat(ReleaseShrinkingCheck.problems(true, List.of(RULES, "-keep class a.B\r\n  -dontshrink  \r\n")))
                .containsExactly("a rule file says -dontshrink");
    }

    @Test
    void aBuildWithoutDontoptimizeFails() {
        assertThat(ReleaseShrinkingCheck.problems(true, List.of("-dontobfuscate\n# -dontoptimize\n")))
                .containsExactly("no rule file says -dontoptimize, so R8 would number the lines anew");
    }

    @Test
    void theOptionsCountOnALineWithOtherOptionsAndInAnyRuleFile() {
        assertThat(ReleaseShrinkingCheck.problems(true, List.of("-dontwarn a.B -dontobfuscate", "-dontoptimize")))
                .isEmpty();
    }
}

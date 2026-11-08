package nl.enadeag.carchargeplanner.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Classes that break {@link EngineArchitectureTest}'s rules on purpose, which shows that the rules see them. */
class BrokenEngineArchitectureTest {

    /** Static state in fields of an interface type, beside constants that are not state. */
    static final class StaticStateBehindAnInterface {

        /** A list that changes, typed as the interface. */
        static final List<String> SEEN = new ArrayList<>();

        /** A constant that cannot change. */
        static final List<String> NAMES = List.of("Open-Meteo", "Forecast.Solar");

        /** A set that changes, made by a call rather than a constructor. */
        static final Set<String> ASKED = Collections.synchronizedSet(new java.util.HashSet<>());

        /** A constant set after a map that changes was made for it. */
        static final Map<String, Integer> SIZES;

        /** A map that changes, set in a static block. */
        static final Map<String, Integer> COUNTED;

        static {
            var sizes = new HashMap<String, Integer>();
            sizes.put("small", 1);
            SIZES = Map.copyOf(sizes);
            COUNTED = new HashMap<>();
        }

        private StaticStateBehindAnInterface() {
        }
    }

    @Test
    void aStaticListOrMapThatChangesBreaksTheRuleThoughItsTypeIsAnInterface() {
        var state = new ClassFileImporter().importClasses(StaticStateBehindAnInterface.class);

        assertThatThrownBy(() -> EngineArchitectureTest.theEngineHasNoStaticMutableObjects.check(state))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("StaticStateBehindAnInterface.SEEN")
                .hasMessageContaining("StaticStateBehindAnInterface.ASKED")
                .hasMessageContaining("StaticStateBehindAnInterface.COUNTED")
                .satisfies(failure -> assertThat(failure.getMessage())
                        .doesNotContain("StaticStateBehindAnInterface.NAMES")
                        .doesNotContain("StaticStateBehindAnInterface.SIZES>"));
    }
}

package nl.enadeag.carchargeplanner.buildlogic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class JdkClassReferencesTest {

    @SuppressWarnings("unused")
    static final class Sample {
        private List<Runtime.Version> versions;

        long pid(Duration wait) {
            return ProcessHandle.current().pid() + wait.toMillis();
        }

        String[] names() {
            return new String[0];
        }
    }

    @Test
    void aCompiledClassGivesTheJdkClassesItCallsAndTheTypesInItsSignatures() throws IOException {
        try (var in = Sample.class.getResourceAsStream("JdkClassReferencesTest$Sample.class")) {
            assertThat(JdkClassReferences.in(in))
                    .contains("java/lang/ProcessHandle", "java/lang/Runtime$Version", "java/time/Duration",
                            "java/util/List", "java/lang/String", "java/lang/Object")
                    .allMatch(name -> name.startsWith("java/"));
        }
    }

    @Test
    void whatIsNoClassFileIsRefused() {
        assertThatThrownBy(() -> JdkClassReferences.in(new ByteArrayInputStream(new byte[] {1, 2, 3, 4, 5, 6, 7, 8})))
                .isInstanceOf(IOException.class);
    }
}

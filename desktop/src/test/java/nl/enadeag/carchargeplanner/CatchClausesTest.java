package nl.enadeag.carchargeplanner;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * No code catches Throwable but the calls into Windows, whose {@code MethodHandle.invokeExact} declares it. Read from
 * the sources, not the classes: javac catches Throwable itself in every try-with-resources.
 */
class CatchClausesTest {

    private static final Path MAIN = Path.of("src/main/java");
    private static final Pattern CATCH_THROWABLE = Pattern.compile("catch\\s*\\([^)]*\\bThrowable\\b");
    private static final Path ALLOWED = Path.of("nl/enadeag/carchargeplanner/platform/windows/Advapi32.java");

    @Test
    void onlyTheCallsIntoWindowsCatchThrowable() throws IOException {
        var catching = new ArrayList<Path>();
        try (var files = Files.walk(MAIN)) {
            for (var file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                if (CATCH_THROWABLE.matcher(Files.readString(file)).find()) {
                    catching.add(MAIN.relativize(file));
                }
            }
        }

        assertThat(catching).isEqualTo(List.of(ALLOWED));
    }
}

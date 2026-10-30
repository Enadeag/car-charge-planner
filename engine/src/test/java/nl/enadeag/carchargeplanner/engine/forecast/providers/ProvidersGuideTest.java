package nl.enadeag.carchargeplanner.engine.forecast.providers;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** The skeleton in docs/providers.md is the example provider the build tests, so the guide cannot drift from it. */
class ProvidersGuideTest {

    private static final Pattern BLOCK = Pattern.compile("```(?:java|json)\\n(.*?)```", Pattern.DOTALL);
    private static final Path HERE = Path.of("src/test/java/nl/enadeag/carchargeplanner/engine/forecast/providers");
    private static final Path RECORDED = Path.of("src/test/resources/nl/enadeag/carchargeplanner/engine/forecast/providers");

    @Test
    void theGuidesSkeletonIsTheTestedExampleProvider() throws IOException {
        var blocks = BLOCK.matcher(Files.readString(Path.of("docs", "providers.md"))).results()
                .map(match -> match.group(1)).toList();

        assertThat(blocks).hasSizeGreaterThanOrEqualTo(3);
        assertThat(blocks.get(0)).isEqualTo(Files.readString(HERE.resolve("ExampleSolar.java")));
        assertThat(blocks.get(1)).isEqualTo(Files.readString(HERE.resolve("ExampleSolarTest.java")));
        assertThat(blocks.get(2)).isEqualTo(Files.readString(RECORDED.resolve("example_solar.json")));
    }
}

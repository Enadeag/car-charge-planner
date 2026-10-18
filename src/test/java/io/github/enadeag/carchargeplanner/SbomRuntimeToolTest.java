package io.github.enadeag.carchargeplanner;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Runs {@code tools/SbomRuntime.java} the way the packaging scripts do: as a source file, with Jackson beside it. */
class SbomRuntimeToolTest {

    private static final String APP = "pkg:maven/io.github.enadeag/car-charge-planner@1.0.0?type=jar";
    private static final String JACKSON = "pkg:maven/tools.jackson.core/jackson-core@3.2.3?type=jar";
    private static final String SBOM = """
            {
              "bomFormat": "CycloneDX",
              "specVersion": "1.6",
              "metadata": {"component": {"type": "application", "bom-ref": "%1$s", "purl": "%1$s"}},
              "components": [{"type": "library", "bom-ref": "%2$s", "purl": "%2$s"}],
              "dependencies": [{"ref": "%1$s", "dependsOn": ["%2$s"]}, {"ref": "%2$s", "dependsOn": []}]
            }
            """.formatted(APP, JACKSON);

    @TempDir
    Path folder;

    private record Run(int exitCode, JsonNode sbom) {
    }

    @Test
    void addsTheRuntimeWithItsVersionAndModulesAsSomethingTheAppDependsOn() throws Exception {
        var run = sbomRuntime(thisJavaVersion());

        assertThat(run.exitCode()).isZero();
        var components = run.sbom().get("components");
        assertThat(components.get(0).get("purl").asString()).isEqualTo(JACKSON);
        var runtime = components.get(1);
        assertThat(runtime.get("version").asString()).isEqualTo(Runtime.version().toString());
        assertThat(runtime.get("cpe").asString())
                .isEqualTo("cpe:2.3:a:oracle:openjdk:" + thisJavaVersion() + ":*:*:*:*:*:*:*");
        assertThat(runtime.get("properties").get(0).get("value").asString()).isEqualTo("java.base javafx.controls");
        assertThat(run.sbom().get("dependencies").get(0).get("dependsOn").valueStream().map(JsonNode::asString))
                .containsExactly(JACKSON, runtime.get("bom-ref").asString());
    }

    @Test
    void refusesARuntimeOfAnotherJavaVersion() throws Exception {
        var run = sbomRuntime("11.0.2");

        assertThat(run.exitCode()).isEqualTo(1);
        assertThat(run.sbom()).isNull();
    }

    private static String thisJavaVersion() {
        return Runtime.version().version().stream().map(String::valueOf).collect(Collectors.joining("."));
    }

    private Run sbomRuntime(String linkedVersion) throws IOException, InterruptedException {
        var sbom = Files.writeString(folder.resolve("in.cdx.json"), SBOM, StandardCharsets.UTF_8);
        var runtime = Files.createDirectory(folder.resolve("runtime"));
        Files.writeString(runtime.resolve("release"),
                "JAVA_VERSION=\"" + linkedVersion + "\"\nMODULES=\"java.base javafx.controls\"\n",
                StandardCharsets.UTF_8);
        var out = folder.resolve("out.cdx.json");
        var java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        var process = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
                Path.of("tools", "SbomRuntime.java").toString(), sbom.toString(), runtime.toString(), out.toString())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        assertThat(process.waitFor(60, TimeUnit.SECONDS)).isTrue();
        var result = Files.exists(out) ? JsonMapper.builder().build().readTree(Files.readString(out)) : null;
        return new Run(process.exitValue(), result);
    }
}

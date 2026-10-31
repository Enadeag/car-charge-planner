package nl.enadeag.carchargeplanner.buildlogic;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AndroidApiClassesCheckTest {

    @TempDir
    Path folder;

    @Test
    void aJarGivesItsClassesOnly() throws IOException {
        var jar = folder.resolve("android.jar");
        try (var zip = new ZipOutputStream(Files.newOutputStream(jar))) {
            for (var name : List.of("java/lang/String.class", "java/util/Map$Entry.class", "META-INF/MANIFEST.MF")) {
                zip.putNextEntry(new ZipEntry(name));
                zip.closeEntry();
            }
        }

        assertThat(AndroidApiClassesCheck.classesIn(List.of(jar.toFile())))
                .containsExactlyInAnyOrder("java/lang/String", "java/util/Map$Entry");
    }

    @Test
    void eachClassThatAndroidLacksIsNamedWithTheClassThatUsesIt() {
        var references = new TreeMap<String, Set<String>>(Map.of(
                "nl.x.Clock", Set.of("java/lang/String", "java/lang/ProcessHandle"),
                "nl.x.Version", Set.of("java/lang/Runtime$Version")));

        assertThat(AndroidApiClassesCheck.missing(references, Set.of("java/lang/String", "java/lang/Runtime")))
                .containsExactly("java.lang.ProcessHandle, used by nl.x.Clock",
                        "java.lang.Runtime$Version, used by nl.x.Version");
    }

    @Test
    void nothingIsMissingWhenAndroidHasEveryClass() {
        var references = Map.of("nl.x.Clock", Set.of("java/lang/String", "java/time/Instant"));

        assertThat(AndroidApiClassesCheck.missing(references, Set.of("java/lang/String", "java/time/Instant")))
                .isEmpty();
    }
}

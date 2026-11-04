package nl.enadeag.carchargeplanner.buildlogic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.gradle.api.GradleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LicenceAssetsTest {

    private static final String NOTICE =
            "Car charge planner\r\nCopyright (C) 2026 Someone\r\n\r\nThis program is free.";
    private static final String LICENCE = "GNU GENERAL PUBLIC LICENSE\n";
    private static final String TERMS = "# Additional terms\n\nTerms.\n";

    @Test
    void theAppsOwnTextsComeFirstThenTheLegalFilesByNameAndTheIndexLast() {
        var legal = new LinkedHashMap<String, String>();
        legal.put("slf4j.txt", "SLF4J\nMIT\n");
        legal.put("apache-2.0.txt", "Apache License 2.0\nArtifact: androidx.core:core\n");

        var assets = LicenceAssets.contents(NOTICE, LICENCE, TERMS, legal);

        assertThat(assets.keySet()).containsExactly("NOTICE", "LICENSE", "ADDITIONAL-TERMS.md", "apache-2.0.txt",
                "slf4j.txt", "index.txt");
        assertThat(assets.get("index.txt")).isEqualTo("""
                copyright\tCopyright (C) 2026 Someone
                part\tNOTICE\tCar charge planner (GPL-3.0-or-later)
                part\tLICENSE\tGNU General Public License, version 3
                part\tADDITIONAL-TERMS.md\tAdditional terms
                part\tapache-2.0.txt\tApache License 2.0
                part\tslf4j.txt\tSLF4J
                """);
    }

    @Test
    void theTextsGetUnixLineEndingsAndEndInOneSoEveryMachineMakesTheSameBytes() {
        var assets = LicenceAssets.contents(NOTICE, LICENCE, TERMS, Map.of("mac.txt", "Old\rMac"));

        assertThat(assets.get("NOTICE")).isEqualTo(
                "Car charge planner\nCopyright (C) 2026 Someone\n\nThis program is free.\n");
        assertThat(assets.get("LICENSE")).isEqualTo(LICENCE);
        assertThat(assets.get("mac.txt")).isEqualTo("Old\nMac\n");
        assertThat(LicenceAssets.contents(NOTICE, LICENCE, TERMS, Map.of("mac.txt", "Old\rMac")))
                .containsExactlyEntriesOf(assets);
    }

    @Test
    void aTitleLineWithATabIsRefusedAsTheIndexSeparatesItsFieldsByTabs() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> LicenceAssets.contents(NOTICE, LICENCE, TERMS, Map.of("x.txt", "Two\twords\n")))
                .withMessageContaining("x.txt").withMessageContaining("replace the tab with spaces");
    }

    @Test
    void aNoticeWithoutACopyrightLineOrAFileWithoutATitleIsRefused() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> LicenceAssets.contents("Car charge planner\n", LICENCE, TERMS, Map.of()))
                .withMessageContaining("Copyright");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> LicenceAssets.contents(NOTICE, LICENCE, TERMS, Map.of("x.txt", "\nText\n")))
                .withMessageContaining("x.txt");
    }

    @Test
    void aTabInTheCopyrightLineIsRefusedAsTheIndexSeparatesItsFieldsByTabs() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> LicenceAssets.contents("Car\nCopyright (C)\t2026 Someone\n", LICENCE, TERMS,
                        Map.of()))
                .withMessageContaining("copyright line").withMessageContaining("replace the tab with spaces");
    }

    @ParameterizedTest
    @ValueSource(strings = {"a\tb.txt", "a\nb.txt", "a\rb.txt"})
    void aFileNameWithATabOrALineBreakIsRefusedAsTheIndexHasOneLinePerFile(String name) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> LicenceAssets.contents(NOTICE, LICENCE, TERMS, Map.of(name, "Title\n")))
                .withMessageContaining("tab or a line break in its name").withMessageContaining("rename the file");
    }

    @Test
    void anOwnTextThatIsNoUtf8IsRefusedNamingTheFileWithItsFix(@TempDir Path dir) throws IOException {
        var notice = Files.write(dir.resolve("NOTICE"), new byte[] {'C', 'a', 'f', (byte) 0xE9});

        assertThatThrownBy(() -> LicenceAssets.read(notice)).isInstanceOf(GradleException.class)
                .hasMessage("NOTICE is no UTF-8 text: save it as UTF-8");
    }
}

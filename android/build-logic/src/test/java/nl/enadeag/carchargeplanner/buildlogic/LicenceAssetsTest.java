package nl.enadeag.carchargeplanner.buildlogic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LicenceAssetsTest {

    private static final String NOTICE = "Car charge planner\r\nCopyright (C) 2026 Someone\r\n\r\nThis program is free.";
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
    void aNoticeWithoutACopyrightLineOrAFileWithoutATitleIsRefused() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> LicenceAssets.contents("Car charge planner\n", LICENCE, TERMS, Map.of()))
                .withMessageContaining("Copyright");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> LicenceAssets.contents(NOTICE, LICENCE, TERMS, Map.of("x.txt", "\nText\n")))
                .withMessageContaining("x.txt");
    }
}

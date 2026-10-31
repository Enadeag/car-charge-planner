package nl.enadeag.carchargeplanner.engine.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

/** The privacy of a platform whose app storage is private already, against the contract every one meets. */
class NoFilePrivacyTest extends FilePrivacyContractTest {

    @Override
    protected FilePrivacy privacy() {
        return FilePrivacy.NONE;
    }

    @Test
    void itAddsNoAttributesAndChangesNothing() throws IOException {
        var file = Files.writeString(folder.resolve("data.json"), "{}");

        assertThat(FilePrivacy.NONE.newFileAttributes(file)).isEmpty();
        assertThat(FilePrivacy.NONE.newFolderAttributes(folder)).isEmpty();
        assertThat(FilePrivacy.NONE.tighten(file)).isFalse();
        assertThat(FilePrivacy.NONE.tighten(folder.resolve("missing"))).isFalse();
    }
}

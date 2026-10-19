package nl.enadeag.carchargeplanner.engine.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import nl.enadeag.carchargeplanner.LogCapture;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;

class StoredFileTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-17T14:30:00Z"), ZoneOffset.UTC);

    /** A file that holds one text, {@code {"text": "..."}}, and refuses anything else. */
    private static final class TextFormat implements FileFormat<String> {

        @Override
        public String read(JsonNode root) throws FormatProblem {
            if (!root.path("text").isString()) {
                throw new FormatProblem("text: must be a text");
            }
            return root.path("text").stringValue();
        }

        @Override
        public JsonNode write(String text) {
            return StoredJson.object().put("text", text);
        }
    }

    @TempDir
    Path folder;

    private Path path() {
        return folder.resolve("data.json");
    }

    private StoredFile<String> file() {
        return new StoredFile<>(path(), new TextFormat(), CLOCK);
    }

    @Test
    void withoutAFileNothingIsStored() {
        assertThat(file().read()).isEqualTo(new Stored.Absent<String>());
    }

    @Test
    void aWrittenValueIsReadBackAlsoByAnotherStore() throws IOException {
        var file = file();
        file.write("hello");

        assertThat(file.read()).isEqualTo(new Stored.Found<>("hello"));
        assertThat(file().read()).isEqualTo(new Stored.Found<>("hello"));
    }

    @Test
    void aFileThatDidNotChangeOnDiskIsNotReadAgain() throws IOException {
        var file = file();
        file.write("hello");
        var modified = Files.getLastModifiedTime(path());
        var same = Files.readString(path()).replace("hello", "world");

        // the same size and time, written in place as an editor might: only the content differs
        Files.writeString(path(), same);
        Files.setLastModifiedTime(path(), modified);

        assertThat(file.read()).isEqualTo(new Stored.Found<>("hello"));
        assertThat(file().read()).isEqualTo(new Stored.Found<>("world"));
    }

    @Test
    void aFileThatChangedOnDiskIsReadAgain() throws IOException {
        var file = file();
        file.write("hello");

        Files.writeString(path(), "{\"text\": \"changed by hand\"}");

        assertThat(file.read()).isEqualTo(new Stored.Found<>("changed by hand"));
    }

    @Test
    void aFileThatIsNotJsonCannotBeUsedAndSaysWhere() throws IOException {
        Files.writeString(path(), "{\n  \"text\": oops\n}");

        assertThat(file().read()).isInstanceOfSatisfying(Stored.Unusable.class, unusable -> {
            assertThat(unusable.reason()).isEqualTo(Stored.Reason.INVALID);
            assertThat(unusable.detail()).startsWith("not valid JSON (line 2");
        });
    }

    @Test
    void aFileOfTheWrongShapeCannotBeUsedAndSaysWhy() throws IOException {
        Files.writeString(path(), "{\"text\": 5}");

        assertThat(file().read()).isEqualTo(new Stored.Unusable<String>(Stored.Reason.INVALID, "text: must be a text"));
    }

    @Test
    void aFileThatCannotBeUsedIsKeptAsideBeforeANewOneIsWritten() throws IOException {
        Files.writeString(path(), "{\"text\": 5}");

        file().write("new");

        assertThat(folder.resolve("data.json.damaged-20261017-143000")).hasContent("{\"text\": 5}");
        assertThat(file().read()).isEqualTo(new Stored.Found<>("new"));
    }

    @Test
    void aSecondFileKeptAsideInTheSameSecondDoesNotReplaceTheFirst() throws IOException {
        Files.writeString(path(), "first damage");
        file().write("new");
        Files.writeString(path(), "second damage");

        file().write("newer");

        assertThat(folder.resolve("data.json.damaged-20261017-143000")).hasContent("first damage");
        assertThat(folder.resolve("data.json.damaged-20261017-143000-2")).hasContent("second damage");
    }

    @Test
    void aFileThatCannotBeUsedIsKeptAsideInsteadOfDeleted() throws IOException {
        Files.writeString(path(), "damaged");

        file().delete();

        assertThat(path()).doesNotExist();
        assertThat(folder.resolve("data.json.damaged-20261017-143000")).hasContent("damaged");
    }

    @Test
    void aGoodFileIsDeleted() throws IOException {
        var file = file();
        file.write("hello");

        file.delete();

        assertThat(path()).doesNotExist();
        assertThat(file.read()).isEqualTo(new Stored.Absent<String>());
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void aFileTheSystemRefusesToReadIsUnreadableAndKeptAsideOnAWrite() throws IOException {
        Files.writeString(path(), "{\"text\": \"secret\"}");
        Files.setPosixFilePermissions(path(), PosixFilePermissions.fromString("-w-------"));
        assumeFalse(Files.isReadable(path()), "root reads any file");

        assertThat(file().read()).isInstanceOfSatisfying(Stored.Unusable.class,
                unusable -> assertThat(unusable.reason()).isEqualTo(Stored.Reason.UNREADABLE));
        file().write("new");
        assertThat(folder.resolve("data.json.damaged-20261017-143000")).exists();
        assertThat(file().read()).isEqualTo(new Stored.Found<>("new"));
    }

    @Test
    void aThirdFileKeptAsideInTheSameSecondGetsTheNextNumber() throws IOException {
        for (var damage : new String[] {"first damage", "second damage", "third damage"}) {
            Files.writeString(path(), damage);
            file().write("new");
        }

        assertThat(folder.resolve("data.json.damaged-20261017-143000")).hasContent("first damage");
        assertThat(folder.resolve("data.json.damaged-20261017-143000-2")).hasContent("second damage");
        assertThat(folder.resolve("data.json.damaged-20261017-143000-3")).hasContent("third damage");
    }

    @Test
    void whatTheParserSaysIsShownForAFileWithoutSecrets() throws IOException {
        Files.writeString(path(), "{\"text\": oops}");

        assertThat(file().read()).isInstanceOfSatisfying(Stored.Unusable.class,
                unusable -> assertThat(unusable.detail()).startsWith("not valid JSON (line 1").contains(": ")
                        .contains("oops"));
    }

    @Test
    void aFileThatCannotBeUsedIsLoggedOnceAndSoIsKeepingItAside() throws IOException {
        Files.writeString(path(), "{\"text\": 5}");
        var file = file();
        try (var log = LogCapture.of(StoredFile.class)) {
            file.read();
            file.read();
            file.write("new");

            assertThat(log.lines()).containsExactly(
                    "WARNING " + path() + " cannot be used: text: must be a text",
                    "WARNING " + path() + " could not be used (text: must be a text), so it is kept as "
                            + folder.resolve("data.json.damaged-20261017-143000") + " before a new one is written");
        }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void aFileInAFolderThatCannotBeEnteredIsUnreadable() throws IOException {
        var locked = Files.createDirectory(folder.resolve("locked"));
        Files.writeString(locked.resolve("data.json"), "{\"text\": \"hello\"}");
        Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("---------"));
        try {
            assumeFalse(Files.isReadable(locked), "root enters any folder");
            var file = new StoredFile<>(locked.resolve("data.json"), new TextFormat(), CLOCK);

            assertThat(file.read()).isInstanceOfSatisfying(Stored.Unusable.class,
                    unusable -> assertThat(unusable.reason()).isEqualTo(Stored.Reason.UNREADABLE));
        } finally {
            Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rwx------"));
        }
    }
}

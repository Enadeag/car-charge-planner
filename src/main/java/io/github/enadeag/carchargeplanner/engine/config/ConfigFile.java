package io.github.enadeag.carchargeplanner.engine.config;

import io.github.enadeag.carchargeplanner.engine.store.FormatProblem;
import io.github.enadeag.carchargeplanner.engine.store.PrivateFiles;
import io.github.enadeag.carchargeplanner.engine.store.StoredJson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import tools.jackson.core.JacksonException;

/**
 * The engine's own config file, in JSON, read and written whole in the shape {@link ConfigFormat} gives. Every value
 * must be given, except the {@code away} and {@code typical} sections: a file without them, such as one written before
 * they existed, has no away periods and no typical values.
 */
public final class ConfigFile {

    private final Path path;

    /**
     * Makes the reader and writer for one config file; nothing is read yet.
     *
     * @param path where the file is, or should be; it need not exist, a missing file is reported when it is read
     */
    public ConfigFile(Path path) {
        this.path = path;
    }

    /**
     * Where the file is, for messages to the owner.
     *
     * @return the path this reader was made with
     */
    public Path path() {
        return path;
    }

    /**
     * Reads and checks the file again; nothing is kept between reads.
     *
     * @return the whole config, every value present and valid
     * @throws ConfigProblem when the file is missing, unreadable, not JSON, or a value is missing or wrong; the problem
     *     names the file and the setting
     */
    public EngineConfig read() throws ConfigProblem {
        String text;
        try {
            text = Files.readString(path);
        } catch (NoSuchFileException e) {
            throw new ConfigProblem(ConfigProblem.Kind.MISSING, path, "");
        } catch (IOException e) {
            throw new ConfigProblem(ConfigProblem.Kind.UNREADABLE, path, String.valueOf(e.getMessage()));
        }
        try {
            return ConfigFormat.read(StoredJson.parse(text));
        } catch (JacksonException e) {
            throw new ConfigProblem(ConfigProblem.Kind.INVALID, path, e.getOriginalMessage());
        } catch (FormatProblem e) {
            throw new ConfigProblem(ConfigProblem.Kind.INVALID, path, e.getMessage());
        }
    }

    /**
     * Replaces the file by this config, in the form {@link #read()} reads, through {@link PrivateFiles}: a reader sees
     * the old file or the new one, never half of one, and only the owner can read it.
     *
     * @param config the whole config to keep; its records have checked their values already
     * @throws IOException when the file or its folder could not be written; the file is then left as it was
     */
    public void write(EngineConfig config) throws IOException {
        PrivateFiles.write(path, StoredJson.print(ConfigFormat.write(config)));
    }
}

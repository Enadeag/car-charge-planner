package nl.enadeag.carchargeplanner.engine.config;

import nl.enadeag.carchargeplanner.engine.store.Stored;
import nl.enadeag.carchargeplanner.engine.store.StoredFile;
import java.io.IOException;
import java.nio.file.Path;

/**
 * The engine's own config file, in JSON, read and written whole in the shape {@link ConfigFormat} gives. Every value
 * must be given, except the {@code away} and {@code typical} sections: a file without them, such as one written before
 * they existed, has no away periods and no typical values. The config is kept in memory until the file changes on
 * disk; a file that cannot be used is renamed, not overwritten, when a new config is written (see {@link
 * StoredFile}).
 */
public final class ConfigFile {

    private final StoredFile<EngineConfig> file;

    /**
     * Makes the reader and writer for one config file; nothing is read yet.
     *
     * @param path where the file is, or should be; it need not exist, a missing file is reported when it is read
     */
    public ConfigFile(Path path) {
        this.file = new StoredFile<>(path, new ConfigFormat());
    }

    /**
     * Where the file is, for messages to the owner.
     *
     * @return the path this reader was made with
     */
    public Path path() {
        return file.path();
    }

    /**
     * The config the file holds, read again when the file changed since it was last read or written.
     *
     * @return the whole config, every value present and valid
     * @throws ConfigProblem when the file is missing, unreadable, not JSON, written by a newer version of the app, or a
     *     value is missing or wrong; the problem names the file and the setting
     */
    public EngineConfig read() throws ConfigProblem {
        return switch (file.read()) {
            case Stored.Found<EngineConfig> found -> found.value();
            case Stored.Absent<EngineConfig> absent -> throw new ConfigProblem(ConfigProblem.Kind.MISSING, path(), "");
            case Stored.Unusable<EngineConfig> unusable -> throw new ConfigProblem(
                    unusable.reason() == Stored.Reason.UNREADABLE ? ConfigProblem.Kind.UNREADABLE
                            : ConfigProblem.Kind.INVALID,
                    path(), unusable.detail());
        };
    }

    /**
     * Replaces the file by this config, in the form {@link #read()} reads: a reader sees the old file or the new one,
     * never half of one, and only the owner can read it. A file that could not be used is kept as
     * {@code <name>.damaged-<time>}.
     *
     * @param config the whole config to keep; its records have checked their values already
     * @throws IOException when the file or its folder could not be written, or an unusable file not renamed; the file
     *     is then left as it was
     */
    public void write(EngineConfig config) throws IOException {
        file.write(config);
    }
}

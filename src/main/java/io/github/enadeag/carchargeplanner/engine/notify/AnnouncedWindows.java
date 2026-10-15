package io.github.enadeag.carchargeplanner.engine.notify;

import io.github.enadeag.carchargeplanner.engine.plan.Window;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * The windows the owner has been told about, in a file, so a restart neither announces them again nor plans them
 * differently while they run. A file that cannot be read counts as empty; one that cannot be written does not stop
 * planning.
 */
public final class AnnouncedWindows {

    private static final TypeReference<List<Window>> WINDOWS = new TypeReference<>() {
    };

    private final Path file;
    private final JsonMapper json = new JsonMapper();

    /**
     * Makes the store; nothing is read or written yet.
     *
     * @param file the file the windows are kept in; it and its folder are made when the first window is saved
     */
    public AnnouncedWindows(Path file) {
        this.file = file;
    }

    /**
     * The windows announced so far.
     *
     * @return the windows in the order they were announced; empty when there is no file or it cannot be read
     */
    public List<Window> load() {
        try {
            return List.copyOf(json.readValue(Files.readString(file), WINDOWS));
        } catch (IOException | JacksonException | NullPointerException e) {
            // a missing or damaged file: at worst a window is announced once more
            return List.of();
        }
    }

    /**
     * Replaces the windows in the file.
     *
     * @param windows the windows to keep, possibly none
     */
    public void save(List<Window> windows) {
        var temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(temporary, json.writeValueAsString(windows));
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | JacksonException e) {
            // the windows are kept for this run all the same: the announcer remembers them itself
        }
    }
}

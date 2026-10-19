package nl.enadeag.carchargeplanner;

import nl.enadeag.carchargeplanner.engine.store.PrivateFiles;
import java.io.IOException;
import java.lang.System.Logger.Level;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Where the API keys' private file is, for when there is no usable keychain: beside the config on Linux, and on
 * Windows in the local data folder, as a roaming profile would copy the roaming one to other computers. Versions
 * before 0.4 kept it beside the config on Windows too; such a file is moved once.
 */
final class SecretsLocation {

    private static final System.Logger LOG = System.getLogger(SecretsLocation.class.getName());

    /** The file's name. */
    static final String FILE_NAME = "secrets.json";

    private SecretsLocation() {
    }

    /**
     * Where the file is on this system.
     *
     * @param os the system the app runs on
     * @param configPath the engine's config file
     * @param dataFolder the app's data folder, such as {@code %LOCALAPPDATA%\car-charge-planner}
     * @return the file in the data folder on Windows, and beside the config elsewhere; it need not exist
     */
    static Path file(Os os, Path configPath, Path dataFolder) {
        return os == Os.WINDOWS ? dataFolder.resolve(FILE_NAME) : configPath.resolveSibling(FILE_NAME);
    }

    /**
     * Moves the file from beside the config, where versions before 0.4 kept it on Windows, to {@link #file}, unless a
     * file is there already. The move is one step where the system can do that, which keeps the file's access rights;
     * otherwise it is copied with them and the old one removed, and when that cannot be removed the copy is removed
     * again, so there is never a second copy of the keys. What it did, or why not, is logged; it never throws.
     *
     * @param os the system the app runs on; nothing moves elsewhere than on Windows
     * @param configPath the engine's config file, beside which the old file is
     * @param dataFolder the app's data folder; made, private, when it is missing
     * @return where the file is to be used from: the new place, or the old one when it could not be moved
     */
    static Path moveFromOldPlace(Os os, Path configPath, Path dataFolder) {
        var now = file(os, configPath, dataFolder);
        var old = configPath.resolveSibling(FILE_NAME);
        if (os != Os.WINDOWS || old.toAbsolutePath().equals(now.toAbsolutePath())
                || !Files.isRegularFile(old, LinkOption.NOFOLLOW_LINKS)) {
            return now;
        }
        if (Files.exists(now, LinkOption.NOFOLLOW_LINKS)) {
            LOG.log(Level.WARNING, "the API keys are kept in " + now + "; " + old + " from an older version is not "
                    + "used, delete it once you no longer need it");
            return now;
        }
        try {
            PrivateFiles.createFolders(now.toAbsolutePath().getParent());
            try {
                Files.move(old, now, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                copyAndRemove(old, now);
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "could not move " + old + " to " + now + ", so it is used where it is ("
                    + e.getClass().getSimpleName() + ")");
            return old;
        }
        LOG.log(Level.INFO, "moved " + old + " to " + now + ", which stays on this computer");
        return now;
    }

    /** Copies the file with its access rights, then removes the old one, or the copy again when that fails. */
    private static void copyAndRemove(Path old, Path now) throws IOException {
        Files.copy(old, now, StandardCopyOption.COPY_ATTRIBUTES);
        try {
            Files.delete(old);
        } catch (IOException e) {
            Files.deleteIfExists(now);
            throw e;
        }
    }
}

package nl.enadeag.carchargeplanner.engine.store;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A platform's file privacy as the engine's tests see it, without file modes: it keeps which paths are open to
 * others, closes them when asked to tighten, and records the paths it gave attributes for, so a test can tell what
 * was made private without a platform that has modes. It can also refuse, like a platform that cannot change a file.
 */
public final class RecordingFilePrivacy implements FilePrivacy {

    private final Set<Path> openToOthers = ConcurrentHashMap.newKeySet();
    private final List<Path> files = new CopyOnWriteArrayList<>();
    private final List<Path> folders = new CopyOnWriteArrayList<>();
    private volatile IOException refusal;

    @Override
    public List<FileAttribute<?>> newFileAttributes(Path file) {
        files.add(file.toAbsolutePath());
        return List.of();
    }

    @Override
    public List<FileAttribute<?>> newFolderAttributes(Path folder) {
        folders.add(folder.toAbsolutePath());
        return List.of();
    }

    @Override
    public boolean tighten(Path path) throws IOException {
        if (refusal != null) {
            throw refusal;
        }
        if (!Files.exists(path)) {
            throw new NoSuchFileException(path.toString());
        }
        return openToOthers.remove(path.toAbsolutePath());
    }

    /**
     * Treats a path as readable by others, until it is tightened.
     *
     * @param path the file or folder
     */
    public void openToOthers(Path path) {
        openToOthers.add(path.toAbsolutePath());
    }

    /**
     * Whether others could still read a path.
     *
     * @param path the file or folder
     * @return true when it was opened and not tightened since
     */
    public boolean isOpenToOthers(Path path) {
        return openToOthers.contains(path.toAbsolutePath());
    }

    /**
     * Makes every later tightening fail, as on a file the platform cannot change.
     *
     * @param problem what tightening throws
     */
    public void refuse(IOException problem) {
        refusal = problem;
    }

    /**
     * The files it gave attributes for, oldest first.
     *
     * @return their absolute paths
     */
    public List<Path> filesMade() {
        return List.copyOf(files);
    }

    /**
     * The folders it gave attributes for, oldest first.
     *
     * @return their absolute paths
     */
    public List<Path> foldersMade() {
        return List.copyOf(folders);
    }
}

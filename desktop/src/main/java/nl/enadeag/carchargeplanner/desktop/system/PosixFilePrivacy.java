package nl.enadeag.carchargeplanner.desktop.system;

import nl.enadeag.carchargeplanner.engine.store.FilePrivacy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Set;

/**
 * The desktop's file privacy: where a path's file system has file modes (Linux, macOS) a folder is {@code rwx------}
 * and a file {@code rw-------}, readable by its owner only; where it has none, as on Windows, nothing is added or
 * changed, as the user's own profile folders keep the app's files from other users. Decided per path, as a file
 * system without modes can be mounted on Linux too.
 */
public final class PosixFilePrivacy implements FilePrivacy {

    private static final Set<PosixFilePermission> PRIVATE_FOLDER = PosixFilePermissions.fromString("rwx------");
    private static final Set<PosixFilePermission> PRIVATE_FILE = PosixFilePermissions.fromString("rw-------");

    /** Makes the desktop's file privacy; it holds nothing. */
    public PosixFilePrivacy() {
    }

    @Override
    public List<FileAttribute<?>> newFileAttributes(Path file) {
        return hasModes(file) ? List.of(PosixFilePermissions.asFileAttribute(PRIVATE_FILE)) : List.of();
    }

    @Override
    public List<FileAttribute<?>> newFolderAttributes(Path folder) {
        return hasModes(folder) ? List.of(PosixFilePermissions.asFileAttribute(PRIVATE_FOLDER)) : List.of();
    }

    @Override
    public boolean tighten(Path path) throws IOException {
        if (!hasModes(path)) {
            return false;
        }
        var attributes = Files.readAttributes(path, PosixFileAttributes.class);
        var mode = attributes.isDirectory() ? PRIVATE_FOLDER : PRIVATE_FILE;
        if (attributes.permissions().equals(mode)) {
            return false;
        }
        Files.setPosixFilePermissions(path, mode);
        return true;
    }

    private static boolean hasModes(Path path) {
        return path.getFileSystem().supportedFileAttributeViews().contains("posix");
    }
}

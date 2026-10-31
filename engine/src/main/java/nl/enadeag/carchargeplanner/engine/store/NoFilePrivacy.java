package nl.enadeag.carchargeplanner.engine.store;

import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.List;

/**
 * {@link FilePrivacy#NONE}: adds no attributes and changes nothing, for a platform that keeps the app's files private
 * already.
 */
final class NoFilePrivacy implements FilePrivacy {

    /** Makes the one instance, {@link FilePrivacy#NONE}. */
    NoFilePrivacy() {
    }

    @Override
    public List<FileAttribute<?>> newFileAttributes(Path file) {
        return List.of();
    }

    @Override
    public List<FileAttribute<?>> newFolderAttributes(Path folder) {
        return List.of();
    }

    @Override
    public boolean tighten(Path path) {
        return false;
    }
}

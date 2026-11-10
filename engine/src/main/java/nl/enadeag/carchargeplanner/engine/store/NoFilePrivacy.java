package nl.enadeag.carchargeplanner.engine.store;

import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.List;

final class NoFilePrivacy implements FilePrivacy {

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

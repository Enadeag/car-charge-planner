package io.github.enadeag.carchargeplanner.instance;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.Optional;
import java.util.Set;

/**
 * The folder of the instance socket, which only its user may use. Where files have an owner and a mode (Linux) it must
 * be owned by the user and have mode 0700; on Windows it lies in the user's own application data folder, which only
 * the user, the system and administrators may open. Everywhere it must be a real folder, not a link or junction that
 * could lead elsewhere.
 */
final class PrivateFolder {

    private static final Set<PosixFilePermission> OWNER_ONLY = PosixFilePermissions.fromString("rwx------");

    private final Path path;
    private final Optional<UserPrincipal> user;

    /**
     * Makes the folder's checker; nothing is made or checked yet.
     *
     * @param path the folder; its parent must exist
     * @param user the user this app runs as, who must own the folder, where files have an owner and a mode; empty on
     *     Windows, where a folder made by an administrator's process is owned by the administrators instead
     */
    PrivateFolder(Path path, Optional<UserPrincipal> user) {
        this.path = path;
        this.user = user;
    }

    /**
     * Makes the folder when it is missing, then checks it, also when it was there already.
     *
     * @return the folder
     * @throws IOException when it cannot be made, or another user or the whole world could use it
     */
    Path make() throws IOException {
        try {
            if (user.isPresent()) {
                Files.createDirectory(path, PosixFilePermissions.asFileAttribute(OWNER_ONLY));
            } else {
                Files.createDirectory(path);
            }
        } catch (FileAlreadyExistsException e) {
            // checked below like a new one
        }
        var attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isDirectory() || attributes.isSymbolicLink() || attributes.isOther()) {
            throw new IOException(path + " is not a folder but a link or another kind of file");
        }
        if (user.isPresent()) {
            var posix = Files.readAttributes(path, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!posix.owner().equals(user.get())) {
                throw new IOException(path + " belongs to another user");
            }
            if (!posix.permissions().equals(OWNER_ONLY)) {
                throw new IOException(path + " is open to other users: its mode is not 0700");
            }
        }
        return path;
    }
}

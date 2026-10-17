package io.github.enadeag.carchargeplanner.instance;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.Optional;
import java.util.Set;

/**
 * A folder only its user may use: the instance socket's, and the composition root's folders for the icons the tray and
 * the notifications hand to other programs. Where files have an owner and a mode (Linux) it must
 * be owned by the user and have mode 0700; on Windows it lies in the user's own application data folder, which only
 * the user, the system and administrators may open. Everywhere it must be a real folder, not a link or junction that
 * could lead elsewhere.
 */
public final class PrivateFolder {

    private static final Set<PosixFilePermission> OWNER_ONLY = PosixFilePermissions.fromString("rwx------");

    private final Path path;
    private final Optional<UserPrincipal> user;

    /**
     * Makes the folder's checker; nothing is made or checked yet.
     *
     * @param path the folder; the folders above it that are missing are made too, with mode 0700 where files have one
     * @param user the user this app runs as, who must own the folder, where files have an owner and a mode; empty on
     *     Windows, where a folder made by an administrator's process is owned by the administrators instead
     */
    public PrivateFolder(Path path, Optional<UserPrincipal> user) {
        this.path = path;
        this.user = user;
    }

    /**
     * Makes the folder when it is missing, then checks it, also when it was there already.
     *
     * @return the folder
     * @throws NotPrivateException when it is a link or another kind of file, or another user or the whole world could
     *     use it
     * @throws IOException when it cannot be made
     */
    public Path make() throws IOException {
        var parent = path.toAbsolutePath().getParent();
        if (parent != null && !Files.isDirectory(parent)) {
            Files.createDirectories(parent, attributes());
        }
        try {
            Files.createDirectory(path, attributes());
        } catch (FileAlreadyExistsException e) {
            // checked below like a new one
        }
        var attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isDirectory() || attributes.isSymbolicLink() || attributes.isOther()) {
            throw new NotPrivateException(path + " is not a folder but a link or another kind of file");
        }
        if (user.isPresent()) {
            var posix = Files.readAttributes(path, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!posix.owner().equals(user.get())) {
                throw new NotPrivateException(path + " belongs to another user");
            }
            if (!posix.permissions().equals(OWNER_ONLY)) {
                throw new NotPrivateException(path + " is open to other users: its mode is not 0700");
            }
        }
        return path;
    }

    private FileAttribute<?>[] attributes() {
        return user.isPresent()
                ? new FileAttribute<?>[] {PosixFilePermissions.asFileAttribute(OWNER_ONLY)}
                : new FileAttribute<?>[0];
    }
}

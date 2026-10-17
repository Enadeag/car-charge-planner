package io.github.enadeag.carchargeplanner.instance;

import java.io.IOException;
import java.lang.System.Logger.Level;
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
 * the notifications hand to other programs. Where files have an owner and a mode (Linux) it must be owned by the user
 * and have mode 0700; the user's own folder that others may read but not write in is tightened to that. On Windows it
 * lies in the user's own application data folder, which only the user, the system and administrators may open.
 * Everywhere it must be a real folder, not a link or junction that could lead elsewhere.
 */
public final class PrivateFolder {

    private static final System.Logger LOG = System.getLogger(PrivateFolder.class.getName());
    private static final Set<PosixFilePermission> OWNER_ONLY = PosixFilePermissions.fromString("rwx------");
    private static final Set<PosixFilePermission> WRITE_BY_OTHERS =
            Set.of(PosixFilePermission.GROUP_WRITE, PosixFilePermission.OTHERS_WRITE);

    private final Path path;
    private final Optional<UserPrincipal> user;
    private final ModeChange modeChange;

    /** Changes a folder's mode; a seam for the tests, which make it fail or swap the folder. */
    @FunctionalInterface
    interface ModeChange {

        /**
         * Sets the mode.
         *
         * @param folder the folder, checked to be the user's own and not a link
         * @param mode the mode it gets
         * @throws IOException when the mode cannot be changed
         */
        void set(Path folder, Set<PosixFilePermission> mode) throws IOException;
    }

    /**
     * Makes the folder's checker; nothing is made or checked yet.
     *
     * @param path the folder; the folders above it that are missing are made too, with mode 0700 where files have one
     * @param user the user this app runs as, who must own the folder, where files have an owner and a mode; empty on
     *     Windows, where a folder made by an administrator's process is owned by the administrators instead
     */
    public PrivateFolder(Path path, Optional<UserPrincipal> user) {
        this(path, user, Files::setPosixFilePermissions);
    }

    /**
     * Makes the folder's checker with its own way of changing a folder's mode, for the tests.
     *
     * @param path the folder; the folders above it that are missing are made too
     * @param user the user this app runs as, who must own the folder; empty where files have no owner and mode
     * @param modeChange how a folder's mode is changed
     */
    PrivateFolder(Path path, Optional<UserPrincipal> user, ModeChange modeChange) {
        this.path = path;
        this.user = user;
        this.modeChange = modeChange;
    }

    /**
     * Makes the folder when it is missing, then checks it, also when it was there already. The user's own folder that
     * others may read but not write in is made owner-only, and that is logged: nobody else can have put a file in it.
     *
     * @return the folder
     * @throws NotPrivateException when it is a link or another kind of file, another user's, or one that others may
     *     write in, whose files cannot be trusted; or when making it owner-only fails, or it is no longer the same
     *     owner-only folder afterwards
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
                tighten(posix);
            }
        }
        return path;
    }

    /** The user's own folder with a wider mode: their own mistake, or an older version's, put right when it is safe. */
    private void tighten(PosixFileAttributes before) throws NotPrivateException {
        var mode = PosixFilePermissions.toString(before.permissions());
        if (before.permissions().stream().anyMatch(WRITE_BY_OTHERS::contains)) {
            throw new NotPrivateException(
                    path + " is open to other users, who may write in it: its mode is " + mode + ", not 0700");
        }
        PosixFileAttributes after;
        try {
            modeChange.set(path, OWNER_ONLY);
            after = Files.readAttributes(path, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException e) {
            throw new NotPrivateException(path + " is open to other users and cannot be made owner-only: " + e);
        }
        // another folder may have been put in its place between the check and the change
        if (!after.isDirectory() || after.isSymbolicLink() || !after.owner().equals(user.orElseThrow())
                || !after.permissions().equals(OWNER_ONLY) || before.fileKey() == null
                || !before.fileKey().equals(after.fileKey())) {
            throw new NotPrivateException(path + " is not the same owner-only folder after making it owner-only");
        }
        LOG.log(Level.WARNING, "tightened " + path + " to owner-only: its mode was " + mode);
    }

    private FileAttribute<?>[] attributes() {
        return user.isPresent()
                ? new FileAttribute<?>[] {PosixFilePermissions.asFileAttribute(OWNER_ONLY)}
                : new FileAttribute<?>[0];
    }
}

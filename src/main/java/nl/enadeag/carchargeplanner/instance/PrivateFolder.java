package nl.enadeag.carchargeplanner.instance;

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
    private final Owner owner;
    private final ModeChange modeChange;
    private final EntryOwner entryOwner;

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
     * Tells who owns a file in the folder, or the folder's parent; a seam for the tests, as only root can make a file
     * of another user.
     */
    @FunctionalInterface
    interface EntryOwner {

        /**
         * The file's owner, without following a link.
         *
         * @param entry a file in the folder, of any kind, or the folder's parent
         * @return its owner
         * @throws IOException when it cannot be read
         */
        UserPrincipal of(Path entry) throws IOException;
    }

    /**
     * Makes the folder's checker; nothing is made or checked yet.
     *
     * @param path the folder; the folders above it that are missing are made too, with mode 0700 where files have one
     * @param owner the user this app runs as, who must own the folder, where files have an owner and a mode;
     *     unchecked on Windows, where a folder made by an administrator's process is owned by the administrators
     *     instead
     */
    public PrivateFolder(Path path, Owner owner) {
        this(path, owner, Files::setPosixFilePermissions, entry -> Files.getOwner(entry, LinkOption.NOFOLLOW_LINKS));
    }

    /**
     * Makes the folder's checker with its own way of changing a folder's mode and of telling a file's owner, for the
     * tests.
     *
     * @param path the folder; the folders above it that are missing are made too
     * @param owner the user this app runs as, who must own the folder; unchecked where files have no owner and mode
     * @param modeChange how a folder's mode is changed
     * @param entryOwner how the owner of a file in the folder is told
     */
    PrivateFolder(Path path, Owner owner, ModeChange modeChange, EntryOwner entryOwner) {
        this.path = path;
        this.owner = owner;
        this.modeChange = modeChange;
        this.entryOwner = entryOwner;
    }

    /**
     * Makes the folder when it is missing, then checks it, also when it was there already. The user's own folder that
     * others may read but not write in is made owner-only, and that is logged: nobody else can have put a file in it.
     * Only the folder, its files and its parent are checked: the folder lives in the user's own runtime or data
     * folder, and someone who can write in the user's own folders above it controls the account already, so deeper
     * checks are deliberately not done.
     *
     * @return the folder
     * @throws NotPrivateException when it is a link or another kind of file, another user's, or one that others may
     *     write in, whose files cannot be trusted; or, when it is to be made owner-only, when it holds a file of
     *     another user or one with another name (a hard link), lies in a folder others may write in, the change
     *     fails, or it is no longer the same owner-only folder afterwards
     * @throws IOException when it cannot be made
     */
    public Path make() throws IOException {
        var parent = path.toAbsolutePath().getParent();
        if (parent != null && !Files.isDirectory(parent)) {
            Files.createDirectories(parent, attributes());
        }
        try {
            Files.createDirectory(path, attributes());
        } catch (FileAlreadyExistsException _) {
            // checked below like a new one
        }
        var attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isDirectory() || attributes.isSymbolicLink() || attributes.isOther()) {
            throw new NotPrivateException(path + " is not a folder but a link or another kind of file");
        }
        if (owner instanceof Owner.User(var user)) {
            var posix = Files.readAttributes(path, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!posix.owner().equals(user)) {
                throw new NotPrivateException(path + " belongs to another user");
            }
            if (!posix.permissions().equals(OWNER_ONLY)) {
                tighten(posix, user);
            }
        }
        return path;
    }

    /**
     * The user's own folder with a wider mode: their own mistake, or an older version's, put right when it is safe.
     * Java cannot change the mode of a folder it holds open, so another folder could take this one's place between
     * the checks and the change. That needs write access to the parent folder, so a parent others may write in is
     * refused; and after the change the folder must still be the same one (its file key), owner-only and not a link.
     * A folder others could once write in, and that was narrowed later, may still hold a file of theirs, so every
     * file in it must be the user's and have no other name (a hard link).
     */
    private void tighten(PosixFileAttributes before, UserPrincipal user) throws NotPrivateException {
        var mode = PosixFilePermissions.toString(before.permissions());
        if (before.permissions().stream().anyMatch(WRITE_BY_OTHERS::contains)) {
            throw new NotPrivateException(
                    path + " is open to other users, who may write in it: its mode is " + mode + ", not 0700");
        }
        PosixFileAttributes after;
        try {
            var parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                refuseUnsafeParent(parent, user);
            }
            refuseOthersFiles(user);
            modeChange.set(path, OWNER_ONLY);
            after = Files.readAttributes(path, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (NotPrivateException e) {
            throw e;
        } catch (IOException e) {
            throw new NotPrivateException(path + " is open to other users and cannot be made owner-only: " + e);
        }
        if (!after.isDirectory() || after.isSymbolicLink() || !after.owner().equals(user)
                || !after.permissions().equals(OWNER_ONLY) || before.fileKey() == null
                || !before.fileKey().equals(after.fileKey())) {
            throw new NotPrivateException(path + " is not the same owner-only folder after making it owner-only");
        }
        LOG.log(Level.WARNING, "tightened " + path + " to owner-only: its mode was " + mode);
    }

    private void refuseUnsafeParent(Path parent, UserPrincipal user) throws IOException {
        var attributes = Files.readAttributes(parent, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isDirectory() || attributes.isSymbolicLink()) {
            throw new NotPrivateException(path + " is open to other users and lies in " + parent
                    + ", which is a link or not a folder, so it is not made owner-only");
        }
        var owner = entryOwner.of(parent);
        var root = parent.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName("root");
        if (!owner.equals(user) && !owner.equals(root)) {
            throw new NotPrivateException(path + " is open to other users and lies in " + parent
                    + ", which belongs to another user, so it is not made owner-only");
        }
        if (attributes.permissions().stream().anyMatch(WRITE_BY_OTHERS::contains)) {
            throw new NotPrivateException(path + " is open to other users and lies in " + parent
                    + ", which other users may write in, so it is not made owner-only");
        }
    }

    private void refuseOthersFiles(UserPrincipal user) throws IOException {
        try (var entries = Files.list(path)) {
            for (var entry : (Iterable<Path>) entries::iterator) {
                if (!entryOwner.of(entry).equals(user)) {
                    throw new NotPrivateException(
                            path + " holds " + entry.getFileName() + ", which belongs to another user");
                }
                // a folder always has more than one name ("." and its entry); only other files can be hard links
                if (!Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS) && names(entry) > 1) {
                    throw new NotPrivateException(path + " holds " + entry.getFileName() + ", which has another name");
                }
            }
        }
    }

    private static int names(Path entry) throws IOException {
        return ((Number) Files.getAttribute(entry, "unix:nlink", LinkOption.NOFOLLOW_LINKS)).intValue();
    }

    private FileAttribute<?>[] attributes() {
        return owner instanceof Owner.User
                ? new FileAttribute<?>[] {PosixFilePermissions.asFileAttribute(OWNER_ONLY)}
                : new FileAttribute<?>[0];
    }
}

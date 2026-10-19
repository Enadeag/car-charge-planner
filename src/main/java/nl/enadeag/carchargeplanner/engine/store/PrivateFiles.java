package nl.enadeag.carchargeplanner.engine.store;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.System.Logger.Level;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

/**
 * Writes the app's files so that no reader sees half a file, a crash leaves the old file or the new one, and only the
 * owner can read them. Where files have modes (Linux, macOS) a folder the app makes is {@code rwx------} and every file
 * it writes {@code rw-------}, both made with that mode, so there is no moment when others could read them; on Windows
 * the user's own profile folders already keep them from other users.
 */
public final class PrivateFiles {

    private static final System.Logger LOG = System.getLogger(PrivateFiles.class.getName());
    private static final Set<PosixFilePermission> PRIVATE_FOLDER = PosixFilePermissions.fromString("rwx------");
    private static final Set<PosixFilePermission> PRIVATE_FILE = PosixFilePermissions.fromString("rw-------");

    private PrivateFiles() {
    }

    /**
     * Replaces a file by this text, in UTF-8. The text goes to {@code <name>.tmp} beside the file, made private, and
     * is flushed to the disk before it is moved over the file in one step; then the folder is flushed where the
     * system allows it, so the move survives a power cut too. Missing folders are made private; a folder that exists
     * is left as it is.
     *
     * @param file the file to replace or make; its folder need not exist
     * @param text the whole new content
     * @throws IOException when a folder, the temporary file or the move failed; the file is then left as it was
     */
    public static void write(Path file, String text) throws IOException {
        var target = file.toAbsolutePath();
        var folder = target.getParent();
        createFolders(folder);
        var temporary = target.resolveSibling(target.getFileName() + ".tmp");
        if (!Files.isDirectory(temporary, LinkOption.NOFOLLOW_LINKS)) {
            Files.deleteIfExists(temporary); // a leftover may be open to others; one made now is private
        }
        try (var channel = FileChannel.open(temporary, Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                privateMode(folder, PRIVATE_FILE))) {
            var bytes = ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8));
            while (bytes.hasRemaining()) {
                channel.write(bytes);
            }
            channel.force(true);
        } catch (IOException e) {
            if (Files.isRegularFile(temporary, LinkOption.NOFOLLOW_LINKS)) {
                deleteQuietly(temporary);
            }
            throw e;
        }
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            deleteQuietly(temporary);
            throw e;
        }
        flushFolder(folder);
    }

    /**
     * Opens a file to add to its end, such as a log. A file that is missing is made private as it is made, so there is
     * no moment when others could read it, and so are its missing folders; a file that exists keeps its mode.
     *
     * @param file the file to add to; its folder need not exist
     * @return the stream that writes at the file's end; the caller closes it
     * @throws IOException when a folder or the file could not be made or opened
     */
    public static OutputStream append(Path file) throws IOException {
        var target = file.toAbsolutePath();
        var folder = target.getParent();
        createFolders(folder);
        return Channels.newOutputStream(FileChannel.open(target,
                Set.of(StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND),
                privateMode(folder, PRIVATE_FILE)));
    }

    /**
     * Makes a folder and the folders above it that are missing, each private. Folders that exist are left as they
     * are.
     *
     * @param folder the folder that must exist
     * @throws IOException when a folder could not be made, or a file is in the way
     */
    public static void createFolders(Path folder) throws IOException {
        if (!Files.isDirectory(folder)) {
            Files.createDirectories(folder, privateMode(folder, PRIVATE_FOLDER));
        }
    }

    /**
     * Makes an existing folder of the app private, where folders have modes, for folders made before the app made
     * them private. A folder that is missing, or whose mode cannot be changed, is left as it is; the latter is
     * logged.
     *
     * @param folder one of the app's own folders, never one the owner named, such as with {@code --config=}
     */
    public static void tighten(Path folder) {
        if (!hasModes(folder) || !Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try {
            if (!Files.getPosixFilePermissions(folder).equals(PRIVATE_FOLDER)) {
                Files.setPosixFilePermissions(folder, PRIVATE_FOLDER);
                LOG.log(Level.INFO, "made " + folder + " readable only by you");
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "could not make " + folder + " readable only by you: " + e.getMessage());
        }
    }

    private static boolean hasModes(Path path) {
        return path.getFileSystem().supportedFileAttributeViews().contains("posix");
    }

    private static FileAttribute<?>[] privateMode(Path path, Set<PosixFilePermission> mode) {
        return hasModes(path) ? new FileAttribute<?>[] {PosixFilePermissions.asFileAttribute(mode)}
                : new FileAttribute<?>[0];
    }

    /** Where a folder can be opened, as on Linux; Windows refuses to, and keeps its own journal of the move. */
    private static void flushFolder(Path folder) {
        try (var channel = FileChannel.open(folder, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException e) {
            // the file itself is on the disk already; only the move could be lost in a power cut
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            // a leftover temporary file is removed by the next write
        }
    }
}

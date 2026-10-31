package nl.enadeag.carchargeplanner.engine.store;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes the app's files so that no reader sees half a file, a crash leaves the old file or the new one, and only the
 * owner can read them. Every folder and file it makes is made with the attributes of the platform's
 * {@link FilePrivacy}, so there is no moment when others could read them; how private that is, is the platform's
 * part: on the desktop a folder is {@code rwx------} and a file {@code rw-------} where files have modes (Linux,
 * macOS), and on Windows the user's own profile folders keep them from other users. The rest, the temporary file, the
 * flush to the disk and the move in one step, is the same on every platform. Safe to use from several threads, as
 * long as no two write the same file at once.
 */
public final class PrivateFiles {

    private static final Logger LOG = LoggerFactory.getLogger(PrivateFiles.class);

    private final FilePrivacy privacy;
    /** The files already reported as replaced by a plain move, so each is logged once. */
    private final Set<Path> movedPlainly = ConcurrentHashMap.newKeySet();

    /**
     * Makes the writer for one platform.
     *
     * @param privacy how the platform makes a file or folder private; never null, {@link FilePrivacy#NONE} where its
     *     app storage is private already
     */
    public PrivateFiles(FilePrivacy privacy) {
        this.privacy = privacy;
    }

    /**
     * Replaces a file by this text, in UTF-8. The text goes to {@code <name>.tmp} beside the file, made private, and
     * is flushed to the disk before it is moved over the file in one step; then the folder is flushed where the
     * system allows it, so the move survives a power cut too. On a file system that cannot move in one step the file
     * is replaced by a plain move, which is logged: a crash during that move may leave the file missing or half
     * written, with the whole text still in the temporary file. Missing folders are made private; a folder that
     * exists is left as it is.
     *
     * @param file the file to replace or make; its folder need not exist
     * @param text the whole new content
     * @throws IOException when a folder, the temporary file or the move failed; the file is then left as it was, except
     *     when the plain move failed after the file was gone: then the file is missing and the whole text is kept in
     *     {@code <name>.tmp}
     */
    public void write(Path file, String text) throws IOException {
        var target = file.toAbsolutePath();
        var folder = target.getParent();
        createFolders(folder);
        var temporary = target.resolveSibling(target.getFileName() + ".tmp");
        if (!Files.isDirectory(temporary, LinkOption.NOFOLLOW_LINKS)) {
            Files.deleteIfExists(temporary); // a leftover may be open to others; one made now is private
        }
        try (var channel = FileChannel.open(temporary, Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                attributes(privacy.newFileAttributes(temporary)))) {
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
        replace(temporary, target);
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
    public OutputStream append(Path file) throws IOException {
        var target = file.toAbsolutePath();
        var folder = target.getParent();
        createFolders(folder);
        return Channels.newOutputStream(FileChannel.open(target,
                Set.of(StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND),
                attributes(privacy.newFileAttributes(target))));
    }

    /**
     * Makes a folder and the folders above it that are missing, each private. Folders that exist are left as they
     * are.
     *
     * @param folder the folder that must exist
     * @throws IOException when a folder could not be made, or a file is in the way
     */
    public void createFolders(Path folder) throws IOException {
        if (!Files.isDirectory(folder)) {
            Files.createDirectories(folder, attributes(privacy.newFolderAttributes(folder)));
        }
    }

    /**
     * Makes an existing folder of the app private through the platform, for folders made before the app made them
     * private; a change is logged. A folder that is missing, or that the platform cannot change, is left as it is;
     * the latter is logged.
     *
     * @param folder one of the app's own folders, never one the owner named, such as with {@code --config=}
     */
    public void tighten(Path folder) {
        if (!Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try {
            if (privacy.tighten(folder)) {
                LOG.info("made " + folder + " readable only by you");
            }
        } catch (IOException e) {
            LOG.warn("could not make " + folder + " readable only by you: " + e.getMessage());
        }
    }

    /**
     * Makes an existing file private through the platform, for a file whose privacy matters enough to refuse it
     * otherwise, such as the API keys' file. Nothing is logged.
     *
     * @param file the file; when it is missing, the platform refuses it or reports that nothing changed (see
     *     {@link FilePrivacy#tighten})
     * @return true when it had to be changed; false when it was private already, or the platform has nothing to change
     * @throws IOException when the platform could not read or change it
     */
    public boolean tightenFile(Path file) throws IOException {
        return privacy.tighten(file);
    }

    /**
     * Removes a file the app no longer uses, such as one an older build left behind. A link is removed itself, not
     * what it points to.
     *
     * @param file the file to remove; it need not exist
     * @return true when it was there and is removed now; false when there was none
     * @throws IOException when it is there and could not be removed, such as a folder that is not empty
     */
    public boolean remove(Path file) throws IOException {
        return Files.deleteIfExists(file);
    }

    private static FileAttribute<?>[] attributes(List<FileAttribute<?>> attributes) {
        return attributes.toArray(new FileAttribute<?>[0]);
    }

    /**
     * In one step where the file system can, and by a plain move that replaces the file where it cannot. A move that
     * fails removes the temporary file, unless it holds the only copy of the text.
     */
    private void replace(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            if (movedPlainly.add(target.toAbsolutePath())) {
                LOG.warn("could not replace " + target + " in one step, so it is replaced by a plain move: "
                        + e.getMessage());
            }
            movePlainly(temporary, target);
        } catch (IOException e) {
            deleteQuietly(temporary);
            throw e;
        }
    }

    private static void movePlainly(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                deleteQuietly(temporary);
            }
            throw e;
        }
    }

    /** Where a folder can be opened, as on Linux; Windows refuses to, and keeps its own journal of the move. */
    private static void flushFolder(Path folder) {
        try (var channel = FileChannel.open(folder, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException ignored) {
            // the file itself is on the disk already; only the move could be lost in a power cut
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // a leftover temporary file is removed by the next write
        }
    }
}

package nl.enadeag.carchargeplanner.engine.store;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;

/**
 * One of the app's files, read through its {@link FileFormat} and kept in memory until it changes on disk, so the
 * planner, which asks every minute, does not read and parse it again each time. A change is noticed by the file's
 * last-modified time, size and identity; a write through this store updates the memory copy at once.
 *
 * <p>A file this version cannot use is never overwritten as it is: a write first renames it to
 * {@code <name>.damaged-<time>}, such as {@code engine.json.damaged-20261017-143000}, and logs that, so the owner can
 * still find what it held. Writes go through {@link PrivateFiles}. Safe to use from several threads.
 *
 * @param <T> the kind of value the file keeps
 */
public final class StoredFile<T> {

    private static final Logger LOG = LoggerFactory.getLogger(StoredFile.class);
    private static final DateTimeFormatter ASIDE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT).withZone(ZoneOffset.UTC);

    /** What tells one version of the file from the next, without reading it. */
    private record Stamp(FileTime modified, long size, Object identity) {
    }

    private final Path path;
    private final FileFormat<T> format;
    private final Clock clock;
    private Stamp seen;
    private Stored<T> remembered = new Stored.Absent<>();

    /**
     * Makes the store for one file; a file that cannot be used is renamed with the time on the system's clock.
     * Nothing is read yet.
     *
     * @param path where the file is, or will be made; its folder is made when needed
     * @param format how the file is read and written
     */
    public StoredFile(Path path, FileFormat<T> format) {
        this(path, format, Clock.systemUTC());
    }

    /**
     * Makes the store for one file; nothing is read yet.
     *
     * @param path where the file is, or will be made; its folder is made when needed
     * @param format how the file is read and written
     * @param clock the time a file that cannot be used is renamed with
     */
    public StoredFile(Path path, FileFormat<T> format, Clock clock) {
        this.path = path;
        this.format = format;
        this.clock = clock;
    }

    /**
     * Where the file is, to tell the owner.
     *
     * @return the path this store was made with
     */
    public Path path() {
        return path;
    }

    /**
     * What the file holds now: read again only when it changed on disk since the last read or write.
     *
     * @return nothing when there is no file, the value, or why this version cannot use the file; the latter is logged
     *     once per change of the file
     */
    public synchronized Stored<T> read() {
        Stamp now;
        try {
            now = stamp();
        } catch (NoSuchFileException ignored) {
            seen = null;
            remembered = new Stored.Absent<>();
            return remembered;
        } catch (IOException e) {
            return unreadable(e);
        }
        if (!now.equals(seen)) {
            remembered = parsed();
            seen = now;
            if (remembered instanceof Stored.Unusable<T> unusable) {
                LOG.warn(path + " cannot be used: " + unusable.detail());
            }
        }
        return remembered;
    }

    /**
     * Replaces the file by this value. A file this version cannot use is renamed first, and kept.
     *
     * @param value the value to keep
     * @throws IOException when the file could not be renamed or written; it is then left as it was
     */
    public synchronized void write(T value) throws IOException {
        moveAsideWhenUnusable();
        PrivateFiles.write(path, StoredJson.pretty(format.write(value)));
        remembered = new Stored.Found<>(value);
        try {
            seen = stamp();
        } catch (IOException ignored) {
            seen = null; // read again next time
        }
    }

    /**
     * Removes the file, for a value that is kept elsewhere now. A file this version cannot use is renamed instead, so
     * what it held is not lost.
     *
     * @throws IOException when the file could not be removed or renamed
     */
    public synchronized void delete() throws IOException {
        moveAsideWhenUnusable();
        Files.deleteIfExists(path);
        seen = null;
        remembered = new Stored.Absent<>();
    }

    private void moveAsideWhenUnusable() throws IOException {
        // something else in the file's place, such as a folder, is not ours to move: the write then fails
        if (!(read() instanceof Stored.Unusable<T> unusable) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        var name = path.getFileName() + ".damaged-" + ASIDE_TIME.format(clock.instant());
        var aside = path.resolveSibling(name);
        for (int count = 2; Files.exists(aside); count++) {
            aside = path.resolveSibling(name + "-" + count);
        }
        Files.move(path, aside);
        LOG.warn(path + " could not be used (" + unusable.detail() + "), so it is kept as " + aside
                + " before a new one is written");
        seen = null;
        remembered = new Stored.Absent<>();
    }

    private Stamp stamp() throws IOException {
        var attributes = Files.readAttributes(path, BasicFileAttributes.class);
        return new Stamp(attributes.lastModifiedTime(), attributes.size(), attributes.fileKey());
    }

    private Stored<T> parsed() {
        String text;
        try {
            text = Files.readString(path);
        } catch (IOException e) {
            return unreadable(e);
        }
        try {
            return new Stored.Found<>(format.read(StoredJson.parse(text)));
        } catch (JacksonException e) {
            var location = e.getLocation();
            var where = location == null ? "" : " (line " + location.getLineNr() + ", column "
                    + location.getColumnNr() + ")";
            // Jackson's message quotes the text it stumbled on, which may be a secret
            var what = format.holdsSecrets() ? "" : ": " + e.getOriginalMessage();
            return new Stored.Unusable<>(Stored.Reason.INVALID, "not valid JSON" + where + what);
        } catch (FormatProblem e) {
            return new Stored.Unusable<>(Stored.Reason.INVALID, e.getMessage());
        }
    }

    private Stored<T> unreadable(IOException e) {
        // an IOException names the file and the system's reason, never the content
        return new Stored.Unusable<>(Stored.Reason.UNREADABLE, e.getClass().getSimpleName() + ": " + e.getMessage());
    }
}

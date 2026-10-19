package nl.enadeag.carchargeplanner.logging;

import nl.enadeag.carchargeplanner.engine.store.PrivateFiles;
import nl.enadeag.carchargeplanner.start.AppName;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.ErrorManager;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.StreamHandler;

/**
 * The app's log file, which rotates: records go to {@code car-charge-planner.0.log}; once that file reaches its size
 * limit it becomes {@code .1.log}, the {@code .1.log} becomes {@code .2.log} and so on, the oldest is dropped, and a
 * new {@code .0.log} is started.
 *
 * <p>Every file is made through {@link PrivateFiles}, so only the owner can read it from the moment it exists; Java's
 * own {@code FileHandler} makes them with the system's default mode, which lets others read them. Only one copy of the
 * app runs at a time, so no lock file is needed either. Safe to use from several threads.
 */
final class LogFile extends StreamHandler {

    /** The start of each file's name. */
    static final String NAME = AppName.ID;

    private final Path folder;
    private final long limit;
    private final int count;
    private long written;

    /**
     * Opens the newest file, adding to what it holds.
     *
     * @param folder the folder the files are in; made, private, when it is missing
     * @param limit the size in bytes after which a file is rotated; a record is never split, so a file may grow a
     *     little past it; at least 1
     * @param count how many files are kept, the newest included; at least 1
     * @param formatter how each record is written
     * @throws IOException when the folder or the file could not be made or opened
     * @throws IllegalArgumentException when the limit or the count is less than 1
     */
    LogFile(Path folder, long limit, int count, Formatter formatter) throws IOException {
        if (limit < 1 || count < 1) {
            throw new IllegalArgumentException("a log file needs a size limit and a count of at least 1");
        }
        this.folder = folder;
        this.limit = limit;
        this.count = count;
        setFormatter(formatter);
        setEncoding(StandardCharsets.UTF_8.name());
        setLevel(Level.ALL);
        open();
    }

    /**
     * The file that records are written to now, to tell the owner where to look.
     *
     * @param folder the folder the files are in
     * @return the newest file; it need not exist yet
     */
    static Path newest(Path folder) {
        return generation(folder, 0);
    }

    /**
     * Writes the record at once, so it is in the file even when the app ends unexpectedly, and rotates the files when
     * the newest one has reached its limit.
     *
     * @param record the record; one below this handler's level is left out
     */
    @Override
    public synchronized void publish(LogRecord record) {
        super.publish(record);
        flush();
        if (written >= limit) {
            rotate();
        }
    }

    private static Path generation(Path folder, int number) {
        return folder.resolve(NAME + "." + number + ".log");
    }

    private void open() throws IOException {
        var file = newest(folder);
        written = Files.exists(file) ? Files.size(file) : 0;
        setOutputStream(new Counting(PrivateFiles.append(file)));
    }

    /** Like FileHandler: a file that cannot be rotated is reported to the error manager, and records may be lost. */
    private void rotate() {
        super.close(); // Windows cannot rename a file that is open
        try {
            Files.deleteIfExists(generation(folder, count - 1));
            for (int number = count - 2; number >= 0; number--) {
                var older = generation(folder, number);
                if (Files.exists(older)) {
                    Files.move(older, generation(folder, number + 1), StandardCopyOption.REPLACE_EXISTING);
                }
            }
            open();
        } catch (IOException e) {
            reportError("could not rotate the log files", e, ErrorManager.OPEN_FAILURE);
        }
    }

    /** Counts what is written to the newest file, so its size is known without asking the disk at every record. */
    private final class Counting extends FilterOutputStream {

        /**
         * Counts what goes to a stream.
         *
         * @param out the newest file's stream, closed when this is closed
         */
        Counting(OutputStream out) {
            super(out);
        }

        @Override
        public void write(int b) throws IOException {
            out.write(b);
            written++;
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            out.write(bytes, offset, length);
            written += length;
        }
    }
}

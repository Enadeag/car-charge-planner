package nl.enadeag.carchargeplanner.engine.store;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.ProviderMismatchException;
import java.nio.file.StandardCopyOption;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.StreamSupport;

/**
 * The system's own file system, except that it cannot move a file in one step: a move with
 * {@link StandardCopyOption#ATOMIC_MOVE} throws {@link AtomicMoveNotSupportedException}, as some file systems do. The
 * JDK's zip file system cannot stand in for one: it accepts that option. Everything else is passed to the system's
 * file system, so files are real files in a test's own folder. Its variants fail in other ways a move can.
 */
final class NoAtomicMoveFileSystem extends FileSystem {

    private static final FileSystem SYSTEM = FileSystems.getDefault();

    private final Provider provider = new Provider();
    private final IOException refusal;
    private final boolean plainMoveLosesTheFile;

    /** Refuses to move in one step and moves plainly as the system does. */
    NoAtomicMoveFileSystem() {
        this(null, false);
    }

    private NoAtomicMoveFileSystem(IOException refusal, boolean plainMoveLosesTheFile) {
        this.refusal = refusal;
        this.plainMoveLosesTheFile = plainMoveLosesTheFile;
    }

    /**
     * A file system whose plain move removes the file it replaces and then fails, as one that stops halfway does.
     *
     * @return the file system
     */
    static NoAtomicMoveFileSystem whosePlainMoveLosesTheFile() {
        return new NoAtomicMoveFileSystem(null, true);
    }

    /**
     * A file system whose move in one step fails for another reason than that it cannot.
     *
     * @param refusal what the move in one step throws; never null
     * @return the file system
     */
    static NoAtomicMoveFileSystem refusingToMove(IOException refusal) {
        return new NoAtomicMoveFileSystem(refusal, false);
    }

    /**
     * A path of this file system for a path of the system's.
     *
     * @param path a path on the system's own file system
     * @return the same path, on this file system
     */
    Path wrap(Path path) {
        return new Wrapped(this, path);
    }

    @Override
    public FileSystemProvider provider() {
        return provider;
    }

    @Override
    public void close() {
        // the system's file system stays open
    }

    @Override
    public boolean isOpen() {
        return true;
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public String getSeparator() {
        return SYSTEM.getSeparator();
    }

    @Override
    public Iterable<Path> getRootDirectories() {
        return StreamSupport.stream(SYSTEM.getRootDirectories().spliterator(), false).map(this::wrap).toList();
    }

    @Override
    public Iterable<FileStore> getFileStores() {
        return SYSTEM.getFileStores();
    }

    @Override
    public Set<String> supportedFileAttributeViews() {
        return SYSTEM.supportedFileAttributeViews();
    }

    @Override
    public Path getPath(String first, String... more) {
        return wrap(SYSTEM.getPath(first, more));
    }

    @Override
    public PathMatcher getPathMatcher(String syntaxAndPattern) {
        var matcher = SYSTEM.getPathMatcher(syntaxAndPattern);
        return path -> matcher.matches(unwrap(path));
    }

    @Override
    public UserPrincipalLookupService getUserPrincipalLookupService() {
        return SYSTEM.getUserPrincipalLookupService();
    }

    @Override
    public WatchService newWatchService() {
        throw new UnsupportedOperationException("not needed by the tests");
    }

    private static Path unwrap(Path path) {
        if (path instanceof Wrapped wrapped) {
            return wrapped.inner;
        }
        throw new ProviderMismatchException();
    }

    /** A path of the system's file system, seen through this one. */
    private record Wrapped(NoAtomicMoveFileSystem fileSystem, Path inner) implements Path {

        private Path wrapped(Path path) {
            return path == null ? null : new Wrapped(fileSystem, path);
        }

        @Override
        public FileSystem getFileSystem() {
            return fileSystem;
        }

        @Override
        public boolean isAbsolute() {
            return inner.isAbsolute();
        }

        @Override
        public Path getRoot() {
            return wrapped(inner.getRoot());
        }

        @Override
        public Path getFileName() {
            return wrapped(inner.getFileName());
        }

        @Override
        public Path getParent() {
            return wrapped(inner.getParent());
        }

        @Override
        public int getNameCount() {
            return inner.getNameCount();
        }

        @Override
        public Path getName(int index) {
            return wrapped(inner.getName(index));
        }

        @Override
        public Path subpath(int beginIndex, int endIndex) {
            return wrapped(inner.subpath(beginIndex, endIndex));
        }

        @Override
        public boolean startsWith(Path other) {
            return inner.startsWith(unwrap(other));
        }

        @Override
        public boolean endsWith(Path other) {
            return inner.endsWith(unwrap(other));
        }

        @Override
        public Path normalize() {
            return wrapped(inner.normalize());
        }

        @Override
        public Path resolve(Path other) {
            return wrapped(inner.resolve(unwrap(other)));
        }

        @Override
        public Path relativize(Path other) {
            return wrapped(inner.relativize(unwrap(other)));
        }

        @Override
        public URI toUri() {
            return inner.toUri();
        }

        @Override
        public Path toAbsolutePath() {
            return wrapped(inner.toAbsolutePath());
        }

        @Override
        public Path toRealPath(LinkOption... options) throws IOException {
            return wrapped(inner.toRealPath(options));
        }

        @Override
        public WatchKey register(WatchService watcher, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers) {
            throw new UnsupportedOperationException("not needed by the tests");
        }

        @Override
        public int compareTo(Path other) {
            return inner.compareTo(unwrap(other));
        }

        @Override
        public String toString() {
            return inner.toString();
        }
    }

    /** Passes everything to the system's provider, but refuses to move in one step, and fails as its variant does. */
    private final class Provider extends FileSystemProvider {

        private final FileSystemProvider system = SYSTEM.provider();

        @Override
        public String getScheme() {
            return "no-atomic-move";
        }

        @Override
        public FileSystem newFileSystem(URI uri, Map<String, ?> env) {
            throw new UnsupportedOperationException("made by the tests only");
        }

        @Override
        public FileSystem getFileSystem(URI uri) {
            return NoAtomicMoveFileSystem.this;
        }

        @Override
        public Path getPath(URI uri) {
            return wrap(system.getPath(uri));
        }

        @Override
        public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options,
                FileAttribute<?>... attrs) throws IOException {
            return system.newByteChannel(unwrap(path), options, attrs);
        }

        @Override
        public FileChannel newFileChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs)
                throws IOException {
            return system.newFileChannel(unwrap(path), options, attrs);
        }

        @Override
        public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter)
                throws IOException {
            throw new UnsupportedOperationException("not needed by the tests");
        }

        @Override
        public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
            system.createDirectory(unwrap(dir), attrs);
        }

        @Override
        public void delete(Path path) throws IOException {
            system.delete(unwrap(path));
        }

        @Override
        public void copy(Path source, Path target, CopyOption... options) throws IOException {
            system.copy(unwrap(source), unwrap(target), options);
        }

        @Override
        public void move(Path source, Path target, CopyOption... options) throws IOException {
            if (Arrays.asList(options).contains(StandardCopyOption.ATOMIC_MOVE)) {
                if (refusal != null) {
                    throw refusal;
                }
                throw new AtomicMoveNotSupportedException(source.toString(), target.toString(),
                        "this file system cannot move in one step");
            }
            if (plainMoveLosesTheFile) {
                system.deleteIfExists(unwrap(target));
                throw new IOException("the move stopped after " + target + " was removed");
            }
            system.move(unwrap(source), unwrap(target), options);
        }

        @Override
        public boolean isSameFile(Path path, Path path2) throws IOException {
            return system.isSameFile(unwrap(path), unwrap(path2));
        }

        @Override
        public boolean isHidden(Path path) throws IOException {
            return system.isHidden(unwrap(path));
        }

        @Override
        public FileStore getFileStore(Path path) throws IOException {
            return system.getFileStore(unwrap(path));
        }

        @Override
        public void checkAccess(Path path, AccessMode... modes) throws IOException {
            system.checkAccess(unwrap(path), modes);
        }

        @Override
        public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type,
                LinkOption... options) {
            return system.getFileAttributeView(unwrap(path), type, options);
        }

        @Override
        public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options)
                throws IOException {
            return system.readAttributes(unwrap(path), type, options);
        }

        @Override
        public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options)
                throws IOException {
            return system.readAttributes(unwrap(path), attributes, options);
        }

        @Override
        public void setAttribute(Path path, String attribute, Object value, LinkOption... options)
                throws IOException {
            system.setAttribute(unwrap(path), attribute, value, options);
        }
    }
}

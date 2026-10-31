package nl.enadeag.carchargeplanner.engine.store;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.List;

/**
 * How the platform keeps the app's files from other users: {@link PrivateFiles} makes every file and folder with the
 * attributes this gives, so there is no moment when others could read one, and asks it to tighten a file or folder
 * that was made before. The engine does not set file modes itself, as each platform protects files its own way: the
 * desktop with POSIX modes where the file system has them, Android not at all, as its app storage is private already
 * (ADR 0010).
 *
 * <p>What every implementation must do, which {@code FilePrivacyContractTest} among the engine's tests checks:
 * <ul>
 *   <li>give attributes that the path's own file system accepts when it makes a file or folder there, and that leave
 *       the owner able to read and write the file, and to list, enter and add to the folder;</li>
 *   <li>make a file or folder made with them private already, so tightening it changes nothing;</li>
 *   <li>tighten a file or folder so that it stays the owner's to read and write, and report a change only when it
 *       made one;</li>
 *   <li>never make a path that is missing when asked to tighten it;</li>
 *   <li>be safe to use from several threads at once.</li>
 * </ul>
 */
public interface FilePrivacy {

    /** Adds nothing and changes nothing, for a platform whose app storage is private already, and for tests. */
    FilePrivacy NONE = new NoFilePrivacy();

    /**
     * The attributes to make a new file with, so it is private from the moment it exists.
     *
     * @param file the file about to be made; never null; its file system decides what it accepts
     * @return the attributes, none when the platform adds none; never null
     */
    List<FileAttribute<?>> newFileAttributes(Path file);

    /**
     * The attributes to make a new folder with, so it is private from the moment it exists.
     *
     * @param folder the folder about to be made; never null; its file system decides what it accepts
     * @return the attributes, none when the platform adds none; never null
     */
    List<FileAttribute<?>> newFolderAttributes(Path folder);

    /**
     * Makes an existing file or folder private, for one made before the app made its files private, or by hand.
     *
     * @param path the file or folder; never null; a missing one is never made, and may be refused with an
     *     {@link IOException}
     * @return true when it had to be changed; false when it was private already, or the platform has nothing to change
     * @throws IOException when it could not be read or changed, such as when it is missing
     */
    boolean tighten(Path path) throws IOException;
}

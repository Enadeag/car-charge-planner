package nl.enadeag.carchargeplanner.host;

import nl.enadeag.carchargeplanner.instance.PrivateFolder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.Map;
import java.util.Optional;

/**
 * A private folder for files this process hands to other programs, such as the icons for the panel and the
 * notification server: only the user may use it, so no other user can plant a link in it or swap its files.
 */
public final class RuntimeFolder {

    private RuntimeFolder() {
    }

    /**
     * Makes the folder and checks it is private.
     *
     * @param purpose what the folder is for, part of its name, such as "tray"
     * @param environment the environment variables; {@code XDG_RUNTIME_DIR} is used when it is an absolute path
     * @param temporary the system's folder for temporary files, for when there is no usable {@code XDG_RUNTIME_DIR}
     * @param user the user this app runs as, who must own the folder; empty when that could not be told, and then the
     *     owner is not checked
     * @return in the user's runtime folder, which the session empties at logout, a folder named after the purpose
     *     and this process; without one, a new folder with a random name in the temporary folder. Either has mode
     *     0700.
     * @throws IOException when the folder cannot be made, or it is a link, another user's, or open to others
     */
    public static Path make(String purpose, Map<String, String> environment, Path temporary,
            Optional<UserPrincipal> user) throws IOException {
        var prefix = ConfigLocation.APP_FOLDER + "-" + purpose + "-";
        var runtime = environment.getOrDefault("XDG_RUNTIME_DIR", "");
        if (runtime.isEmpty() || !Path.of(runtime).isAbsolute()) {
            // a guessable name in a shared folder could be made by another user first
            var made = Files.createTempDirectory(temporary, prefix,
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
            return new PrivateFolder(made, user).make();
        }
        var folder = Path.of(runtime).resolve(prefix + ProcessHandle.current().pid());
        return new PrivateFolder(folder, user).make();
    }
}

package nl.enadeag.carchargeplanner.instance;

import java.nio.file.attribute.UserPrincipal;

/** Who must own the private folders and the instance socket, and so the only user whose connections are taken. */
public sealed interface Owner {

    /**
     * The user this app runs as, where files have an owner and a mode (Linux).
     *
     * @param user the user, as the file system names them
     */
    record User(UserPrincipal user) implements Owner {

        /**
         * Checks the user is given.
         *
         * @throws IllegalArgumentException when the user is null
         */
        public User {
            if (user == null) {
                throw new IllegalArgumentException("the user must be given");
            }
        }
    }

    /**
     * Nobody is checked: on Windows the folder's place keeps others out, as a folder made by an administrator's
     * process is owned by the administrators; elsewhere, when the user could not be told.
     */
    record Unchecked() implements Owner {
    }
}

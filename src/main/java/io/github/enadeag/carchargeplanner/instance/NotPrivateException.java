package io.github.enadeag.carchargeplanner.instance;

import java.io.IOException;

/**
 * A folder that must be the user's only is not, and cannot safely be made so: another user's, one others may write
 * in, or a link or another kind of file in its place. Unlike a folder that merely cannot be made, it may be
 * someone's attempt to get in the way, so it is not used.
 */
public final class NotPrivateException extends IOException {

    private static final long serialVersionUID = 1L;

    /**
     * Makes the exception.
     *
     * @param message what is wrong with which folder, such as "/run/user/1000/x belongs to another user"
     */
    public NotPrivateException(String message) {
        super(message);
    }
}

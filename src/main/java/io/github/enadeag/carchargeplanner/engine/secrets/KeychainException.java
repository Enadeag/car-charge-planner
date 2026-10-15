package io.github.enadeag.carchargeplanner.engine.secrets;

/** The keychain is missing, locked or failed. The message never holds a value. */
public class KeychainException extends Exception {

    /**
     * Makes the exception.
     *
     * @param message what went wrong, without any value
     */
    public KeychainException(String message) {
        super(message);
    }

    /**
     * Makes the exception, keeping the failure underneath.
     *
     * @param message what went wrong, without any value
     * @param cause the library's or the system's own failure
     */
    public KeychainException(String message, Throwable cause) {
        super(message, cause);
    }
}

package io.github.enadeag.carchargeplanner.engine.secrets;

/** The keychain is missing, locked or failed. The message never holds a value. */
public class KeychainException extends Exception {

    public KeychainException(String message) {
        super(message);
    }

    public KeychainException(String message, Throwable cause) {
        super(message, cause);
    }
}

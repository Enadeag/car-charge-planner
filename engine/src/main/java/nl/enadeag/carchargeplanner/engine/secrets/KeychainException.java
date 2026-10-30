package nl.enadeag.carchargeplanner.engine.secrets;

/** The keychain is missing, locked or failed; {@link #kind()} says which. The message never holds a value. */
public class KeychainException extends Exception {

    /** What kind of failure it is, which decides what the app does next. */
    public enum Kind {
        /** There is no keychain the app can use, or it does not answer: the keys go to the private file. */
        UNAVAILABLE,
        /** The keychain is there but locked: the keys are not available yet, and the owner can unlock it. */
        LOCKED,
        /** The keychain answered and refused, or did not keep what was written to it. */
        REFUSED
    }

    private final Kind kind;

    /**
     * Makes the exception.
     *
     * @param kind what kind of failure it is; never null
     * @param message what went wrong, without any value
     * @throws IllegalArgumentException when the kind is null
     */
    public KeychainException(Kind kind, String message) {
        super(message);
        if (kind == null) {
            throw new IllegalArgumentException("the kind must not be null");
        }
        this.kind = kind;
    }

    /**
     * Makes the exception, keeping the failure underneath.
     *
     * @param kind what kind of failure it is; never null
     * @param message what went wrong, without any value
     * @param cause the library's or the system's own failure
     * @throws IllegalArgumentException when the kind is null
     */
    public KeychainException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        if (kind == null) {
            throw new IllegalArgumentException("the kind must not be null");
        }
        this.kind = kind;
    }

    /**
     * What kind of failure it is.
     *
     * @return unavailable, locked or refused
     */
    public Kind kind() {
        return kind;
    }
}

package io.github.enadeag.carchargeplanner.engine.secrets;

/**
 * The keys cannot be read or stored safely; the message says why in words for the owner and never holds a key, and
 * {@link #kind()} lets the app say it in the owner's language where that matters.
 */
public class SecretsProblem extends Exception {

    /** What kind of problem it is. */
    public enum Kind {
        /** The keychain or the file failed, or holds something that cannot be used. */
        FAILED,
        /** The system keychain is locked: the keys are not available until the owner unlocks it. */
        LOCKED
    }

    private final Kind kind;

    /**
     * Makes a problem of the kind {@link Kind#FAILED}.
     *
     * @param message what went wrong, in words for the owner, without any key
     */
    public SecretsProblem(String message) {
        this(Kind.FAILED, message);
    }

    /**
     * Makes the exception.
     *
     * @param kind what kind of problem it is; never null
     * @param message what went wrong, in words for the owner, without any key
     * @throws IllegalArgumentException when the kind is null
     */
    public SecretsProblem(Kind kind, String message) {
        super(message);
        if (kind == null) {
            throw new IllegalArgumentException("the kind must not be null");
        }
        this.kind = kind;
    }

    /**
     * What kind of problem it is.
     *
     * @return failed, or locked
     */
    public Kind kind() {
        return kind;
    }
}

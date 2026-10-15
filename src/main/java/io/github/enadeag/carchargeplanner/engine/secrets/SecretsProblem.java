package io.github.enadeag.carchargeplanner.engine.secrets;

/** The keys cannot be read or stored safely; the message says why in words for the owner and never holds a key. */
public class SecretsProblem extends Exception {

    /**
     * Makes the exception.
     *
     * @param message what went wrong, in words for the owner, without any key
     */
    public SecretsProblem(String message) {
        super(message);
    }
}

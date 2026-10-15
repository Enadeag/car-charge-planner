package io.github.enadeag.carchargeplanner.engine.secrets;

/** The keys cannot be read or stored safely; the message says why in words for the owner and never holds a key. */
public class SecretsProblem extends Exception {

    public SecretsProblem(String message) {
        super(message);
    }
}

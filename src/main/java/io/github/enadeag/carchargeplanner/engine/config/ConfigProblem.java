package io.github.enadeag.carchargeplanner.engine.config;

/** The config file is missing or does not describe a valid system; the message says what to fix. */
public class ConfigProblem extends Exception {

    /**
     * Makes the problem.
     *
     * @param message what is wrong and where, in words for the owner; not null
     */
    public ConfigProblem(String message) {
        super(message);
    }
}

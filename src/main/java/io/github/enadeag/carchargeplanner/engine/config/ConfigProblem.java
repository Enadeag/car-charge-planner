package io.github.enadeag.carchargeplanner.engine.config;

/** The config file is missing or does not describe a valid system; the message says what to fix. */
public class ConfigProblem extends Exception {

    public ConfigProblem(String message) {
        super(message);
    }
}

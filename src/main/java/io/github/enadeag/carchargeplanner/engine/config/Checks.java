package io.github.enadeag.carchargeplanner.engine.config;

/** The checks the config records make on their values. */
final class Checks {

    private Checks() {
    }

    /**
     * Refuses a value that is not allowed.
     *
     * @param condition whether the value is allowed
     * @param problem the message for the owner, naming the setting and what is allowed
     * @throws IllegalArgumentException with the problem as its message when the condition is false
     */
    static void require(boolean condition, String problem) {
        if (!condition) {
            throw new IllegalArgumentException(problem);
        }
    }
}

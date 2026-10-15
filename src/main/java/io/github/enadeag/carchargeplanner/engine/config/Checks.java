package io.github.enadeag.carchargeplanner.engine.config;

final class Checks {

    private Checks() {
    }

    static void require(boolean condition, String problem) {
        if (!condition) {
            throw new IllegalArgumentException(problem);
        }
    }
}

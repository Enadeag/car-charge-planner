package io.github.enadeag.carchargeplanner;

/** How the app's process ends, as the shell sees it. */
enum ExitCode {
    /** It did what was asked, or there was nothing to do. */
    SUCCESS(0),
    /** It could not do what was asked; the message on standard error says why. */
    FAILURE(1),
    /** The command line names no command the app knows. */
    USAGE(2);

    private final int code;

    ExitCode(int code) {
        this.code = code;
    }

    /**
     * The number the process ends with.
     *
     * @return 0 for success, more for a failure
     */
    int code() {
        return code;
    }
}

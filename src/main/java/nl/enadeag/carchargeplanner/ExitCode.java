package nl.enadeag.carchargeplanner;

/** How the app's process ends, as the shell sees it. */
enum ExitCode {
    /** It did what was asked, or there was nothing to do. */
    SUCCESS(0),
    /** It could not do what was asked; the message on standard error says why. */
    FAILURE(1),
    /** The command line names no command the app knows. */
    USAGE(2),
    /**
     * The app runs already, but this start could not reach it: neither to show its dashboard nor to quit it. Also when
     * a command that only the running app can carry out, such as showing a test notification, finds no app running.
     */
    UNREACHABLE(3);

    private final int code;

    ExitCode(int code) {
        this.code = code;
    }

    /**
     * The number the process ends with.
     *
     * @return 0 for success, more for a failure, each its own number
     */
    int code() {
        return code;
    }
}

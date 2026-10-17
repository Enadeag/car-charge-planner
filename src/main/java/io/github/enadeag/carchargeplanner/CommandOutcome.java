package io.github.enadeag.carchargeplanner;

import java.io.PrintStream;

/**
 * What came of a command of the command line, for the owner who typed it.
 *
 * @param exit how the process ends
 * @param text what the owner is told, in their language; may hold several lines
 */
record CommandOutcome(ExitCode exit, String text) {

    /**
     * Prints the text: to standard output when the command succeeded, else to standard error.
     *
     * @param out standard output
     * @param err standard error
     */
    void print(PrintStream out, PrintStream err) {
        (exit == ExitCode.SUCCESS ? out : err).println(text);
    }
}

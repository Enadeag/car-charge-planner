package nl.enadeag.carchargeplanner.cli;

import java.io.PrintStream;

/**
 * What came of a command of the command line, for the owner who typed it.
 *
 * @param exit how the process ends
 * @param text what the owner is told, in their language; may hold several lines
 */
public record CommandOutcome(ExitCode exit, String text) {

    /**
     * Prints the text: to standard output when the command succeeded, else to standard error.
     *
     * @param out where the text of a success goes, normally standard output
     * @param err where the text of a failure goes, normally standard error
     */
    public void print(PrintStream out, PrintStream err) {
        (exit == ExitCode.SUCCESS ? out : err).println(text);
    }
}

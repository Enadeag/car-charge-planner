package nl.enadeag.carchargeplanner.engine.store;

/**
 * A stored file was written by a newer version of the app, so this version cannot know what it holds. Its own kind of
 * {@link FormatProblem}, so a client can tell the owner to update the app rather than to fix or replace the file.
 */
public class NewerFileVersion extends FormatProblem {

    /**
     * Makes the problem.
     *
     * @param message what is wrong, in English, such as the file's version and the newest this version reads
     */
    public NewerFileVersion(String message) {
        super(message);
    }
}

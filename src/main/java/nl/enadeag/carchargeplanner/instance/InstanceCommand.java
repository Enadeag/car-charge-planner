package nl.enadeag.carchargeplanner.instance;

import java.util.Arrays;
import java.util.Optional;

/** What another start of the app may ask the running one, each sent as one line of text. */
public enum InstanceCommand {
    /** Show the dashboard. */
    SHOW("show"),
    /** Quit the app. */
    QUIT("quit"),
    /** Show a test notification, which looks like an announcement but is not one: nothing is recorded or planned. */
    TEST_NOTIFICATION("test-notification");

    private final String word;

    InstanceCommand(String word) {
        this.word = word;
    }

    /**
     * The word that is sent for this command.
     *
     * @return the lower-case word, without the line's end
     */
    public String word() {
        return word;
    }

    /**
     * The command a line holds.
     *
     * @param line the line as received, without its end; may be anything another program sent
     * @return the command, or empty when the line is not exactly one of the commands' words
     */
    static Optional<InstanceCommand> of(String line) {
        return Arrays.stream(values()).filter(command -> command.word.equals(line)).findFirst();
    }
}

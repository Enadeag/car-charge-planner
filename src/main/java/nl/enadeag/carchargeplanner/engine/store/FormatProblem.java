package nl.enadeag.carchargeplanner.engine.store;

/**
 * A stored file holds something this version cannot use: not the JSON it expects, or a value it refuses. The message
 * is in English and names the place in the file, such as {@code system.car: usableBatteryKwh must be more than 0}.
 */
public class FormatProblem extends Exception {

    /**
     * Makes the problem.
     *
     * @param message what is wrong and where, in English; never a secret from the file
     */
    public FormatProblem(String message) {
        super(message);
    }
}

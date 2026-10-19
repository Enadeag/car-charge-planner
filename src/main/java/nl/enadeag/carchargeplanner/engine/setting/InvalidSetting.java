package nl.enadeag.carchargeplanner.engine.setting;

/**
 * A value the config refuses, with the setting it belongs to and the rule it breaks, so that a client can say what is
 * wrong next to the field in the owner's language. The message is the engine's own, in English, as in the config
 * file's problems.
 */
public class InvalidSetting extends IllegalArgumentException {

    /** The rule a refused value breaks. */
    public enum Rule {
        /** The value lies outside the range the setting allows. */
        OUT_OF_RANGE,
        /** The minimum charge level is not below the target charge level. */
        NOT_BELOW_TARGET,
        /** The highest current is below the lowest current. */
        BELOW_MINIMUM_CURRENT,
        /** The fallback provider is the primary provider. */
        SAME_AS_PRIMARY,
        /** An away period does not end after it starts on the same day. */
        NOT_AFTER_START
    }

    private final ConfigSetting setting;
    private final Rule rule;

    /**
     * Makes the exception.
     *
     * @param setting the setting whose value is refused; for a rule between two settings, the one whose field shows
     *     the problem
     * @param rule the rule the value breaks
     * @param message in English, naming the value as the config file does, such as "margin must be more than 0 and at
     *     most 1"
     */
    public InvalidSetting(ConfigSetting setting, Rule rule, String message) {
        super(message);
        this.setting = setting;
        this.rule = rule;
    }

    /**
     * The setting whose value is refused.
     *
     * @return the setting this exception was made with
     */
    public ConfigSetting setting() {
        return setting;
    }

    /**
     * The rule the value breaks.
     *
     * @return the rule this exception was made with
     */
    public Rule rule() {
        return rule;
    }
}

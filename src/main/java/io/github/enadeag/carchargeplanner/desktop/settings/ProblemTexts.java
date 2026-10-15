package io.github.enadeag.carchargeplanner.desktop.settings;

import io.github.enadeag.carchargeplanner.desktop.Messages;
import io.github.enadeag.carchargeplanner.desktop.TextKey;
import io.github.enadeag.carchargeplanner.engine.setting.InvalidSetting;

/** What a field says about a value the engine refused, in the owner's language. */
final class ProblemTexts {

    private ProblemTexts() {
    }

    /**
     * The sentence for a broken rule.
     *
     * @param rule the rule the engine says the value breaks
     * @param messages the texts, in the owner's language
     * @return such as "Must be below the target charge level."
     */
    static String of(InvalidSetting.Rule rule, Messages messages) {
        return messages.text(switch (rule) {
            case OUT_OF_RANGE -> TextKey.PROBLEM_OUT_OF_RANGE;
            case NOT_BELOW_TARGET -> TextKey.PROBLEM_NOT_BELOW_TARGET;
            case BELOW_MINIMUM_CURRENT -> TextKey.PROBLEM_BELOW_MINIMUM_CURRENT;
            case SAME_AS_PRIMARY -> TextKey.PROBLEM_SAME_AS_PRIMARY;
            case NOT_AFTER_START -> TextKey.PROBLEM_NOT_AFTER_START;
        });
    }
}

package nl.enadeag.carchargeplanner.desktop.settings.field;

import java.util.Optional;

/**
 * A control the owner types a number in, which says itself what is wrong with the text, and what the engine allows
 * for its setting, as Save and Next check it.
 */
public interface NumberField extends Input {

    /**
     * What is wrong with the text itself, before the engine's records see the number.
     *
     * @return such as "Type a number." in the owner's language; empty when the text is a number the field takes
     */
    Optional<String> problem();

    /**
     * What the field says when the engine refuses its value as outside the setting's limit.
     *
     * @return such as "Type a number from 1 to 1000." in the owner's language and number format, in the field's unit
     */
    String outOfRange();
}

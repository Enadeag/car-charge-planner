package nl.enadeag.carchargeplanner.desktop.settings.form;

import nl.enadeag.carchargeplanner.desktop.text.Messages;

/**
 * How a number field writes its value and reads what the owner typed, the way the owner's language writes numbers. A
 * text that is not a number keeps the value held before; a text the owner did not edit gives that value back exactly,
 * not as rounded for showing.
 */
public final class NumberText {

    private final Messages messages;

    /**
     * Makes the texts.
     *
     * @param messages writes and reads the numbers in the owner's language
     */
    public NumberText(Messages messages) {
        this.messages = messages;
    }

    /**
     * A value as the field shows it.
     *
     * @param value the value, in the field's unit
     * @param decimals the decimals the field shows, 0 or more
     * @return such as "0.25" in English and "0,25" in Dutch for two decimals
     */
    public String write(double value, int decimals) {
        return messages.number(value, decimals);
    }

    /**
     * The value a text in the field stands for.
     *
     * @param text what the field shows now, typed by the owner or not
     * @param held the value the field held before, in the field's unit
     * @param decimals the decimals the field shows, 0 or more
     * @return {@code held} exactly when the text is how it is written, the typed number when the text is one, and
     *     {@code held} otherwise; not brought into the field's range
     */
    public double read(String text, double held, int decimals) {
        if (text.equals(write(held, decimals))) {
            return held; // untouched: keep the exact value, not the rounded one shown
        }
        return messages.parseNumber(text).orElse(held);
    }
}

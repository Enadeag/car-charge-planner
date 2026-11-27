package nl.enadeag.carchargeplanner.desktop.settings.form;

import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import java.util.regex.Pattern;

/**
 * How a number field writes its value and reads what the owner typed. It writes the language's decimal mark, a comma
 * or a point, without a thousands separator; it reads either mark, a leading minus and the digits 0 to 9, and nothing
 * else: no thousands separator, exponent or plus sign, and an empty field is no number. Spaces around the text are
 * left out. A text the owner did not edit gives the value held back exactly, not as rounded for showing. Whether the
 * number fits is left to the engine's rules.
 */
public final class NumberText {

    private static final Pattern NUMBER = Pattern.compile("-?[0-9]+([.,][0-9]+)?");
    private static final Pattern WHOLE = Pattern.compile("-?[0-9]+");

    private final Messages messages;

    /** A number read from a field, or what is wrong with the text. */
    public sealed interface Read {

        /**
         * The text is a number the field takes.
         *
         * @param value the number, finite, not brought into any range
         */
        record Number(double value) implements Read {
        }

        /**
         * The text is no number the field takes.
         *
         * @param problem what the field says
         */
        record Wrong(TextKey problem) implements Read {
        }
    }

    /**
     * Makes the texts.
     *
     * @param messages writes the numbers in the owner's language
     */
    public NumberText(Messages messages) {
        this.messages = messages;
    }

    /**
     * A value as the field shows it.
     *
     * @param value the value, in the field's unit
     * @param decimals the decimals the field shows, 0 or more
     * @return such as "0.25" in English and "0,25" in Dutch for two decimals, and "1000" without a separator
     */
    public String write(double value, int decimals) {
        return messages.plainNumber(value, decimals);
    }

    /**
     * The value a text in the field stands for.
     *
     * @param text what the field shows now, typed by the owner or not
     * @param held the value the field held before, in the field's unit
     * @param decimals the decimals the field shows, 0 or more
     * @param whole whether the field takes only whole numbers
     * @return {@code held} exactly when the text is how it is written; else the typed number, or the problem: "Type a
     *     number." for no number, "Type a whole number" for decimals in a whole-number field, and the general text
     *     for a whole number too long for one
     */
    public Read read(String text, double held, int decimals, boolean whole) {
        if (text.equals(write(held, decimals))) {
            return new Read.Number(held); // untouched: keep the exact value, not the rounded one shown
        }
        var typed = text.strip();
        if (whole && WHOLE.matcher(typed).matches()) {
            try {
                return new Read.Number(Integer.parseInt(typed));
            } catch (NumberFormatException tooLong) {
                return new Read.Wrong(TextKey.PROBLEM_OUT_OF_RANGE);
            }
        }
        if (!NUMBER.matcher(typed).matches()) {
            return new Read.Wrong(TextKey.PROBLEM_NOT_A_NUMBER);
        }
        if (whole) {
            return new Read.Wrong(TextKey.PROBLEM_NOT_A_WHOLE_NUMBER);
        }
        var number = Double.parseDouble(typed.replace(',', '.'));
        return Double.isFinite(number) ? new Read.Number(number) : new Read.Wrong(TextKey.PROBLEM_NOT_A_NUMBER);
    }
}

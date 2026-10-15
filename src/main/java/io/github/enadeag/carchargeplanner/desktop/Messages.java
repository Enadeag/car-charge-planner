package io.github.enadeag.carchargeplanner.desktop;

import java.math.RoundingMode;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * The app's texts in one language, from the resource bundle {@code messages_<code>.properties} beside this class, with
 * the numbers, times and day names in them written the way that language writes them. Nothing here follows the JVM's
 * default locale: the language is the one given.
 */
public final class Messages {

    private static final String BUNDLE = Messages.class.getPackageName() + ".messages";

    private final Language language;
    private final ResourceBundle bundle;
    private final DateTimeFormatter time;

    /**
     * Loads the language's texts.
     *
     * @param language the language to show the texts in; never null
     */
    public Messages(Language language) {
        this.language = language;
        // without this control, a missing bundle would quietly fall back to the JVM's default locale
        this.bundle = ResourceBundle.getBundle(BUNDLE, language.locale(),
                ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
        this.time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(language.locale());
    }

    /**
     * The language the texts are in.
     *
     * @return the language these messages were made with
     */
    public Language language() {
        return language;
    }

    /**
     * A text with its values filled in, by {@link MessageFormat}.
     *
     * @param key which text
     * @param values the values for the text's places {@code {0}}, {@code {1}} and on, in that order: texts already
     *     formatted (see {@link #decimal}, {@link #whole}, {@link #time}), or whole numbers such as a current in A,
     *     which a {@code choice} place can pick a plural by; none for a text without places
     * @return the text in this language
     */
    public String text(TextKey key, Object... values) {
        return new MessageFormat(bundle.getString(key.name()), language.locale()).format(values);
    }

    /**
     * A number with one decimal, such as an energy or a power.
     *
     * @param value the number, in any unit
     * @return such as "2.3" in English and "2,3" in Dutch; a half is rounded away from zero
     */
    public String decimal(double value) {
        return number(value, 1);
    }

    /**
     * A number rounded to a whole one, such as a percentage.
     *
     * @param value the number, in any unit
     * @return such as "92"; a half is rounded away from zero
     */
    public String whole(double value) {
        return number(value, 0);
    }

    /**
     * A moment as a clock time.
     *
     * @param moment the moment
     * @param zone the time zone of the owner's home, in which the time is told
     * @return hours and minutes the way the language writes them, such as "09:05" on a 24-hour clock
     */
    public String time(Instant moment, ZoneId zone) {
        return time.format(moment.atZone(zone));
    }

    /**
     * The times of a span, such as a charge window.
     *
     * @param start the span's start
     * @param end the span's end, after its start
     * @param zone the time zone of the owner's home, in which the times are told
     * @return such as "11:15 – 14:30"
     */
    public String span(Instant start, Instant end, ZoneId zone) {
        return time(start, zone) + " – " + time(end, zone);
    }

    /**
     * A day's short name, for its tab.
     *
     * @param day the day to name
     * @param today the day the plan was made, in the owner's home
     * @return "Today", or the weekday's short name such as "Fri" in English and "Vr" in Dutch
     */
    String tab(LocalDate day, LocalDate today) {
        return day.equals(today) ? text(TextKey.DAY_TODAY) : capitalised(day.getDayOfWeek().getDisplayName(TextStyle.SHORT, locale()));
    }

    /**
     * A day's name, as a title.
     *
     * @param day the day to name
     * @param today the day the plan was made, in the owner's home
     * @return "Today", "Tomorrow", or the weekday such as "Sunday"
     */
    String title(LocalDate day, LocalDate today) {
        if (day.equals(today)) {
            return text(TextKey.DAY_TODAY);
        }
        if (day.equals(today.plusDays(1))) {
            return text(TextKey.DAY_TOMORROW);
        }
        return capitalised(weekday(day));
    }

    /**
     * A day's name as it goes in the middle of a sentence.
     *
     * @param day the day to name
     * @param today the day the plan was made, in the owner's home
     * @return "today", "tomorrow", or the weekday as the language writes it there: "Sunday" in English, "zondag" in Dutch
     */
    String inSentence(LocalDate day, LocalDate today) {
        if (day.equals(today)) {
            return text(TextKey.DAY_TODAY_IN_SENTENCE);
        }
        if (day.equals(today.plusDays(1))) {
            return text(TextKey.DAY_TOMORROW_IN_SENTENCE);
        }
        return weekday(day);
    }

    /**
     * The locale the texts are written in, for changing a text's case.
     *
     * @return the language's locale
     */
    Locale locale() {
        return language.locale();
    }

    private String weekday(LocalDate day) {
        return day.getDayOfWeek().getDisplayName(TextStyle.FULL, locale());
    }

    private String capitalised(String text) {
        return text.substring(0, 1).toUpperCase(locale()) + text.substring(1);
    }

    /** A new format each time: a NumberFormat may not be shared between threads. */
    private String number(double value, int decimals) {
        var format = NumberFormat.getNumberInstance(locale());
        format.setMinimumFractionDigits(decimals);
        format.setMaximumFractionDigits(decimals);
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(value);
    }
}

package nl.enadeag.carchargeplanner.desktop.text;

import nl.enadeag.carchargeplanner.desktop.system.ClockFormat;
import nl.enadeag.carchargeplanner.engine.dashboard.DayName;
import java.math.RoundingMode;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.chrono.IsoChronology;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.FormatStyle;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * The app's texts in one language, from the resource bundle {@code messages_<code>.properties} beside this class, with
 * the numbers, times and day names in them written the way that language writes them, and the clock times on the
 * system's 12- or 24-hour clock. Nothing here follows the JVM's default locale: the language is the one given.
 */
public final class Messages {

    private static final String BUNDLE = Messages.class.getPackageName() + ".messages";

    private final Language language;
    private final ClockFormat clock;
    private final ResourceBundle bundle;
    private final DateTimeFormatter time;
    private final boolean twelveHour;
    private final TypedTime typed;

    /**
     * Loads the language's texts, with the times on the language's own clock.
     *
     * @param language the language to show the texts in; never null
     */
    public Messages(Language language) {
        this(language, ClockFormat.LANGUAGE);
    }

    /**
     * Loads the language's texts, with the times on the system's clock.
     *
     * @param language the language to show the texts in; never null
     * @param clock the system's 12- or 24-hour clock, or {@link ClockFormat#LANGUAGE} for the language's own; the
     *     marker of a 12-hour clock is the language's, such as "pm" in English and "p.m." in Dutch; never null
     */
    public Messages(Language language, ClockFormat clock) {
        this.language = language;
        this.clock = clock;
        // without this control, a missing bundle would quietly fall back to the JVM's default locale
        this.bundle = ResourceBundle.getBundle(BUNDLE, language.locale(),
                ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
        // the language's own hours and minutes on the clock asked for: "7:30 pm" in English and "7:30 p.m." in Dutch
        this.time = (switch (clock) {
            case LANGUAGE -> DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT);
            case TWELVE_HOUR -> DateTimeFormatter.ofLocalizedPattern("hm");
            case TWENTY_FOUR_HOUR -> DateTimeFormatter.ofLocalizedPattern("Hm");
        }).withLocale(language.locale());
        this.twelveHour = switch (clock) {
            case LANGUAGE -> isTwelveHour(DateTimeFormatterBuilder.getLocalizedDateTimePattern(null, FormatStyle.SHORT,
                    IsoChronology.INSTANCE, language.locale()));
            case TWELVE_HOUR -> true;
            case TWENTY_FOUR_HOUR -> false;
        };
        this.typed = new TypedTime(language.locale());
    }

    /** Whether a pattern of {@link DateTimeFormatter} has hours from 1 to 12, or from 0 to 11, outside its quotes. */
    private static boolean isTwelveHour(String pattern) {
        return pattern.replaceAll("'[^']*'", "").matches(".*[hK].*");
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
     * The clock the times are written on.
     *
     * @return the system's 12- or 24-hour clock these messages were made with, or {@link ClockFormat#LANGUAGE}
     */
    public ClockFormat clock() {
        return clock;
    }

    /**
     * Whether the times are written on a 12-hour clock, with a marker, so a field for a time is made wider.
     *
     * @return true on a 12-hour clock, also when it is the language's own
     */
    public boolean twelveHour() {
        return twelveHour;
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
     * @param moment the instant to tell, such as a charge window's start
     * @param zone the time zone of the owner's home, in which the time is told
     * @return hours and minutes the way the language writes them on the system's clock, such as "09:05" on a 24-hour
     *     clock and "9:05 am" on a 12-hour one
     */
    public String time(Instant moment, ZoneId zone) {
        return time.format(moment.atZone(zone));
    }

    /**
     * A time of day as {@link #time(Instant, ZoneId)} writes it, also in a field the owner types a time in.
     *
     * @param time the time, to the minute
     * @return such as "19:30" on a 24-hour clock, "7:30 pm" on a 12-hour one in English (with a narrow no-break space)
     *     and "7:30 p.m." in Dutch
     */
    public String time(LocalTime time) {
        return this.time.format(time);
    }

    /**
     * The time of day a typed text stands for, on either clock whatever the system's: "19:30" and "7:30 pm" are the
     * same time.
     *
     * @param text what the owner typed: hours and minutes from 0:00 to 23:59, a leading zero optional, such as "7:30"
     *     for 07:30; or hours from 1 to 12 with or without minutes and a marker, "am" or "pm", or the language's own
     *     such as Dutch "a.m.", in any case, with or without dots and a space, so "12 am" is 00:00 and "12 pm" noon;
     *     spaces around it are ignored
     * @return the time; empty when the text is none of these, or far longer than any of them
     */
    public Optional<LocalTime> readTime(String text) {
        return typed.read(text);
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
     * @param day the day to name, as the dashboard's model names it
     * @return "Today", or the weekday's short name such as "Fri" in English and "Vr" in Dutch
     */
    public String tab(DayName day) {
        return day instanceof DayName.Today ? text(TextKey.DAY_TODAY)
                : capitalised(day.date().getDayOfWeek().getDisplayName(TextStyle.SHORT, locale()));
    }

    /**
     * A day's name, as a title.
     *
     * @param day the day to name, as the dashboard's model names it
     * @return "Today", "Tomorrow", or the weekday such as "Sunday"
     */
    public String title(DayName day) {
        return switch (day) {
            case DayName.Today _ -> text(TextKey.DAY_TODAY);
            case DayName.Tomorrow _ -> text(TextKey.DAY_TOMORROW);
            case DayName.Other other -> capitalised(weekday(other.date()));
        };
    }

    /**
     * A day's name as it goes in the middle of a sentence.
     *
     * @param day the day to name
     * @param today the day the plan was made, in the owner's home
     * @return "today", "tomorrow", or the weekday as the language writes it there: "Sunday" in English, "zondag" in Dutch
     */
    public String inSentence(LocalDate day, LocalDate today) {
        return inSentence(DayName.of(day, today));
    }

    /**
     * A day's name as it goes in the middle of a sentence.
     *
     * @param day the day to name, as the dashboard's model names it
     * @return "today", "tomorrow", or the weekday as the language writes it there: "Sunday" in English, "zondag" in Dutch
     */
    public String inSentence(DayName day) {
        return switch (day) {
            case DayName.Today _ -> text(TextKey.DAY_TODAY_IN_SENTENCE);
            case DayName.Tomorrow _ -> text(TextKey.DAY_TOMORROW_IN_SENTENCE);
            case DayName.Other other -> weekday(other.date());
        };
    }

    /**
     * A day's name for a list that may reach beyond the coming week.
     *
     * @param day the day to name
     * @param today the day in the owner's home now
     * @return "Today", "Tomorrow", or the short weekday and date such as "Sat 17 Oct" in English and "Za 17 okt" in
     *     Dutch
     */
    public String date(LocalDate day, LocalDate today) {
        return date(DayName.of(day, today));
    }

    /**
     * A day's name for a list that may reach beyond the coming week.
     *
     * @param day the day to name, as the dashboard's model names it
     * @return "Today", "Tomorrow", or the short weekday and date such as "Sat 17 Oct" in English and "Za 17 okt" in
     *     Dutch
     */
    public String date(DayName day) {
        return day instanceof DayName.Other other
                ? capitalised(DateTimeFormatter.ofPattern("EEE d MMM", locale()).format(other.date())) : title(day);
    }

    /**
     * The locale the texts are written in, for changing a text's case.
     *
     * @return the language's locale
     */
    public Locale locale() {
        return language.locale();
    }

    /**
     * A day's weekday as it goes in the middle of a sentence, also for today and tomorrow.
     *
     * @param day the day to name
     * @return the weekday as the language writes it there: "Sunday" in English, "zondag" in Dutch
     */
    public String weekday(LocalDate day) {
        return day.getDayOfWeek().getDisplayName(TextStyle.FULL, locale());
    }

    private String capitalised(String text) {
        return text.substring(0, 1).toUpperCase(locale()) + text.substring(1);
    }

    /**
     * A number with a fixed number of decimals, such as a value in a settings field.
     *
     * @param value the number, in any unit
     * @param decimals how many decimals to show, 0 or more
     * @return such as "0.25" in English and "0,25" in Dutch for two decimals; a half is rounded away from zero
     */
    public String number(double value, int decimals) {
        // a new format each time: a NumberFormat may not be shared between threads
        var format = NumberFormat.getNumberInstance(locale());
        format.setMinimumFractionDigits(decimals);
        format.setMaximumFractionDigits(decimals);
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(value);
    }

    /**
     * A number as a settings field shows it, so that it reads back as the same number.
     *
     * @param value the number, in any unit
     * @param decimals how many decimals to show, 0 or more
     * @return such as "0.25" in English and "0,25" in Dutch for two decimals, and "1000" without a thousands
     *     separator; a half is rounded away from zero
     */
    public String plainNumber(double value, int decimals) {
        var format = NumberFormat.getNumberInstance(locale());
        format.setGroupingUsed(false);
        format.setMinimumFractionDigits(decimals);
        format.setMaximumFractionDigits(decimals);
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(value);
    }
}

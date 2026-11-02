package nl.enadeag.carchargeplanner.android.form

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.util.Locale

/** A time of day as hours and minutes on a 24-hour clock, such as 07:30, as the desktop's fields write it. */
object TimeText {

    private val CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT)

    /**
     * A time as a field shows it.
     *
     * @param time the time, to the minute
     * @return such as "07:30"
     */
    fun write(time: LocalTime): String = CLOCK.format(time)

    /**
     * The time a text stands for.
     *
     * @param text what the owner typed; spaces around it are ignored, as on the desktop
     * @return the time; null when the text is not a time such as 07:30
     */
    fun read(text: String): LocalTime? = try {
        LocalTime.parse(text.trim(), CLOCK)
    } catch (ignored: DateTimeParseException) {
        null
    }
}

package nl.enadeag.carchargeplanner.android.ui.components

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * A day as Material's date picker holds it, the UTC midnight that starts it in milliseconds, and back: a date alone,
 * which no time zone moves, the home's or the phone's.
 */
internal object PickerDays {

    /**
     * @return the milliseconds from 1970 to the day's midnight in UTC
     */
    fun millis(day: LocalDate): Long = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    /**
     * @param millis milliseconds from 1970, as the picker gives them, a UTC midnight
     */
    fun day(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
}

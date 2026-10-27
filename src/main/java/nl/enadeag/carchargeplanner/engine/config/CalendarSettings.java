package nl.enadeag.carchargeplanner.engine.config;

import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting;
import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalTime;
import java.util.Optional;

/**
 * The owner's settings for the trips read from the calendar that are not secret; the calendar's iCal link is a secret
 * setting, kept with the API keys.
 *
 * @param icsFile the {@code .ics} file the calendar is read from, by its whole path; empty when there is none
 * @param travelMargin the time added before and after each event's travel time, in whole minutes, from 0 to
 *     {@link #MAX_TRAVEL_MARGIN}
 * @param allDayDeparture when the car leaves on the first day of an all-day event, to the minute
 * @param allDayReturn when the car is back on the last day of an all-day event, to the minute, after
 *     {@code allDayDeparture}
 */
public record CalendarSettings(Optional<Path> icsFile, Duration travelMargin, LocalTime allDayDeparture,
        LocalTime allDayReturn) {

    /** The longest travel margin: four hours. */
    public static final Duration MAX_TRAVEL_MARGIN = Duration.ofHours(4);

    /** The settings unless the owner changes them: no file, a travel margin of 15 minutes, all day from 09:00 to 18:00. */
    public static final CalendarSettings STANDARD = new CalendarSettings(Optional.empty(), Duration.ofMinutes(15),
            LocalTime.of(9, 0), LocalTime.of(18, 0));

    /**
     * Checks the values.
     *
     * @throws InvalidSetting when the file is not a whole path, the travel margin is negative or longer than
     *     {@link #MAX_TRAVEL_MARGIN}, or the return is not after the departure
     * @throws IllegalArgumentException when the margin or a time is not a whole minute, which the config file cannot
     *     hold
     */
    public CalendarSettings {
        if (icsFile.isPresent() && !icsFile.get().isAbsolute()) {
            throw new InvalidSetting(ConfigSetting.CALENDAR_FILE, InvalidSetting.Rule.NOT_A_FULL_PATH,
                    "icsFile must be a whole path, not " + icsFile.get());
        }
        if (travelMargin.isNegative() || travelMargin.compareTo(MAX_TRAVEL_MARGIN) > 0) {
            throw new InvalidSetting(ConfigSetting.TRAVEL_MARGIN, InvalidSetting.Rule.OUT_OF_RANGE,
                    "travelMarginMinutes must be from 0 to " + MAX_TRAVEL_MARGIN.toMinutes());
        }
        if (travelMargin.toSecondsPart() != 0 || travelMargin.toNanosPart() != 0) {
            throw new IllegalArgumentException("travelMargin must be whole minutes, not " + travelMargin);
        }
        if (allDayDeparture.getSecond() != 0 || allDayDeparture.getNano() != 0) {
            throw new IllegalArgumentException("allDay departure must be a whole minute, not " + allDayDeparture);
        }
        if (allDayReturn.getSecond() != 0 || allDayReturn.getNano() != 0) {
            throw new IllegalArgumentException("allDay return must be a whole minute, not " + allDayReturn);
        }
        if (!allDayReturn.isAfter(allDayDeparture)) {
            throw new InvalidSetting(ConfigSetting.ALL_DAY_TIMES, InvalidSetting.Rule.NOT_AFTER_DEPARTURE,
                    "allDay return must be after the departure");
        }
    }
}

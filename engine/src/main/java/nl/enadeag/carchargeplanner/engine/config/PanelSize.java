package nl.enadeag.carchargeplanner.engine.config;

import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting;
import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting;
import java.time.Year;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * How big a panel array is: either its peak power in kWp, as the owner once entered it, or its number of panels with
 * the power per panel or the year they were placed, from which the peak power follows.
 */
public sealed interface PanelSize permits PanelSize.Peak, PanelSize.Panels {

    /**
     * The peak power of the whole array, which the forecast providers are given.
     *
     * @return the peak power in kWp, more than 0
     */
    double kwp();

    /**
     * The array's peak power as such.
     *
     * @param kwp the peak power in kWp, more than 0
     */
    record Peak(double kwp) implements PanelSize {

        /**
         * Checks the value.
         *
         * @throws InvalidSetting when the peak power is not more than 0
         */
        public Peak {
            if (kwp <= 0 || !Double.isFinite(kwp)) {
                throw new InvalidSetting(ConfigSetting.PANEL_SIZE, InvalidSetting.Rule.OUT_OF_RANGE,
                        "kwp must be more than 0");
            }
        }
    }

    /**
     * The array as a number of panels: with the power per panel when the owner knows it, else estimated from the year
     * the panels were placed.
     *
     * @param count the number of panels, from {@link #MIN_COUNT} to {@link #MAX_COUNT}
     * @param wattPeak the power per panel in Wp, from {@link #MIN_WATT_PEAK} to {@link #MAX_WATT_PEAK}; empty when the
     *     owner does not know it
     * @param placed the year the panels were placed, {@link #FIRST_YEAR} or later; may be empty only when
     *     {@code wattPeak} is given
     */
    record Panels(int count, OptionalInt wattPeak, Optional<Year> placed) implements PanelSize {

        /** The fewest panels an array has. */
        public static final int MIN_COUNT = 1;

        /** The most panels an array has. */
        public static final int MAX_COUNT = 200;

        /** The lowest power per panel, in Wp. */
        public static final int MIN_WATT_PEAK = 50;

        /** The highest power per panel, in Wp. */
        public static final int MAX_WATT_PEAK = 1000;

        /** The earliest year panels may have been placed. */
        public static final Year FIRST_YEAR = Year.of(1990);

        /**
         * Checks the values.
         *
         * @throws InvalidSetting when the count, the power per panel or the year is outside its range, or neither the
         *     power per panel nor the year is given
         */
        public Panels {
            if (count < MIN_COUNT || count > MAX_COUNT) {
                throw new InvalidSetting(ConfigSetting.PANEL_COUNT, InvalidSetting.Rule.PANEL_COUNT_OUT_OF_RANGE,
                        "count must be from " + MIN_COUNT + " to " + MAX_COUNT + ", not " + count);
            }
            if (wattPeak.isPresent() && (wattPeak.getAsInt() < MIN_WATT_PEAK || wattPeak.getAsInt() > MAX_WATT_PEAK)) {
                throw new InvalidSetting(ConfigSetting.PANEL_POWER, InvalidSetting.Rule.PANEL_POWER_OUT_OF_RANGE,
                        "wpPerPanel must be from " + MIN_WATT_PEAK + " to " + MAX_WATT_PEAK + ", not "
                                + wattPeak.getAsInt());
            }
            if (placed.isPresent() && placed.get().isBefore(FIRST_YEAR)) {
                throw new InvalidSetting(ConfigSetting.PANEL_YEAR, InvalidSetting.Rule.YEAR_PLACED_OUT_OF_RANGE,
                        "yearPlaced must be " + FIRST_YEAR + " or later, not " + placed.get());
            }
            if (wattPeak.isEmpty() && placed.isEmpty()) {
                throw new InvalidSetting(ConfigSetting.PANEL_YEAR, InvalidSetting.Rule.NO_YEAR_OR_POWER,
                        "yearPlaced is needed when wpPerPanel is not given");
            }
        }

        /**
         * The power per panel the peak power is worked out with.
         *
         * @return the power per panel in Wp as given, or else the typical one for the year placed
         */
        public int wattPeakUsed() {
            return wattPeak.isPresent() ? wattPeak.getAsInt() : typicalWattPeak(placed.orElseThrow());
        }

        /**
         * The peak power of all panels together.
         *
         * @return the number of panels times {@link #wattPeakUsed()}, in kWp
         */
        @Override
        public double kwp() {
            return count * wattPeakUsed() / 1000.0;
        }

        /**
         * Checks that the panels were not placed after this year, as a client does with what the owner entered; a
         * file is read without a clock, so the record itself cannot.
         *
         * @param thisYear the current year in the home's or the device's time zone
         * @return this array's size
         * @throws InvalidSetting when the year placed is after {@code thisYear}
         */
        public Panels notPlacedAfter(Year thisYear) {
            if (placed.isPresent() && placed.get().isAfter(thisYear)) {
                throw new InvalidSetting(ConfigSetting.PANEL_YEAR, InvalidSetting.Rule.YEAR_PLACED_OUT_OF_RANGE,
                        "yearPlaced must be " + thisYear + " or earlier, not " + placed.get());
            }
            return this;
        }

        /**
         * The usual power of one panel placed in a year: a series derived from module efficiency per year (LBNL's
         * Tracking the Sun) and published ranges per period, not a published table, so a year may be 30 to 40 Wp off.
         *
         * @param placed the year the panel was placed; any year
         * @return the power per panel in Wp: 220 up to 2010, rising to 430 from 2024
         */
        public static int typicalWattPeak(Year placed) {
            int year = placed.getValue();
            if (year <= 2010) {
                return 220;
            }
            if (year <= 2013) {
                return 245;
            }
            if (year <= 2015) {
                return 260;
            }
            if (year <= 2017) {
                return 280;
            }
            if (year <= 2019) {
                return 300;
            }
            if (year == 2020) {
                return 330;
            }
            if (year == 2021) {
                return 360;
            }
            if (year == 2022) {
                return 380;
            }
            if (year == 2023) {
                return 400;
            }
            return 430;
        }
    }
}

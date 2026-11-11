package nl.enadeag.carchargeplanner.desktop.settings.form;

import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.config.PanelArray;
import nl.enadeag.carchargeplanner.engine.config.PanelSize;
import nl.enadeag.carchargeplanner.engine.setting.ConfigSetting;
import nl.enadeag.carchargeplanner.engine.setting.InvalidSetting;
import java.time.Year;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.regex.Pattern;

/**
 * A panel array as its card shows it: the number of panels, the year placed and the power per panel as typed, the
 * tilt and the direction as the degrees they store, and the losses as a percentage.
 *
 * @param name the array's name; may be empty
 * @param count the number of panels as typed; may be empty
 * @param yearPlaced the year the panels were placed as typed; may be empty
 * @param wattPeak the power per panel in Wp as typed; may be empty
 * @param tiltDeg the tilt to store, in degrees from flat: the chosen tilt's, or the stored one while the owner did not
 *     choose; empty for a new array without a choice
 * @param azimuthDeg the direction to store, in degrees from south: the chosen direction's, or the stored one while the
 *     owner did not choose; empty for a new array without a choice
 * @param lossesPct the system's losses, in %
 * @param kept the stored peak power, which stays while the count, the year and the power are all empty; empty for an
 *     array of panels or a new one
 * @param sizeTyped whether the owner changed the count, the year or the power since the card showed the array; only
 *     then may the year placed not be after this year, so a stored array the owner left alone is kept as it was read,
 *     even when the clock is set back or the file came from a time zone already in the new year
 */
public record PanelArrayEntry(String name, String count, String yearPlaced, String wattPeak, OptionalDouble tiltDeg,
        OptionalDouble azimuthDeg, double lossesPct, Optional<PanelSize.Peak> kept, boolean sizeTyped) {

    private static final Pattern WHOLE = Pattern.compile("[0-9]{1,9}");

    /** The array as entered, or what is wrong with it. */
    public sealed interface Checked {

        /**
         * The records accepted the array.
         *
         * @param array the array
         */
        record Accepted(PanelArray array) implements Checked {
        }

        /**
         * A text is not a whole number, a choice is not made, or a record refused a value.
         *
         * @param setting the setting whose field shows the problem
         * @param problem what the field says, in the owner's language
         */
        record Refused(ConfigSetting setting, String problem) implements Checked {
        }
    }

    /**
     * What the card shows for a stored array.
     *
     * @param array the array as the engine keeps it
     * @return its values as the fields show them: an array kept as a peak power shows no count, year or power
     */
    public static PanelArrayEntry of(PanelArray array) {
        var losses = array.systemLosses() * 100;
        var tilt = OptionalDouble.of(array.tiltDeg());
        var direction = OptionalDouble.of(array.azimuthDeg());
        if (array.size() instanceof PanelSize.Panels panels) {
            return new PanelArrayEntry(array.name(), String.valueOf(panels.count()),
                    panels.placed().map(year -> String.valueOf(year.getValue())).orElse(""),
                    panels.wattPeak().isPresent() ? String.valueOf(panels.wattPeak().getAsInt()) : "", tilt, direction,
                    losses, Optional.empty(), false);
        }
        return new PanelArrayEntry(array.name(), "", "", "", tilt, direction, losses,
                Optional.of(new PanelSize.Peak(array.kwp())), false);
    }

    /**
     * What the card shows for a new array: nothing entered or chosen, and the typical losses.
     *
     * @return the empty entry
     */
    public static PanelArrayEntry empty() {
        return new PanelArrayEntry("", "", "", "", OptionalDouble.empty(), OptionalDouble.empty(),
                PanelArray.TYPICAL.systemLosses() * 100, Optional.empty(), false);
    }

    /**
     * Has the records check the entry. The fields are looked at in the card's order: the number of panels, the
     * direction, the tilt, the year and the power; then the records' own rules.
     *
     * @param thisYear the current year, which a typed year placed may not be after
     * @param messages the texts, in the owner's language
     * @return the array, or the first problem and the field that shows it
     */
    public Checked check(Year thisYear, Messages messages) {
        try {
            if (!keepsThePeakPower()) {
                panelCount();
            }
            if (azimuthDeg.isEmpty()) {
                return notChosen(ConfigSetting.PANEL_DIRECTION, messages);
            }
            if (tiltDeg.isEmpty()) {
                return notChosen(ConfigSetting.PANEL_TILT, messages);
            }
            var size = keepsThePeakPower() ? kept.orElseThrow() : panels(thisYear);
            return new Checked.Accepted(new PanelArray(name, size, tiltDeg.getAsDouble(), azimuthDeg.getAsDouble(),
                    lossesPct / 100));
        } catch (InvalidSetting e) {
            return new Checked.Refused(e.setting(), ProblemTexts.of(e.rule(), messages));
        }
    }

    /**
     * The peak power the typed panels give together, which the card shows below them.
     *
     * @param thisYear the current year, which a typed year placed may not be after
     * @return the peak power in kWp; empty while the count, the year or the power is missing or refused
     */
    public OptionalDouble together(Year thisYear) {
        try {
            return keepsThePeakPower() ? OptionalDouble.empty() : OptionalDouble.of(panels(thisYear).kwp());
        } catch (InvalidSetting e) {
            return OptionalDouble.empty();
        }
    }

    /**
     * The power per panel the app counts on while the power is left empty: the typical one for the year typed.
     *
     * @param thisYear the current year, which the year placed may not be after
     * @return the power in Wp; empty while the year is not one the array may have
     */
    public OptionalInt typicalWattPeak(Year thisYear) {
        var year = whole(yearPlaced);
        return year.isEmpty() ? OptionalInt.empty() : PanelSize.Panels.typicalWattPeakFor(year.getAsInt(), thisYear);
    }

    /**
     * Whether the array keeps its stored peak power, as nothing of the new fields was typed.
     *
     * @return true for an array stored as a peak power whose count, year and power are all empty
     */
    public boolean keepsThePeakPower() {
        return kept.isPresent() && count.isBlank() && yearPlaced.isBlank() && wattPeak.isBlank();
    }

    private int panelCount() {
        var panels = whole(count);
        if (panels.isEmpty()) {
            throw new InvalidSetting(ConfigSetting.PANEL_COUNT, InvalidSetting.Rule.PANEL_COUNT_OUT_OF_RANGE,
                    "count must be a whole number");
        }
        return panels.getAsInt();
    }

    private PanelSize.Panels panels(Year thisYear) {
        int panels = panelCount();
        var year = whole(yearPlaced);
        if (year.isEmpty() && !yearPlaced.isBlank()) {
            throw new InvalidSetting(ConfigSetting.PANEL_YEAR, InvalidSetting.Rule.YEAR_PLACED_OUT_OF_RANGE,
                    "yearPlaced must be a whole number");
        }
        var power = whole(wattPeak);
        if (power.isEmpty() && !wattPeak.isBlank()) {
            throw new InvalidSetting(ConfigSetting.PANEL_POWER, InvalidSetting.Rule.PANEL_POWER_OUT_OF_RANGE,
                    "wpPerPanel must be a whole number");
        }
        var placed = year.isPresent() ? Optional.of(Year.of(year.getAsInt())) : Optional.<Year>empty();
        var size = new PanelSize.Panels(panels, power, placed);
        return sizeTyped ? size.notPlacedAfter(thisYear) : size;
    }

    private static Checked notChosen(ConfigSetting setting, Messages messages) {
        return new Checked.Refused(setting, messages.text(TextKey.PROBLEM_NOT_CHOSEN));
    }

    private static OptionalInt whole(String text) {
        var stripped = text.strip();
        return WHOLE.matcher(stripped).matches() ? OptionalInt.of(Integer.parseInt(stripped)) : OptionalInt.empty();
    }
}

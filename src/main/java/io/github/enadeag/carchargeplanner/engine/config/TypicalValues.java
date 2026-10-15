package io.github.enadeag.carchargeplanner.engine.config;

import io.github.enadeag.carchargeplanner.engine.setting.Setting;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Which values of the system are typical values: filled in with a common value because the owner did not know them
 * (see "Typical value" in CONTEXT.md), until the owner enters their own. Planning preferences are choices and never
 * typical. In JSON the settings are their names, such as {@code "CAR_BATTERY"}.
 *
 * @param system the typical values of the house, the car and the charger
 * @param panels per panel array, in the order of the system's arrays, its typical values; an array past the end of
 *     the list has none
 */
public record TypicalValues(Set<Setting> system, List<Set<Setting>> panels) {

    /** The settings of the house, the car and the charger that can hold a typical value. */
    public static final Set<Setting> SYSTEM_SETTINGS = Set.copyOf(EnumSet.of(Setting.HOUSE_BASELOAD,
            Setting.CAR_BATTERY, Setting.CAR_CONSUMPTION, Setting.CAR_DAILY_DISTANCE, Setting.CHARGER_PHASES,
            Setting.CHARGER_MIN_CURRENT, Setting.CHARGER_MAX_CURRENT, Setting.CHARGER_VOLTAGE,
            Setting.CHARGER_EFFICIENCY));

    /** The settings of a panel array that can hold a typical value; its name never does. */
    public static final Set<Setting> PANEL_SETTINGS = Set.copyOf(EnumSet.of(Setting.PANEL_SIZE, Setting.PANEL_TILT,
            Setting.PANEL_DIRECTION, Setting.PANEL_LOSSES));

    /** No typical values: the owner entered every value. */
    public static final TypicalValues NONE = new TypicalValues(Set.of(), List.of());

    /**
     * Checks that each setting can be typical where it is listed, and copies the sets.
     *
     * @throws IllegalArgumentException when a setting is listed that cannot be typical there, such as a planning
     *     preference
     */
    public TypicalValues {
        if (!SYSTEM_SETTINGS.containsAll(system)) {
            throw new IllegalArgumentException("typical.system may only list " + SYSTEM_SETTINGS);
        }
        if (!panels.stream().allMatch(PANEL_SETTINGS::containsAll)) {
            throw new IllegalArgumentException("typical.panels may only list " + PANEL_SETTINGS);
        }
        system = Set.copyOf(system);
        panels = panels.stream().map(Set::copyOf).toList();
    }

    /**
     * One panel array's typical values.
     *
     * @param array the array's place in the system's list, from 0
     * @return its typical values; none for an array the list does not reach
     */
    public Set<Setting> ofPanel(int array) {
        return array < panels.size() ? panels.get(array) : Set.of();
    }
}

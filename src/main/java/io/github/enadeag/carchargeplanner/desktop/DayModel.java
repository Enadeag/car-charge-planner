package io.github.enadeag.carchargeplanner.desktop;

import java.util.List;

/**
 * One day of the plan, as its tab and main card show it.
 *
 * @param tab the tab's short label: "Today", "Fri"
 * @param title "Today", "Tomorrow" or the weekday
 * @param hero the main card's large text, the charge window; empty when there is none
 * @param facts what to set and how much it brings: "Set 10 A (2.3 kW)", "about 7.9 kWh"
 * @param note a sentence below the figures; may be empty
 * @param spoken the main card in one sentence, for a screen reader
 */
record DayModel(
        String tab,
        String title,
        DayKind kind,
        String heading,
        String hero,
        List<String> facts,
        List<Chip> chips,
        String note,
        String spoken) {

    DayModel {
        facts = List.copyOf(facts);
        chips = List.copyOf(chips);
    }
}

package io.github.enadeag.carchargeplanner.desktop.dashboard;

/**
 * Advice to charge from the grid, because the sun will not keep the car above its minimum charge level.
 *
 * @param label the advice's name, on its chip: "Grid advice"
 * @param title how much to charge and when: "Charge 8.4 kWh from the grid: Sunday 09:00 – 15:10."
 * @param detail why, and what to set: "Without it the battery ends Sunday at 17 %, ..."
 */
record GridAdviceModel(String label, String title, String detail) {

    /**
     * The advice in one sentence, for a screen reader.
     *
     * @return the label, then the title and the detail
     */
    String spoken() {
        return label + ": " + title + " " + detail;
    }
}

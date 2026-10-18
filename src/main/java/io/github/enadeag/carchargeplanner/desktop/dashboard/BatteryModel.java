package io.github.enadeag.carchargeplanner.desktop.dashboard;

/**
 * One day's battery as the battery bar draws it.
 *
 * @param levelPct the battery level at the start: now for today, from 0 to 100
 * @param afterPct the battery level once the day's windows are done, from 0 to 100
 * @param minimumPct the minimum charge level, from 0 to 100
 * @param targetPct the target charge level, from 0 to 100
 * @param levels the levels in words, such as "50 → 74 %"
 * @param minimumText the minimum's mark, such as "min 30"
 * @param targetText the target's mark, such as "target 80"
 * @param editable true for today, whose level the owner can enter
 * @param spoken the battery in words, for screen readers
 */
record BatteryModel(
        double levelPct,
        double afterPct,
        double minimumPct,
        double targetPct,
        String levels,
        String minimumText,
        String targetText,
        boolean editable,
        String spoken) {

    /**
     * Checks the ranges.
     *
     * @throws IllegalArgumentException when a level is outside 0 to 100
     */
    BatteryModel {
        for (double pct : new double[] {levelPct, afterPct, minimumPct, targetPct}) {
            if (!(pct >= 0 && pct <= 100)) {
                throw new IllegalArgumentException("a battery level must be from 0 to 100, not " + pct);
            }
        }
    }
}

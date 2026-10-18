package io.github.enadeag.carchargeplanner.desktop.dashboard;

/**
 * A short figure about one source of energy: "92 % sun".
 *
 * @param source where the energy in the figure comes from or goes to
 * @param text the figure in words, such as "92 % sun" or "Battery 50 → 74 %"
 */
record Chip(Source source, String text) {

    /** Colour means source: sun amber, grid blue, car and battery green. */
    enum Source {
        /** Solar energy, amber. */
        SUN,
        /** Energy from the grid, blue. */
        GRID,
        /** The car and its battery, green. */
        CAR
    }
}

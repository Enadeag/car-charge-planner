package nl.enadeag.carchargeplanner.desktop.dashboard.parts;

/** Where energy comes from or goes to; its colour on the dashboard means it: sun amber, grid blue, car green. */
public enum EnergySource {
    /** Solar energy, amber. */
    SUN,
    /** Energy from the grid, blue. */
    GRID,
    /** The car and its battery, green. */
    CAR
}

package nl.enadeag.carchargeplanner.desktop.dashboard.parts;

/** Where energy comes from or goes to; its colour on the dashboard means it: solar amber, grid blue, car green. */
public enum EnergySource {
    /** Solar energy, amber. */
    SOLAR,
    /** Energy from the grid, blue. */
    GRID,
    /** The car and its battery, green. */
    CAR
}

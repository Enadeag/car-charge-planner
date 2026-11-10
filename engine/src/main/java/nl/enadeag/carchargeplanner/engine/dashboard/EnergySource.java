package nl.enadeag.carchargeplanner.engine.dashboard;

/** Where energy comes from or goes to; each client shows each source in colours of its own. */
public enum EnergySource {

    /** The solar panels. */
    SOLAR,

    /** The grid. */
    GRID,

    /** The car and its battery. */
    CAR
}

package io.github.enadeag.carchargeplanner.desktop;

/** A short figure in the colour of its source, with the source's glyph beside it: "92 % sun". */
record Chip(Source source, String text) {

    /** Colour means source: sun amber, grid blue, car and battery green. */
    enum Source {
        SUN,
        GRID,
        CAR
    }
}

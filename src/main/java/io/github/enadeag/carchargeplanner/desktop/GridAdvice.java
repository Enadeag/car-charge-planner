package io.github.enadeag.carchargeplanner.desktop;

/** Advice to charge from the grid, because the sun will not keep the car above its minimum charge level. */
record GridAdvice(String title, String detail) {

    String spoken() {
        return "Grid advice: " + title + " " + detail;
    }
}

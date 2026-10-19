package nl.enadeag.carchargeplanner.desktop.settings.page;

import javafx.geometry.Dimension2D;
import javafx.geometry.Rectangle2D;

/** The size Settings and set-up open with: so many lines of the system's text, but never larger than the screen. */
public final class WindowSize {

    private WindowSize() {
    }

    /**
     * The window's size on a screen.
     *
     * @param widthEm the width it would like, in lines of text, more than 0
     * @param heightEm the height it would like, in lines of text, more than 0
     * @param em the system's text size in pixels, more than 0; larger text makes a larger window
     * @param screen the free part of the screen, without a taskbar or panel
     * @return the size in pixels, at most the screen's; a page that does not fit then scrolls
     * @throws IllegalArgumentException when a size is not more than 0
     */
    public static Dimension2D on(double widthEm, double heightEm, double em, Rectangle2D screen) {
        if (widthEm <= 0 || Double.isNaN(widthEm) || heightEm <= 0 || Double.isNaN(heightEm)) {
            throw new IllegalArgumentException("a window's size in lines must be more than 0");
        }
        if (em <= 0 || Double.isNaN(em)) {
            throw new IllegalArgumentException("the text size must be more than 0");
        }
        return new Dimension2D(Math.min(widthEm * em, screen.getWidth()), Math.min(heightEm * em, screen.getHeight()));
    }
}

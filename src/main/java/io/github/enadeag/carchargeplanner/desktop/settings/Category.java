package io.github.enadeag.carchargeplanner.desktop.settings;

import io.github.enadeag.carchargeplanner.desktop.TextKey;

/** A group of settings with a page of its own, in the order of Settings' sidebar. */
public enum Category {
    /** The panel arrays. */
    SOLAR(TextKey.CATEGORY_SOLAR),
    /** Where the home is, and its time zone. */
    LOCATION(TextKey.CATEGORY_LOCATION),
    /** The car, and the house's own use of power. */
    CAR_HOUSE(TextKey.CATEGORY_CAR_HOUSE),
    /** The wall charger. */
    CHARGER(TextKey.CATEGORY_CHARGER),
    /** The planning preferences: charge levels, how cautious the plan is, and notifications. */
    PLANNING(TextKey.CATEGORY_PLANNING),
    /** The desktop app's own settings: how it shows itself, its language, the units, and start at login. */
    APP(TextKey.CATEGORY_APP),
    /** The version, the author, the licence, the source and where the files are kept; always the last. */
    ABOUT(TextKey.CATEGORY_ABOUT);

    private final TextKey title;

    Category(TextKey title) {
        this.title = title;
    }

    /**
     * The category's name in the sidebar and above its page.
     *
     * @return the text's key
     */
    TextKey title() {
        return title;
    }
}

package nl.enadeag.carchargeplanner.desktop.settings.setup;

import nl.enadeag.carchargeplanner.desktop.text.TextKey;

/** The steps of set-up, one subject each, in the order the owner goes through them. */
enum SetUpStep {
    WELCOME(TextKey.SET_UP_WELCOME),
    APP(TextKey.CATEGORY_APP),
    /** Where the home is, found by the place search or typed as coordinates; it must be chosen. */
    LOCATION(TextKey.CATEGORY_LOCATION),
    SOLAR(TextKey.CATEGORY_SOLAR),
    CAR(TextKey.SET_UP_CAR),
    CHARGER(TextKey.CATEGORY_CHARGER),
    FORECAST(TextKey.CATEGORY_FORECAST),
    /** The weekly away periods, which may be skipped; set-up keeps the typical house's use, asking for none. */
    AWAY(TextKey.SET_UP_AWAY),
    /** One forecast from the primary provider for the place entered; a failure does not stop set-up. */
    CHECK(TextKey.SET_UP_CHECK),
    /** The owner's dashboard with their own plan, before Finish saves it; always the last. */
    PREVIEW(TextKey.SET_UP_PREVIEW);

    private final TextKey title;

    SetUpStep(TextKey title) {
        this.title = title;
    }

    TextKey title() {
        return title;
    }

    /**
     * @return from 1 for the first step to {@link #count()} for the last
     */
    int number() {
        return ordinal() + 1;
    }

    static int count() {
        return values().length;
    }

    boolean isLast() {
        return this == PREVIEW;
    }

    /**
     * @return the next step; the last step for the last
     */
    SetUpStep next() {
        return values()[Math.min(ordinal() + 1, count() - 1)];
    }

    /**
     * @return the previous step; the first step for the first
     */
    SetUpStep previous() {
        return values()[Math.max(ordinal() - 1, 0)];
    }
}

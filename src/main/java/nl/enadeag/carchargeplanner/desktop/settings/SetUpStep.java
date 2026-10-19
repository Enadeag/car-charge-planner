package nl.enadeag.carchargeplanner.desktop.settings;

import nl.enadeag.carchargeplanner.desktop.text.TextKey;

/** The steps of set-up, one subject each, in the order the owner goes through them. */
enum SetUpStep {
    /** What the app does, what leaves the computer, and that unknown values can be typical values. */
    WELCOME(TextKey.SETUP_WELCOME),
    /** The app's own settings: language, appearance, units, how it shows itself, start at login. */
    APP(TextKey.CATEGORY_APP),
    /** Where the home is, found by the place search or typed as coordinates; it must be chosen. */
    LOCATION(TextKey.CATEGORY_LOCATION),
    /** The panel arrays. */
    SOLAR(TextKey.CATEGORY_SOLAR),
    /** The car. */
    CAR(TextKey.SETUP_CAR),
    /** The wall charger. */
    CHARGER(TextKey.CATEGORY_CHARGER),
    /** The forecast providers and their API keys. */
    FORECAST(TextKey.CATEGORY_FORECAST),
    /** What makes the plan better and may be skipped: the house's own use and the weekly away periods. */
    MORE(TextKey.SETUP_MORE),
    /** One forecast from the primary provider for the place entered; a failure does not stop set-up. */
    CHECK(TextKey.SETUP_CHECK),
    /** The owner's dashboard with their own plan, before Finish saves it; always the last. */
    PREVIEW(TextKey.SETUP_PREVIEW);

    private final TextKey title;

    SetUpStep(TextKey title) {
        this.title = title;
    }

    /**
     * The step's name above its page and in the progress.
     *
     * @return the text's key
     */
    TextKey title() {
        return title;
    }

    /**
     * The step's place, for the progress.
     *
     * @return from 1 for the first step to {@link #count()} for the last
     */
    int number() {
        return ordinal() + 1;
    }

    /**
     * How many steps there are.
     *
     * @return the number of steps, at least 1
     */
    static int count() {
        return values().length;
    }

    /**
     * Whether this is the last step, whose Next is Finish.
     *
     * @return true for the preview
     */
    boolean isLast() {
        return this == PREVIEW;
    }

    /**
     * The step after this one.
     *
     * @return the next step; the last step for the last
     */
    SetUpStep next() {
        return values()[Math.min(ordinal() + 1, count() - 1)];
    }

    /**
     * The step before this one.
     *
     * @return the previous step; the first step for the first
     */
    SetUpStep previous() {
        return values()[Math.max(ordinal() - 1, 0)];
    }
}

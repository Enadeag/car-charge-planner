package io.github.enadeag.carchargeplanner.desktop.settings;

import java.util.List;

/** What pressing Save came to. */
sealed interface SaveOutcome {

    /** Everything was stored. */
    record Saved() implements SaveOutcome {
    }

    /**
     * Nothing was stored, because values were refused; each shows its problem next to its field.
     *
     * @param categories the categories with a refused value, in the sidebar's order; at least one
     */
    record Refused(List<Category> categories) implements SaveOutcome {

        /** Copies the list. */
        public Refused {
            categories = List.copyOf(categories);
        }
    }

    /**
     * A file could not be written; what was stored before that stays stored.
     *
     * @param detail the system's error, as it gave it
     */
    record Failed(String detail) implements SaveOutcome {
    }
}

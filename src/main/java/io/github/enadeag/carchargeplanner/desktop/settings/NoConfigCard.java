package io.github.enadeag.carchargeplanner.desktop.settings;

import io.github.enadeag.carchargeplanner.desktop.text.Messages;
import io.github.enadeag.carchargeplanner.desktop.text.TextKey;

/** The card a page shows in place of its settings when the engine's config file cannot be read. */
final class NoConfigCard {

    private NoConfigCard() {
    }

    /**
     * Makes the card.
     *
     * @param reason the engine's words about the config file, in English; never empty
     * @param messages the texts, in the owner's language
     * @return a card that says the settings cannot be edited now, and why
     */
    static Card card(String reason, Messages messages) {
        return new Card(messages.text(TextKey.SETTINGS_NO_CONFIG), messages.text(TextKey.SETTINGS_NO_CONFIG_HINT),
                FieldRow.paragraph(reason, "note"));
    }
}

package nl.enadeag.carchargeplanner.desktop.settings.page;

import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.desktop.theme.Texts;

/** The card a page shows in place of its settings when the engine's config file cannot be read. */
public final class NoConfigCard {

    private NoConfigCard() {
    }

    /**
     * Makes the card.
     *
     * @param reason the engine's words about the config file, in English; never empty
     * @param messages the texts, in the owner's language
     * @return a card that says the settings cannot be edited now, and why
     */
    public static Card card(String reason, Messages messages) {
        return new Card(messages.text(TextKey.SETTINGS_NO_CONFIG), messages.text(TextKey.SETTINGS_NO_CONFIG_HINT),
                Texts.paragraph(reason, "note"));
    }
}

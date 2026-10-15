package io.github.enadeag.carchargeplanner.desktop.settings;

/** A page whose settings the engine keeps in its config file. */
interface ConfigPage extends Page, ConfigPart {

    /**
     * Shows why the settings cannot be edited, in place of the cards: the config file could not be read.
     *
     * @param reason the engine's words about the config file, in English; never empty
     */
    void showUnavailable(String reason);
}

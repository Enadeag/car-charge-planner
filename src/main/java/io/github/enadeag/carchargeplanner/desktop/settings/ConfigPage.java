package io.github.enadeag.carchargeplanner.desktop.settings;

import io.github.enadeag.carchargeplanner.engine.config.EngineConfig;
import java.util.Optional;

/** A page whose settings the engine keeps in its config file. */
interface ConfigPage extends Page {

    /**
     * Shows the stored values, as the ones not edited.
     *
     * @param config the config as the engine holds it now
     */
    void load(EngineConfig config);

    /**
     * Shows why the settings cannot be edited, in place of the cards: the config file could not be read.
     *
     * @param reason the engine's words about the config file, in English; never empty
     */
    void showUnavailable(String reason);

    /**
     * Puts the page's values in a config, after the engine's records checked them. A value they refuse shows its
     * problem next to its field; problems shown before are taken away first.
     *
     * @param config the config to change, with the other pages' values in it already
     * @return the config with this page's values; the same config when nothing on the page changed; empty when a value
     *     is refused
     */
    Optional<EngineConfig> applyTo(EngineConfig config);
}

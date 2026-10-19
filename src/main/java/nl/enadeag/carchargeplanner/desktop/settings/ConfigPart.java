package nl.enadeag.carchargeplanner.desktop.settings;

import nl.enadeag.carchargeplanner.engine.config.EngineConfig;
import java.util.Optional;

/**
 * Settings the engine keeps in its config file, shown on one or more cards; Settings puts parts on its pages, set-up
 * on its steps.
 */
interface ConfigPart {

    /**
     * Shows the values of a config, as the ones not edited.
     *
     * @param config the config as the engine holds it now, or set-up's draft
     */
    void load(EngineConfig config);

    /**
     * Puts the part's values in a config, after the engine's records checked them. A value they refuse shows its
     * problem next to its field; problems shown before are taken away first.
     *
     * @param config the config to change, with the other parts' values in it already
     * @return the config with this part's values; the same config when nothing in the part changed since it was
     *     loaded; empty when a value is refused
     */
    Optional<EngineConfig> applyTo(EngineConfig config);

    /**
     * Calls an action whenever a value in the part changes, by the owner or by loading.
     *
     * @param action what to run, on the JavaFX thread
     */
    void onChange(Runnable action);

    /**
     * Whether the owner changed something in the part since it was loaded.
     *
     * @return true when applying it would change the config
     */
    boolean isChanged();
}

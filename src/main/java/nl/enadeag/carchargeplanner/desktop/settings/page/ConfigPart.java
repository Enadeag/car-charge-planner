package nl.enadeag.carchargeplanner.desktop.settings.page;

import nl.enadeag.carchargeplanner.engine.config.EngineConfig;
import java.util.Optional;

/**
 * Settings the engine keeps in its config file, shown on one or more cards; Settings puts parts on its pages, set-up
 * on its steps.
 */
public interface ConfigPart {

    /**
     * Shows the values of a config, as the ones not edited.
     *
     * @param config the config as the engine holds it now, or set-up's draft
     */
    void load(EngineConfig config);

    /**
     * Puts the part's values as entered in a config, after the engine's records checked them. Only reads: no problem
     * shows or goes, and the fields keep what the owner typed as it is; {@link #showProblems()} shows them.
     *
     * @param config the config to change, with the other parts' values in it already
     * @return the config with this part's values; the same config when nothing in the part changed since it was
     *     loaded; empty when a value is refused
     */
    Optional<EngineConfig> read(EngineConfig config);

    /**
     * Shows the problem of each value the engine's records refuse next to its field, as Save and Next do; problems
     * shown before are taken away first. What the owner typed but did not confirm is confirmed first, so a field
     * shows the value that is checked.
     *
     * @return true when a value is refused, so {@link #read(EngineConfig)} gives nothing
     */
    boolean showProblems();

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

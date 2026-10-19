package nl.enadeag.carchargeplanner.desktop.settings.page;

import javafx.scene.layout.Region;

/** What Settings shows for one category. */
public interface Page {

    /**
     * The category the page belongs to.
     *
     * @return its category
     */
    Category category();

    /**
     * The page itself.
     *
     * @return the same node every time
     */
    Region view();

    /**
     * Calls an action whenever a value on the page changes, by the owner or by loading.
     *
     * @param action what to run, on the JavaFX thread
     */
    void onChange(Runnable action);

    /**
     * Whether the owner changed something on the page since it was loaded.
     *
     * @return true when saving would change what is stored
     */
    boolean isChanged();
}

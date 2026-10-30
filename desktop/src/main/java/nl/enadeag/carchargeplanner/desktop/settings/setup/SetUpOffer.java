package nl.enadeag.carchargeplanner.desktop.settings.setup;

import nl.enadeag.carchargeplanner.desktop.settings.page.Category;
import nl.enadeag.carchargeplanner.engine.Outlook;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Leads the owner into set-up while the app is not set up (see "Not set up" in CONTEXT.md): it opens set-up the first
 * time a plan finds no config, once per run, and sends the ways into Settings to set-up instead until there is one.
 * A config that is there but cannot be read or is wrong is left to Settings and the dashboard's message, so set-up
 * never writes over a file the owner may want to fix.
 *
 * <p>Its methods may be called from any thread.
 */
public final class SetUpOffer {

    private final Runnable setUp;
    private final Consumer<Category> settings;
    private final boolean openFirst;
    private final AtomicBoolean opened = new AtomicBoolean();
    private volatile boolean missing;

    /**
     * Makes the offer; nothing is opened until {@link #follow(Outlook)}.
     *
     * @param setUp opens set-up; may be called from any thread
     * @param settings opens Settings on a category; may be called from any thread
     * @param openFirst true to open set-up by itself at the first plan without a config; false when the app was
     *     started hidden, as at login, and the tray and the dashboard only offer it
     */
    public SetUpOffer(Runnable setUp, Consumer<Category> settings, boolean openFirst) {
        this.setUp = setUp;
        this.settings = settings;
        this.openFirst = openFirst;
    }

    /**
     * Follows the engine's latest outlook: while it says the app is not set up, the ways into Settings lead to set-up,
     * and the first such outlook of the run opens set-up, unless the app started hidden. Shows nothing itself.
     *
     * @param outlook the engine's latest plan, or why there is none; never null
     */
    public void follow(Outlook outlook) {
        missing = outlook instanceof Outlook.NotSetUp;
        if (missing && openFirst && opened.compareAndSet(false, true)) {
            setUp.run();
        }
    }

    /**
     * Opens Settings on a category, or set-up while there is no config to set.
     *
     * @param category the category Settings should show
     */
    public void openSettingsOrSetUp(Category category) {
        if (missing) {
            setUp.run();
        } else {
            settings.accept(category);
        }
    }
}

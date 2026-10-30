package nl.enadeag.carchargeplanner.desktop.text;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The language the app speaks now, and who to tell when the owner chooses another. The composition root owns the one
 * instance, hands it to every part that shows texts, and changes it once the owner saved another language in Settings
 * or set-up.
 *
 * <p>A plain listener rather than a JavaFX property: the tray and the notifications run on threads of their own, and
 * a JavaFX property would tie them to JavaFX's thread. Each part moves the change to its own thread itself.
 */
public final class LanguageChoice {

    private final Language system;
    private final List<Consumer<Messages>> listeners = new CopyOnWriteArrayList<>();
    private volatile Messages messages;

    /**
     * Starts with the system's language, until the owner's choice is known.
     *
     * @param system the system's language, as {@link Language#ofSystem} finds it as the app starts; spoken while the
     *     owner follows the system; never null
     */
    public LanguageChoice(Language system) {
        if (system == null) {
            throw new IllegalArgumentException("the system's language must not be null");
        }
        this.system = system;
        this.messages = new Messages(system);
    }

    /**
     * The texts in the language spoken now. Read it each time texts are made, not once: it changes while the app runs.
     *
     * @return the messages of the language chosen last
     */
    public Messages messages() {
        return messages;
    }

    /**
     * The texts in a language the owner is choosing, such as on set-up's App step before it is saved; the language
     * spoken now does not change.
     *
     * @param chosen the language the owner is choosing; never null
     * @return the messages of that language
     */
    public Messages of(Language chosen) {
        var now = messages;
        return chosen == now.language() ? now : new Messages(chosen);
    }

    /**
     * The texts in the system's language, for an owner who is choosing to follow the system, as {@link #of(Language)}.
     *
     * @return the messages of the system's language
     */
    public Messages ofSystem() {
        return of(system);
    }

    /**
     * Switches to the owner's choice and tells every listener, on the calling thread, in the order they were added.
     * A choice of the language spoken already does nothing.
     *
     * @param chosen the language the owner chose; never null
     */
    public synchronized void choose(Language chosen) {
        if (chosen == messages.language()) {
            return;
        }
        messages = new Messages(chosen);
        var now = messages;
        listeners.forEach(listener -> listener.accept(now));
    }

    /**
     * Switches to the system's language, as the owner chose to follow the system, as {@link #choose(Language)}.
     */
    public void followSystem() {
        choose(system);
    }

    /**
     * Calls a listener at every switch to another language from now on; not for the language spoken now.
     *
     * @param listener gets the new language's messages, on the thread that chose it; it should hand slow work on and
     *     never block
     */
    public void onChange(Consumer<Messages> listener) {
        listeners.add(listener);
    }
}

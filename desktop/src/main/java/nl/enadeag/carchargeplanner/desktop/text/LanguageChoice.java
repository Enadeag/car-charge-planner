package nl.enadeag.carchargeplanner.desktop.text;

import nl.enadeag.carchargeplanner.desktop.system.ClockFormat;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The language the app speaks now, with the system's clock its times are written on, and who to tell when the owner
 * chooses another language or the system's clock changes. The composition root owns the one instance, hands it to
 * every part that shows texts, changes it once the owner saved another language in Settings or set-up, and follows
 * the system's clock into it.
 *
 * <p>A plain listener rather than a JavaFX property: the tray and the notifications run on threads of their own, and
 * a JavaFX property would tie them to JavaFX's thread. Each part moves the change to its own thread itself.
 */
public final class LanguageChoice {

    private final Language system;
    private final List<Consumer<Messages>> listeners = new CopyOnWriteArrayList<>();
    private volatile Messages messages;

    /**
     * Starts with the system's language on its own clock, until the owner's choice and the system's clock are known.
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
        return chosen == now.language() ? now : new Messages(chosen, now.clock());
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
        tell(new Messages(chosen, messages.clock()));
    }

    /**
     * Writes the times on the system's clock from now on and tells every listener, as {@link #choose(Language)}. The
     * clock written already does nothing.
     *
     * @param clock the system's 12- or 24-hour clock, or {@link ClockFormat#LANGUAGE} for the language's own; never
     *     null
     */
    public synchronized void useClock(ClockFormat clock) {
        if (clock == messages.clock()) {
            return;
        }
        tell(new Messages(messages.language(), clock));
    }

    private void tell(Messages now) {
        messages = now;
        listeners.forEach(listener -> listener.accept(now));
    }

    /**
     * Switches to the system's language, as the owner chose to follow the system, as {@link #choose(Language)}.
     */
    public void followSystem() {
        choose(system);
    }

    /**
     * Calls a listener at every switch to another language or clock from now on; not for the ones used now.
     *
     * @param listener gets the new messages, on the thread that chose it; it should hand slow work on and
     *     never block
     */
    public void onChange(Consumer<Messages> listener) {
        listeners.add(listener);
    }
}

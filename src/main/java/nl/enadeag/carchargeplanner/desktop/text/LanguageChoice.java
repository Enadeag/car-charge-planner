package nl.enadeag.carchargeplanner.desktop.text;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The language the app speaks now, and who to tell when the owner chooses another. The composition root owns the one
 * instance and hands it to every part that shows texts; Settings changes it after the owner saved another language.
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
     * @param system the system's language, as {@link Language#ofSystem} finds it as the app starts; spoken whenever
     *     the owner chose to follow the system; never null
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
     * @param chosen the language as the app settings would hold it; empty for the system's language
     * @return the messages of that language
     */
    public Messages of(Optional<Language> chosen) {
        var language = chosen.orElse(system);
        var now = messages;
        return language == now.language() ? now : new Messages(language);
    }

    /**
     * Switches to the owner's choice and tells every listener, on the calling thread, in the order they were added.
     * A choice that comes down to the language spoken already does nothing.
     *
     * @param chosen the language the owner chose, as the app settings hold it; empty to follow the system's language
     */
    public synchronized void choose(Optional<Language> chosen) {
        var language = chosen.orElse(system);
        if (language == messages.language()) {
            return;
        }
        messages = new Messages(language);
        var now = messages;
        listeners.forEach(listener -> listener.accept(now));
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

package nl.enadeag.carchargeplanner.desktop.dashboard.tray;

import nl.enadeag.carchargeplanner.desktop.system.Tray;
import nl.enadeag.carchargeplanner.desktop.text.LanguageChoice;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.Outlook;

/**
 * Keeps the tray's menu and tooltip in the language spoken now: it shows each outlook on the tray, and when the owner
 * chooses another language it gives the menu its new labels and shows the latest outlook again in that language.
 * Before the first set-up, the menu's Settings entry says that it sets the app up.
 *
 * <p>Its methods may be called from any thread; the tray is always told in the order they were called.
 */
public final class TrayTexts {

    private final Tray tray;
    private final LanguageChoice language;
    /** The latest outlook; null until the engine gives one. */
    private Outlook outlook;
    private boolean notSetUp;

    /**
     * Shows that the forecast is being fetched, and follows the language from now on.
     *
     * @param tray the tray whose menu and tooltip to keep up to date; its menu labels and status are set here
     * @param language the language the texts are in, followed while the app runs; never null
     */
    public TrayTexts(Tray tray, LanguageChoice language) {
        this.tray = tray;
        this.language = language;
        tray.showLabels(menuLabels(language.messages()));
        tray.showStatus(new TrayStatusMapper(language.messages()).loading());
        language.onChange(this::speak);
    }

    /**
     * Shows an outlook on the tray icon and in its tooltip.
     *
     * @param outlook the engine's latest plan, or why there is none; never null
     */
    public synchronized void show(Outlook outlook) {
        this.outlook = outlook;
        var missing = outlook instanceof Outlook.NotSetUp;
        if (missing != notSetUp) {
            notSetUp = missing;
            tray.showLabels(labels(language.messages()));
        }
        tray.showStatus(new TrayStatusMapper(language.messages()).map(outlook));
    }

    /** Before the first set-up, the Settings entry is the way into set-up, and says so. */
    private Tray.MenuLabels labels(Messages messages) {
        var labels = menuLabels(messages);
        return notSetUp ? new Tray.MenuLabels(labels.open(), messages.text(TextKey.MENU_SET_UP), labels.about(),
                labels.quit()) : labels;
    }

    /** "Open dashboard", "Settings…", "About…" and "Quit" in the menu's language. */
    private static Tray.MenuLabels menuLabels(Messages messages) {
        return new Tray.MenuLabels(messages.text(TextKey.OPEN_DASHBOARD), messages.text(TextKey.MENU_SETTINGS),
                messages.text(TextKey.MENU_ABOUT), messages.text(TextKey.QUIT));
    }

    private synchronized void speak(Messages messages) {
        tray.showLabels(labels(messages));
        var mapper = new TrayStatusMapper(messages);
        tray.showStatus(outlook == null ? mapper.loading() : mapper.map(outlook));
    }
}

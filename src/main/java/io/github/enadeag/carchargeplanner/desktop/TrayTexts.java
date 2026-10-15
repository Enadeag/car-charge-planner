package io.github.enadeag.carchargeplanner.desktop;

import io.github.enadeag.carchargeplanner.engine.Outlook;
import java.util.Optional;

/**
 * Keeps the tray's menu and tooltip in the language spoken now: it shows each outlook on the tray, and when the owner
 * chooses another language it gives the menu its new labels and shows the latest outlook again in that language.
 *
 * <p>Its methods may be called from any thread; the tray is always told in the order they were called.
 */
public final class TrayTexts {

    private final Tray tray;
    private final LanguageChoice language;
    private Optional<Outlook> outlook = Optional.empty();

    /**
     * Shows that the forecast is being fetched, and follows the language from now on.
     *
     * @param tray the tray whose menu and tooltip to keep up to date; its menu labels and status are set here
     * @param language the language the texts are in, followed while the app runs; never null
     */
    public TrayTexts(Tray tray, LanguageChoice language) {
        this.tray = tray;
        this.language = language;
        tray.showLabels(Tray.MenuLabels.of(language.messages()));
        tray.showStatus(new TrayStatusMapper(language.messages()).loading());
        language.onChange(this::speak);
    }

    /**
     * Shows an outlook on the tray icon and in its tooltip.
     *
     * @param outlook the engine's latest plan, or why there is none; never null
     */
    public synchronized void show(Outlook outlook) {
        this.outlook = Optional.of(outlook);
        tray.showStatus(new TrayStatusMapper(language.messages()).map(outlook));
    }

    private synchronized void speak(Messages messages) {
        tray.showLabels(Tray.MenuLabels.of(messages));
        var mapper = new TrayStatusMapper(messages);
        tray.showStatus(outlook.map(mapper::map).orElseGet(mapper::loading));
    }
}

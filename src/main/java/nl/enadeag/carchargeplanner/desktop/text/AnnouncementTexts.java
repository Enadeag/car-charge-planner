package nl.enadeag.carchargeplanner.desktop.text;

import nl.enadeag.carchargeplanner.desktop.system.NotificationText;
import nl.enadeag.carchargeplanner.engine.config.Phases;
import nl.enadeag.carchargeplanner.engine.notify.Announcement;

/**
 * An announcement from the engine in words: which window, and what to set in the charger's app. Also the test
 * notification, which shows the owner what an announcement looks like.
 */
public final class AnnouncementTexts {

    private final LanguageChoice language;

    /**
     * Makes the mapper; it keeps nothing between announcements.
     *
     * @param language the language the texts are in, read anew for each announcement; never null
     */
    public AnnouncementTexts(LanguageChoice language) {
        this.language = language;
    }

    /**
     * The notification for an announcement.
     *
     * @param announcement the window the engine announces, with its kind
     * @return a title with the window's times, and a body with the current, the power and the energy, in the language
     *     spoken now
     */
    public NotificationText notification(Announcement announcement) {
        var messages = language.messages();
        var window = announcement.window();
        var span = messages.span(window.start(), window.end(), announcement.zone());
        var power = messages.decimal(window.powerKw());
        var setting = messages.text(announcement.phases() == Phases.THREE ? TextKey.NOTIFY_SET_THREE_PHASES
                : TextKey.NOTIFY_SET_ONE_PHASE, window.amps(), power);
        var energy = messages.decimal(window.solarKwh() + window.importKwh());
        return switch (announcement.kind()) {
            case SOLAR_WINDOW -> new NotificationText(messages.text(TextKey.NOTIFY_WINDOW_TITLE, span), setting + " "
                    + messages.text(TextKey.NOTIFY_WINDOW_BODY, energy, messages.whole(window.solarShare() * 100)));
            case GRID_CHARGE -> new NotificationText(messages.text(TextKey.NOTIFY_GRID_TITLE, span), setting + " "
                    + messages.text(TextKey.NOTIFY_GRID_BODY, energy, messages.whole(announcement.minimumPct().value())));
        };
    }

    /**
     * The test notification, which the command line asks for: it names no window, so nothing in it can be taken for
     * a charge window to set the charger for.
     *
     * @param appName the app's name, which the body names
     * @return a title that says it is a test, and a body that says what an announcement is for and what a click does,
     *     in the language spoken now
     */
    public NotificationText testNotification(String appName) {
        var messages = language.messages();
        return new NotificationText(messages.text(TextKey.NOTIFY_TEST_TITLE),
                messages.text(TextKey.NOTIFY_TEST_BODY, appName));
    }
}

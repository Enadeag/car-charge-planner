package io.github.enadeag.carchargeplanner.desktop.text;

import io.github.enadeag.carchargeplanner.desktop.system.NotificationText;
import io.github.enadeag.carchargeplanner.engine.config.Phases;
import io.github.enadeag.carchargeplanner.engine.notify.Announcement;

/** An announcement from the engine in words: which window, and what to set in the charger's app. */
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
            case CHARGE_WINDOW -> new NotificationText(messages.text(TextKey.NOTIFY_WINDOW_TITLE, span), setting + " "
                    + messages.text(TextKey.NOTIFY_WINDOW_BODY, energy, messages.whole(window.solarShare() * 100)));
            case GRID_CHARGE -> new NotificationText(messages.text(TextKey.NOTIFY_GRID_TITLE, span), setting + " "
                    + messages.text(TextKey.NOTIFY_GRID_BODY, energy, messages.whole(announcement.minimumPct().value())));
        };
    }
}

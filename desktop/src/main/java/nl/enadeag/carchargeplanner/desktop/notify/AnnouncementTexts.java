package nl.enadeag.carchargeplanner.desktop.notify;

import nl.enadeag.carchargeplanner.desktop.app.Units;
import nl.enadeag.carchargeplanner.desktop.system.NotificationTarget;
import nl.enadeag.carchargeplanner.desktop.system.NotificationText;
import nl.enadeag.carchargeplanner.desktop.text.LanguageChoice;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.config.Phases;
import nl.enadeag.carchargeplanner.engine.notify.Announcement;
import nl.enadeag.carchargeplanner.engine.notify.BatteryCheck;
import nl.enadeag.carchargeplanner.engine.notify.Notice;
import nl.enadeag.carchargeplanner.engine.notify.TripChargeStart;
import nl.enadeag.carchargeplanner.engine.notify.TripShort;
import nl.enadeag.carchargeplanner.engine.plan.Window;
import nl.enadeag.carchargeplanner.engine.trip.Trip;
import java.time.ZoneId;
import java.util.function.Supplier;

/**
 * What the engine tells the owner, in words: which window, and what to set in the charger's app; and what a trip asks
 * of the owner. Also the test notification, which shows the owner what an announcement looks like. A trip is named by
 * the weekday and time of its departure, as a notification may be read later than it came.
 */
public final class AnnouncementTexts {

    private final LanguageChoice language;
    private final Supplier<Units> units;

    /**
     * Makes the mapper; it keeps nothing between notices.
     *
     * @param language the language the texts are in, read anew for each notice; never null
     * @param units the units distances are told in, asked anew for each notice; never null
     */
    public AnnouncementTexts(LanguageChoice language, Supplier<Units> units) {
        this.language = language;
        this.units = units;
    }

    /**
     * The notification for a notice of the engine.
     *
     * @param notice the window the engine announces, or what it tells about a trip
     * @return in the language spoken now: for a window, a title with its times and a body with the current, the power
     *     and the energy, and for a grid charge the charge limit; for a trip's grid charge, when to start, the trip and
     *     the charge limit, then the current and the power; for the battery check, the trip and the level the plan
     *     assumes, opening the battery level's entry when clicked; for a trip that is short, the trip and the
     *     distance
     */
    public NotificationText notification(Notice notice) {
        var messages = language.messages();
        return switch (notice) {
            case Announcement announcement -> announcement(announcement, messages);
            case TripChargeStart start -> chargeStart(start, messages);
            case BatteryCheck check -> new NotificationText(messages.text(TextKey.NOTIFY_LEVEL_TITLE),
                    messages.text(TextKey.NOTIFY_LEVEL_BODY, weekday(check.trip(), check.zone(), messages),
                            messages.time(check.trip().departure(), check.zone()),
                            messages.whole(check.levelPct().value())),
                    NotificationTarget.BATTERY_LEVEL);
            case TripShort tripShort -> shortTrip(tripShort, messages);
        };
    }

    private static NotificationText announcement(Announcement announcement, Messages messages) {
        var window = announcement.window();
        var span = messages.span(window.start(), window.end(), announcement.zone());
        var setting = setting(window, announcement.phases(), messages);
        var energy = messages.decimal(window.solarKwh() + window.importKwh());
        return switch (announcement.kind()) {
            case SOLAR_WINDOW -> new NotificationText(messages.text(TextKey.NOTIFY_WINDOW_TITLE, span), setting + " "
                    + messages.text(TextKey.NOTIFY_WINDOW_BODY, energy, messages.whole(window.solarShare() * 100)));
            case GRID_CHARGE -> new NotificationText(messages.text(TextKey.NOTIFY_GRID_TITLE, span), setting + " "
                    + messages.text(TextKey.NOTIFY_GRID_BODY, energy, messages.whole(announcement.minimumPct().value()),
                    messages.whole(announcement.chargeLimitPct().orElseThrow().value())));
        };
    }

    private static NotificationText chargeStart(TripChargeStart start, Messages messages) {
        var window = start.window();
        var zone = start.zone();
        var day = weekday(start.trip(), zone, messages);
        var departure = messages.time(start.trip().departure(), zone);
        var limit = messages.whole(start.chargeLimitPct().value());
        var setting = setting(window, start.phases(), messages);
        return switch (start.start()) {
            case PLANNED -> new NotificationText(
                    messages.text(TextKey.NOTIFY_GRID_TITLE, messages.span(window.start(), window.end(), zone)),
                    messages.text(TextKey.NOTIFY_TRIP_START_BODY, messages.time(window.start(), zone), day, departure,
                            limit) + " " + setting);
            case NOW -> new NotificationText(messages.text(TextKey.NOTIFY_TRIP_NOW_TITLE),
                    messages.text(TextKey.NOTIFY_TRIP_NOW_BODY, day, departure, limit) + " " + setting);
            case NOW_OUTSIDE_CHARGING_HOURS -> new NotificationText(messages.text(TextKey.NOTIFY_TRIP_NOW_TITLE),
                    messages.text(TextKey.NOTIFY_TRIP_NOW_OUTSIDE_BODY, day, departure, limit) + " " + setting);
        };
    }

    private NotificationText shortTrip(TripShort tripShort, Messages messages) {
        var chosen = units.get();
        return new NotificationText(messages.text(TextKey.NOTIFY_SHORT_TITLE),
                messages.text(TextKey.NOTIFY_SHORT_BODY, weekday(tripShort.trip(), tripShort.zone(), messages),
                        messages.time(tripShort.trip().departure(), tripShort.zone()),
                        messages.whole(Math.ceil(chosen.distance(tripShort.kmShort()))), chosen.distanceUnit()));
    }

    /** What to set in the charger's app: the current, per phase on three phases, and the power. */
    private static String setting(Window window, Phases phases, Messages messages) {
        return messages.text(phases == Phases.THREE ? TextKey.NOTIFY_SET_THREE_PHASES : TextKey.NOTIFY_SET_ONE_PHASE,
                window.amps(), messages.decimal(window.powerKw()));
    }

    private static String weekday(Trip trip, ZoneId zone, Messages messages) {
        return messages.weekday(trip.departure().atZone(zone).toLocalDate());
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

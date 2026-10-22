package nl.enadeag.carchargeplanner.desktop.settings.form;

import nl.enadeag.carchargeplanner.desktop.app.AppSettings;
import nl.enadeag.carchargeplanner.engine.calendar.CalendarSecrets;
import nl.enadeag.carchargeplanner.engine.calendar.Consent;
import nl.enadeag.carchargeplanner.engine.config.CalendarSettings;
import nl.enadeag.carchargeplanner.engine.config.EngineConfig;
import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import nl.enadeag.carchargeplanner.engine.secrets.KeyChanges;
import nl.enadeag.carchargeplanner.engine.secrets.Secrets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.Set;

/**
 * What to write; each part only when it is present.
 *
 * @param startAtLogin whether the app should start at login; present when the owner changed it
 * @param consents the calendar's consents that are given; present when the owner gave or withdrew one
 * @param keys what the owner entered and emptied in the API keys, applied to the stored ones; present when they
 *     changed
 * @param config the engine's whole config; present when it changed, or was never saved
 * @param app the app's own settings; present when they changed
 */
public record SettingsChanges(Optional<Boolean> startAtLogin, Optional<Set<Consent>> consents,
        Optional<KeyChanges> keys, Optional<EngineConfig> config, Optional<AppSettings> app) {

    /**
     * What differs between what is stored and what is entered. Start at login counts only where this copy of the app
     * can start at login, and the API keys and the consents only with a config entered: without one, no provider's
     * fields and no consents were shown. A consent that is withdrawn wipes what the calendar brought in, whatever the
     * page shows: the iCal link, also one that could not be read, and the {@code .ics} file.
     *
     * @param stored what the stores hold
     * @param entered what the owner entered
     * @return the parts of {@code entered} to write
     */
    public static SettingsChanges between(AllSettings stored, AllSettings entered) {
        var startAtLogin = stored.startsAtLogin()
                .flatMap(was -> entered.startsAtLogin().filter(now -> !now.equals(was)));
        var consents = entered.config().map(_ -> entered.consents())
                .filter(changed -> !changed.equals(stored.consents()));
        var withdrawn = consents.filter(changed -> !changed.containsAll(stored.consents())).isPresent();
        var keys = entered.config().map(_ -> KeyChanges.between(stored.keys(), entered.keys()))
                .map(changes -> withdrawn ? withoutLink(changes) : changes)
                .filter(changed -> !changed.isEmpty());
        var config = entered.config()
                .map(chosen -> withdrawn ? chosen.withCalendar(withoutFile(chosen.calendar())) : chosen)
                .filter(changed -> !Optional.of(changed).equals(stored.config()));
        var app = Optional.of(entered.app()).filter(changed -> !changed.equals(stored.app()));
        return new SettingsChanges(startAtLogin, consents, keys, config, app);
    }

    /** The changes, with the iCal link removed instead of entered. */
    private static KeyChanges withoutLink(KeyChanges changes) {
        var entered = new LinkedHashMap<>(changes.entered().byProvider());
        var calendar = new LinkedHashMap<>(changes.entered().of(CalendarSecrets.CALENDAR));
        calendar.remove(CalendarSecrets.ICAL_LINK);
        entered.put(CalendarSecrets.CALENDAR, calendar);
        var removed = new LinkedHashMap<ProviderId, Set<String>>(changes.removed());
        var names = new HashSet<>(removed.getOrDefault(CalendarSecrets.CALENDAR, Set.of()));
        names.add(CalendarSecrets.ICAL_LINK);
        removed.put(CalendarSecrets.CALENDAR, names);
        return new KeyChanges(new Secrets(entered), removed);
    }

    private static CalendarSettings withoutFile(CalendarSettings settings) {
        return new CalendarSettings(Optional.empty(), settings.travelMargin(), settings.allDayDeparture(),
                settings.allDayReturn());
    }
}

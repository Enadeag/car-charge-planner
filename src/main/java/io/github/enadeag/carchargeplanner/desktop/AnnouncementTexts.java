package io.github.enadeag.carchargeplanner.desktop;

import static io.github.enadeag.carchargeplanner.desktop.Format.text;

import io.github.enadeag.carchargeplanner.engine.config.Phases;
import io.github.enadeag.carchargeplanner.engine.notify.Announcement;

/** An announcement from the engine in words: which window, and what to set in the charger's app. */
public final class AnnouncementTexts {

    /** Makes the mapper; it holds nothing. */
    public AnnouncementTexts() {
    }

    /**
     * The notification for an announcement.
     *
     * @param announcement the window the engine announces, with its kind
     * @return a title with the window's times, and a body with the current, the power and the energy
     */
    public NotificationText notification(Announcement announcement) {
        var window = announcement.window();
        var zone = announcement.zone();
        var span = Format.time(window.start(), zone) + " – " + Format.time(window.end(), zone);
        var setting = announcement.phases() == Phases.THREE
                ? text("Set the current to %d A per phase (%.1f kW) in your charger's app.", window.amps(), window.powerKw())
                : text("Set the current to %d A (%.1f kW) in your charger's app.", window.amps(), window.powerKw());
        double energyKwh = window.solarKwh() + window.importKwh();
        return switch (announcement.kind()) {
            case CHARGE_WINDOW -> new NotificationText("Charge window " + span,
                    setting + text(" About %.1f kWh, %d %% solar.", energyKwh, Math.round(window.solarShare() * 100)));
            case GRID_CHARGE -> new NotificationText("Grid charge window " + span,
                    setting + text(" About %.1f kWh, needed to stay above %.0f %%.", energyKwh, announcement.minimumPct().value()));
        };
    }
}

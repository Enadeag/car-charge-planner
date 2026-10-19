package nl.enadeag.carchargeplanner.desktop.dashboard.tray;

import nl.enadeag.carchargeplanner.desktop.dashboard.advice.GridAdviceMapper;
import nl.enadeag.carchargeplanner.desktop.dashboard.outlook.OutlookMapper;
import nl.enadeag.carchargeplanner.desktop.system.StatusDot;
import nl.enadeag.carchargeplanner.desktop.system.TrayStatus;
import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.Outlook;
import nl.enadeag.carchargeplanner.engine.plan.Window;
import java.time.LocalDate;

/**
 * Turns the engine's outlook into the tray icon's dot and tooltip. What needs the owner most comes first: no plan,
 * then grid advice (the car would drop below its minimum), then a charge window on sun today.
 */
final class TrayStatusMapper {

    private final Messages messages;

    /**
     * Makes the mapper; it keeps nothing between outlooks.
     *
     * @param messages the texts, in the owner's language
     */
    TrayStatusMapper(Messages messages) {
        this.messages = messages;
    }

    /**
     * What the tray shows before the first plan.
     *
     * @return no dot, and that the forecast is being fetched
     */
    TrayStatus loading() {
        return new TrayStatus(StatusDot.NONE, messages.text(TextKey.LOADING));
    }

    /**
     * The outlook as a dot and a sentence.
     *
     * @param outlook the engine's plan, or why there is none; never null
     * @return the dot that asks the most of the owner, with the same in words
     */
    TrayStatus map(Outlook outlook) {
        return switch (outlook) {
            case Outlook.NotSetUp _ ->
                    new TrayStatus(StatusDot.ACTION, messages.text(TextKey.TRAY_NOT_SET_UP_FIRST));
            case Outlook.ConfigUnusable unusable -> new TrayStatus(StatusDot.ACTION,
                    messages.text(TextKey.TRAY_NOT_SET_UP, OutlookMapper.whatToFix(unusable, messages)));
            case Outlook.NoForecast none when none.keychainLocked() -> new TrayStatus(StatusDot.ACTION,
                    messages.text(TextKey.TRAY_NO_FORECAST, messages.text(TextKey.NO_FORECAST_KEYRING_LOCKED)));
            case Outlook.NoForecast none ->
                    new TrayStatus(StatusDot.ACTION, messages.text(TextKey.TRAY_NO_FORECAST, none.reason()));
            case Outlook.Planned planned when planned.days().isEmpty() ->
                    new TrayStatus(StatusDot.ACTION, messages.text(TextKey.TRAY_NO_DAYS));
            case Outlook.Planned planned -> plan(planned);
        };
    }

    private TrayStatus plan(Outlook.Planned planned) {
        var advice = planned.gridAdvice().map(new GridAdviceMapper(planned, messages)::map);
        if (advice.isPresent()) {
            return new TrayStatus(StatusDot.GRID, advice.get().title());
        }
        var today = planned.madeAt().atZone(planned.zone()).toLocalDate();
        // the next window has not ended, so one that started before today is running now
        return planned.nextWindow()
                .map(window -> day(window, planned).isAfter(today)
                        ? new TrayStatus(StatusDot.NONE, laterDay(window, planned, today))
                        : new TrayStatus(StatusDot.SUN, sun(window, planned)))
                .orElseGet(() -> new TrayStatus(StatusDot.NONE, messages.text(TextKey.TRAY_NOTHING)));
    }

    private String sun(Window window, Outlook.Planned planned) {
        var zone = planned.zone();
        if (window.start().isAfter(planned.madeAt())) {
            return messages.text(TextKey.TRAY_SUN_LATER, messages.time(window.start(), zone),
                    messages.time(window.end(), zone), window.amps());
        }
        return messages.text(TextKey.TRAY_SUN_NOW, messages.time(window.end(), zone), window.amps());
    }

    /** Nothing today: the next window is on a later day. */
    private String laterDay(Window window, Outlook.Planned planned, LocalDate today) {
        var zone = planned.zone();
        return messages.text(TextKey.TRAY_NEXT, messages.inSentence(day(window, planned), today),
                messages.time(window.start(), zone), messages.time(window.end(), zone));
    }

    private static LocalDate day(Window window, Outlook.Planned planned) {
        return window.start().atZone(planned.zone()).toLocalDate();
    }
}

package nl.enadeag.carchargeplanner.desktop.settings.form;

import nl.enadeag.carchargeplanner.desktop.text.Messages;
import nl.enadeag.carchargeplanner.desktop.text.TextKey;
import nl.enadeag.carchargeplanner.engine.calendar.CalendarRead;

/** What the Calendar page says about a reading of the calendar. */
public final class CalendarReadText {

    private CalendarReadText() {
    }

    /**
     * The words for how a reading went.
     *
     * @param read how it went, as the engine says
     * @param messages the texts, in the owner's language
     * @return how many events were read, why the calendar could not be read, with the engine's words in English, or
     *     why it was not read
     */
    public static String of(CalendarRead read, Messages messages) {
        return switch (read) {
            case CalendarRead.Read done -> messages.text(TextKey.CALENDAR_READ, done.events(), done.unreadable());
            case CalendarRead.Failed failed -> messages.text(TextKey.CALENDAR_READ_FAILED, failed.detail());
            case CalendarRead.NotRead notRead -> messages.text(switch (notRead.reason()) {
                case NO_CONSENT -> TextKey.CALENDAR_NO_CONSENT;
                case NO_SOURCE -> TextKey.CALENDAR_NO_SOURCE;
                case NO_PLAN -> TextKey.CALENDAR_NO_PLAN;
            });
        };
    }
}

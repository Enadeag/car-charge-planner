package nl.enadeag.carchargeplanner.desktop.settings.form;

/**
 * How far the owner got on the Calendar page, as it shows now, and so which of its steps are open.
 *
 * @param readConsent whether the box to read the calendar is ticked
 * @param sendConsent whether the box to send the locations is ticked
 * @param hasSource whether an iCal link or an {@code .ics} file is there, also a link that is stored but cannot be
 *     read now, as the keychain is locked
 * @param hasApiKey whether the route service's API key is there, also one that is stored but cannot be read now
 */
public record CalendarSteps(boolean readConsent, boolean sendConsent, boolean hasSource, boolean hasApiKey) {

    /**
     * Whether a step can be filled in: the consents always, every later step once the one before it is done.
     *
     * @param step the step
     * @return true when its fields can be edited
     */
    public boolean isOpen(CalendarStep step) {
        return switch (step) {
            case CONSENTS -> true;
            case SOURCE -> readConsent && sendConsent;
            case API_KEY -> isOpen(CalendarStep.SOURCE) && hasSource;
            case SETTINGS -> isOpen(CalendarStep.API_KEY) && hasApiKey;
        };
    }
}

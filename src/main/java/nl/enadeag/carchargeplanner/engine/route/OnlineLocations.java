package nl.enadeag.carchargeplanner.engine.route;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Tells, on this computer, a location that is no physical place: a link, an online meeting or a call. Such a location
 * is never sent to a route service. This is the one list of what counts as online.
 *
 * <p>A location that holds any of these is online as a whole, also when it names a room beside it, such as Outlook's
 * "Room 3; Microsoft Teams Meeting": nothing of it is sent.
 */
public final class OnlineLocations {

    /** A web address or another link: a scheme such as https: or mailto:, or an address that starts with www. */
    private static final Pattern LINK = Pattern.compile(
            "\\b(?:[a-z][a-z0-9+.-]*://|www\\.|(?:mailto|tel|sip|callto|skype|msteams|zoommtg):)");

    /** Services and words for meetings without a place, in English and Dutch, as whole words. */
    private static final List<String> WORDS = List.of(
            "teams", "zoom", "meet", "google meet", "skype", "webex", "whereby", "jitsi", "gotomeeting", "goto meeting",
            "online", "virtual", "virtueel", "video call", "videocall", "videobellen", "videogesprek", "phone",
            "telephone", "telefonisch", "conference call", "call");

    private static final Pattern WORD = Pattern.compile("\\b(?:" + String.join("|", WORDS.stream()
            .map(word -> word.replace(" ", "\\s+")).toList()) + ")\\b", Pattern.UNICODE_CHARACTER_CLASS);

    private OnlineLocations() {
    }

    /**
     * Whether a location text is no physical place and must not be sent.
     *
     * @param text the event's location text; may be blank, which names nothing online
     * @return true when it holds a link, or names an online meeting or a call
     */
    public static boolean isOnline(String text) {
        var lower = text.toLowerCase(Locale.ROOT);
        return LINK.matcher(lower).find() || WORD.matcher(lower).find();
    }
}

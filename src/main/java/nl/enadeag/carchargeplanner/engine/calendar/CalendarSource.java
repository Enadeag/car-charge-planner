package nl.enadeag.carchargeplanner.engine.calendar;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/** Where the calendar is read from: its secret iCal address, or an {@code .ics} file on this computer. */
public sealed interface CalendarSource {

    /**
     * The calendar's secret iCal address, which gives whoever has it the whole calendar. It never shows in a text the
     * app makes, the log included: {@link #toString()} leaves it out.
     *
     * @param address the address, over https with a host
     */
    record IcalLink(URI address) implements CalendarSource {

        /**
         * Checks the address, without naming it.
         *
         * @throws IllegalArgumentException when the address is not https or has no host
         */
        public IcalLink {
            if (!"https".equals(address.getScheme()) || address.getHost() == null) {
                throw new IllegalArgumentException("an iCal link must be an https address with a host");
            }
        }

        /**
         * Reads an address as the owner typed it or the calendar gave it: https, or webcal, which calendars use for
         * the same address over https.
         *
         * @param text the address; white space around it is ignored
         * @return the address over https; empty when it is not an https or webcal address with a host
         */
        public static Optional<IcalLink> parse(String text) {
            var trimmed = text.strip();
            if (trimmed.chars().anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c))) {
                return Optional.empty();
            }
            try {
                var uri = new URI(trimmed);
                var scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
                if (uri.isOpaque() || uri.getHost() == null || !(scheme.equals("https") || scheme.equals("webcal"))) {
                    return Optional.empty();
                }
                return Optional.of(new IcalLink(new URI("https" + trimmed.substring(uri.getScheme().length()))));
            } catch (URISyntaxException _) {
                return Optional.empty();
            }
        }

        /**
         * Says what this is without the address.
         *
         * @return a text that never holds the address
         */
        @Override
        public String toString() {
            return "IcalLink[address hidden]";
        }
    }

    /**
     * An {@code .ics} file on this computer.
     *
     * @param path the file, by its whole path
     */
    record IcsFile(Path path) implements CalendarSource {

        /**
         * Checks the path.
         *
         * @throws IllegalArgumentException when the path is not a whole path
         */
        public IcsFile {
            if (!path.isAbsolute()) {
                throw new IllegalArgumentException("an .ics file needs a whole path, not " + path);
            }
        }
    }
}

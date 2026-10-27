package nl.enadeag.carchargeplanner.engine.calendar;

import nl.enadeag.carchargeplanner.engine.forecast.ByteSize;
import nl.enadeag.carchargeplanner.engine.forecast.HttpGet;
import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.time.Duration;
import java.util.Map;

/**
 * Fetches the calendar's text from its iCal address or its {@code .ics} file, no larger than {@link #MAX_SIZE}; the
 * address is fetched over https only, within {@link #TIMEOUT}. What it throws never holds the address.
 */
public final class CalendarFetcher {

    /** How long the calendar's server may take to send the whole calendar. */
    public static final Duration TIMEOUT = Duration.ofSeconds(30);

    /**
     * The largest calendar read. A calendar holds every past event too, and some years of a busy one can reach a few
     * megabytes.
     */
    public static final ByteSize MAX_SIZE = ByteSize.ofMegabytes(10);

    private final HttpGet http;

    /**
     * Makes the fetcher.
     *
     * @param http how the iCal address is fetched
     */
    public CalendarFetcher(HttpGet http) {
        this.http = http;
    }

    /**
     * Fetches the calendar's text.
     *
     * @param source where the calendar is
     * @return the calendar's text, as UTF-8
     * @throws CalendarProblem when the server could not be reached, did not send it in time, sent something other
     *     than a 2xx answer or more than {@link #MAX_SIZE}, or the file is missing, unreadable, too large or not
     *     UTF-8; the message never holds the address
     */
    public String fetch(CalendarSource source) throws CalendarProblem {
        return switch (source) {
            case CalendarSource.IcalLink link -> download(link);
            case CalendarSource.IcsFile file -> read(file);
        };
    }

    private String download(CalendarSource.IcalLink link) throws CalendarProblem {
        var request = new HttpGet.Request(link.address(), Map.of("Accept", "text/calendar"), TIMEOUT, MAX_SIZE);
        HttpGet.Reply reply;
        try {
            reply = http.get(request);
        } catch (HttpGet.TooLarge _) {
            throw tooLarge();
        } catch (IOException e) {
            // an IOException's message may hold the address, which gives whoever has it the whole calendar
            throw new CalendarProblem(CalendarProblem.Kind.UNREACHABLE,
                    "could not reach the calendar's server (" + e.getClass().getSimpleName() + ")");
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            throw new CalendarProblem(CalendarProblem.Kind.UNREACHABLE, "reading the calendar was interrupted");
        }
        if (!reply.ok()) {
            throw new CalendarProblem(CalendarProblem.Kind.REFUSED,
                    "the calendar's server answered with HTTP status " + reply.status());
        }
        return reply.body();
    }

    private static String read(CalendarSource.IcsFile file) throws CalendarProblem {
        try {
            if (Files.size(file.path()) > MAX_SIZE.bytes()) {
                throw tooLarge();
            }
            return Files.readString(file.path(), StandardCharsets.UTF_8);
        } catch (NoSuchFileException _) {
            throw new CalendarProblem(CalendarProblem.Kind.FILE_UNREADABLE, "there is no file " + file.path());
        } catch (CharacterCodingException _) {
            throw new CalendarProblem(CalendarProblem.Kind.NOT_A_CALENDAR, file.path() + " is not UTF-8 text");
        } catch (IOException e) {
            throw new CalendarProblem(CalendarProblem.Kind.FILE_UNREADABLE,
                    "could not read " + file.path() + " (" + e.getClass().getSimpleName() + ")");
        }
    }

    private static CalendarProblem tooLarge() {
        return new CalendarProblem(CalendarProblem.Kind.TOO_LARGE,
                "the calendar is larger than " + MAX_SIZE.bytes() + " bytes");
    }
}

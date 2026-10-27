package nl.enadeag.carchargeplanner.engine.calendar;

import nl.enadeag.carchargeplanner.engine.config.CalendarSettings;
import java.io.IOException;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The calendar's events on the days the plan covers, held in memory only, and when the calendar is read again: with a
 * new plan once {@link #EVERY} has passed since the last reading, at once when the {@code .ics} file changed or the
 * days changed, when {@linkplain #readSoon() asked} after a change to the source, and on {@link #readNow}. Nothing is
 * read while the consents do not both count, and then nothing is kept. A calendar that cannot be read keeps the events
 * read before from the same source; that it cannot be read, and that it can be read again, is logged once each,
 * without the iCal address.
 *
 * <p>Its methods may be called from any thread; one reading at a time.
 */
public final class CalendarFeed {

    /** How often the calendar is read with a new plan, at most. */
    public static final Duration EVERY = Duration.ofMinutes(15);

    private static final System.Logger LOG = System.getLogger(CalendarFeed.class.getName());

    private final CalendarFetcher fetcher;
    private final IcsReader reader;
    private List<CalendarEvent> events = List.of();
    private CalendarRead last = new CalendarRead.NotRead(CalendarRead.Reason.NO_PLAN);
    /** Where the events came from; null while there are none. */
    private CalendarSource readFrom;
    /** The days of the last reading; null before the first. */
    private CoveredDays readFor;
    /** When the calendar was last read or tried; null before the first time. */
    private Instant readAt;
    /** The file as it was when it was last read; null for a link, or a file that was not there. */
    private Stamp stamp;
    private boolean asked;
    /** Whether the last reading worked, for logging a change; null before the first or after nothing was read. */
    private Boolean working;

    /**
     * Makes the feed, without events until the first reading.
     *
     * @param fetcher fetches the calendar's text from its address or its file
     * @param reader reads the events from the text
     */
    public CalendarFeed(CalendarFetcher fetcher, IcsReader reader) {
        this.fetcher = fetcher;
        this.reader = reader;
    }

    /** Finds the calendar's iCal address, which is kept with the API keys. */
    @FunctionalInterface
    public interface LinkLookup {

        /**
         * The stored iCal address. May ask the keychain, so it is only called when the calendar is read.
         *
         * @return the address; empty when none is stored
         * @throws CalendarProblem when the address cannot be read, or is not an https or webcal address
         */
        Optional<CalendarSource.IcalLink> link() throws CalendarProblem;
    }

    /**
     * Reads the calendar when that is due, after a new plan.
     *
     * @param consents the consents as stored; when they do not both count, nothing is read and the events are dropped
     * @param settings the calendar's settings, with the {@code .ics} file, which goes before an iCal address
     * @param link finds the iCal address when there is no file
     * @param days the days the new plan covers
     * @param now the time of the plan
     * @return how the reading went, or the last one when none was due
     */
    public synchronized CalendarRead refresh(Consents consents, CalendarSettings settings, LinkLookup link,
            CoveredDays days, Instant now) {
        if (!consents.allCount()) {
            return dropped(CalendarRead.Reason.NO_CONSENT);
        }
        return isDue(settings, days, now) ? read(settings, link, days, now) : last;
    }

    /**
     * Reads the calendar now, as the owner asked, whenever it was read last.
     *
     * @param consents the consents as stored; when they do not both count, nothing is read and the events are dropped
     * @param settings the calendar's settings, with the {@code .ics} file, which goes before an iCal address
     * @param link finds the iCal address when there is no file
     * @param days the days the plan covers
     * @param now the time it is read at
     * @return how the reading went
     */
    public synchronized CalendarRead readNow(Consents consents, CalendarSettings settings, LinkLookup link,
            CoveredDays days, Instant now) {
        if (!consents.allCount()) {
            return dropped(CalendarRead.Reason.NO_CONSENT);
        }
        return read(settings, link, days, now);
    }

    /**
     * There is no plan, so no days to read the calendar for: the events are kept while the consents both count.
     *
     * @param consents the consents as stored; when they do not both count, the events are dropped
     * @return that there was no reading, and why
     */
    public synchronized CalendarRead withoutPlan(Consents consents) {
        if (!consents.allCount()) {
            return dropped(CalendarRead.Reason.NO_CONSENT);
        }
        return new CalendarRead.NotRead(CalendarRead.Reason.NO_PLAN);
    }

    /** Has the calendar read again with the next plan, as its source or the consents may have changed. */
    public synchronized void readSoon() {
        asked = true;
    }

    /**
     * The events of the last reading that worked.
     *
     * @return the occurrences on the days the plan covered then, in order of their start; empty before the first
     *     reading, and while the consents do not both count
     */
    public synchronized List<CalendarEvent> events() {
        return events;
    }

    /**
     * How the last reading went.
     *
     * @return the last outcome; not read for lack of a plan before the first
     */
    public synchronized CalendarRead last() {
        return last;
    }

    private boolean isDue(CalendarSettings settings, CoveredDays days, Instant now) {
        if (readAt == null || asked || !days.equals(readFor) || !now.isBefore(readAt.plus(EVERY))) {
            return true;
        }
        return settings.icsFile().isPresent() && !Objects.equals(stamp, Stamp.of(settings.icsFile().get()));
    }

    private CalendarRead read(CalendarSettings settings, LinkLookup link, CoveredDays days, Instant now) {
        asked = false;
        readAt = now;
        readFor = days;
        CalendarSource source;
        try {
            var found = find(settings, link);
            if (found.isEmpty()) {
                drop();
                return keep(new CalendarRead.NotRead(CalendarRead.Reason.NO_SOURCE));
            }
            source = found.get();
        } catch (CalendarProblem e) {
            return failed(e, now);
        }
        // the stamp before the reading, so a change while it is read is read again
        stamp = source instanceof CalendarSource.IcsFile file ? Stamp.of(file.path()) : null;
        try {
            var read = reader.read(fetcher.fetch(source), days);
            events = read.events();
            readFrom = source;
            if (!Boolean.TRUE.equals(working)) {
                LOG.log(Level.INFO, working == null ? "the calendar is read" : "the calendar is read again");
            }
            working = true;
            return keep(new CalendarRead.Read(now, read.events().size(), read.unreadable()));
        } catch (CalendarProblem e) {
            if (!source.equals(readFrom)) {
                events = List.of();
                readFrom = null;
            }
            return failed(e, now);
        }
    }

    private static Optional<CalendarSource> find(CalendarSettings settings, LinkLookup link) throws CalendarProblem {
        if (settings.icsFile().isPresent()) {
            return Optional.of(new CalendarSource.IcsFile(settings.icsFile().get()));
        }
        return link.link().map(CalendarSource.class::cast);
    }

    private CalendarRead failed(CalendarProblem problem, Instant now) {
        if (!Boolean.FALSE.equals(working)) {
            LOG.log(Level.WARNING, "the calendar could not be read: " + problem.getMessage());
        }
        working = false;
        return keep(new CalendarRead.Failed(now, problem.kind(), problem.getMessage()));
    }

    private CalendarRead dropped(CalendarRead.Reason reason) {
        drop();
        readAt = null;
        readFor = null;
        return keep(new CalendarRead.NotRead(reason));
    }

    private void drop() {
        events = List.of();
        readFrom = null;
        stamp = null;
        working = null;
    }

    private CalendarRead keep(CalendarRead outcome) {
        last = outcome;
        return outcome;
    }

    /** What tells that a file changed: when it was last written, and its size. */
    private record Stamp(FileTime modified, long size) {

        /** The file's stamp now; null when it cannot be read, such as when it is not there. */
        static Stamp of(Path file) {
            try {
                var attributes = Files.readAttributes(file, BasicFileAttributes.class);
                return new Stamp(attributes.lastModifiedTime(), attributes.size());
            } catch (IOException _) {
                return null;
            }
        }
    }
}

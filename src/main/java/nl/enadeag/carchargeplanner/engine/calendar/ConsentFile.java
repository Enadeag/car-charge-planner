package nl.enadeag.carchargeplanner.engine.calendar;

import nl.enadeag.carchargeplanner.engine.store.FileFormat;
import nl.enadeag.carchargeplanner.engine.store.FileVersion;
import nl.enadeag.carchargeplanner.engine.store.FormatProblem;
import nl.enadeag.carchargeplanner.engine.store.Stored;
import nl.enadeag.carchargeplanner.engine.store.StoredFile;
import nl.enadeag.carchargeplanner.engine.store.StoredJson;
import java.io.IOException;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The calendar's consents, in a JSON file the engine keeps beside its other data, apart from the settings file so
 * that withdrawing them removes the file at once:
 * {@code {"version": 1, "consents": [{"consent": "READ_CALENDAR", "givenAt": "2026-10-22T08:00:00Z", "textVersion": 1}]}},
 * where a file without a version is version 1. A file that is missing, cannot be read or holds something this version
 * cannot use counts as no consent at all, so the calendar is never read on a doubt; a file that cannot be used is kept
 * as {@code <name>.damaged-<time>} when consents are saved.
 */
public final class ConsentFile {

    /** The shape this app writes, and where the migrations from older shapes go: none yet, 1 is the first. */
    private static final FileVersion VERSION = new FileVersion(1, List.of());

    private final StoredFile<Consents> file;

    /**
     * Makes the store for one file; nothing is read or written yet.
     *
     * @param path where the file is, or should be; it and its folder are made when the first consent is saved
     */
    public ConsentFile(Path path) {
        this.file = new StoredFile<>(path, new Format());
    }

    /**
     * The consents the owner gave.
     *
     * @return what the file holds; {@link Consents#NONE} when there is no file or it cannot be used
     */
    public Consents consents() {
        return file.read() instanceof Stored.Found<Consents>(var consents) ? consents : Consents.NONE;
    }

    /**
     * Replaces the stored consents; with none left, removes the file.
     *
     * @param consents the consents to keep; {@link Consents#NONE} withdraws them all
     * @throws IOException when the file could not be written or removed, or a file that could not be used not renamed
     */
    public void save(Consents consents) throws IOException {
        if (consents.given().isEmpty()) {
            file.delete();
        } else {
            file.write(consents);
        }
    }

    /** The JSON shape of the consents. */
    private static final class Format implements FileFormat<Consents> {

        @Override
        public Consents read(JsonNode root) throws FormatProblem {
            var list = VERSION.upToDate(root).path("consents");
            if (!list.isArray()) {
                throw new FormatProblem("consents must be a list");
            }
            var given = new EnumMap<Consent, ConsentRecord>(Consent.class);
            for (var item : list.values()) {
                var record = record(item);
                if (given.put(record.consent(), record) != null) {
                    throw new FormatProblem("consents lists " + record.consent() + " more than once");
                }
            }
            return new Consents(given);
        }

        private static ConsentRecord record(JsonNode item) throws FormatProblem {
            var consent = item.path("consent");
            var givenAt = item.path("givenAt");
            var version = item.path("textVersion");
            if (!consent.isString() || !givenAt.isString() || !version.isIntegralNumber() || !version.canConvertToInt()) {
                throw new FormatProblem("each consent needs a consent, a givenAt moment and a whole textVersion");
            }
            try {
                return new ConsentRecord(Consent.valueOf(consent.stringValue().toUpperCase(Locale.ROOT)),
                        Instant.parse(givenAt.stringValue()), version.intValue());
            } catch (DateTimeException | IllegalArgumentException _) {
                throw new FormatProblem("a consent must be READ_CALENDAR or SEND_LOCATIONS, givenAt a moment such as "
                        + "2026-10-22T08:00:00Z, and textVersion 1 or more");
            }
        }

        @Override
        public ObjectNode write(Consents consents) {
            var file = StoredJson.object();
            var list = file.putArray("consents");
            for (var consent : Consent.values()) {
                consents.record(consent).ifPresent(record -> list.addObject()
                        .put("consent", record.consent().name())
                        .put("givenAt", record.givenAt().toString())
                        .put("textVersion", record.textVersion()));
            }
            return VERSION.stamped(file);
        }
    }
}

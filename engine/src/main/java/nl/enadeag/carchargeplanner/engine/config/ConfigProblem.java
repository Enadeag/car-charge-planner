package nl.enadeag.carchargeplanner.engine.config;

import java.nio.file.Path;

/**
 * The config file is missing or does not describe a valid system. The kind and the file are for a client to tell the
 * owner in their own language; the detail and the message are the engine's own words, in English.
 */
public class ConfigProblem extends Exception {

    /** What is wrong with the config file. */
    public enum Kind {
        /** There is no file at the path. */
        MISSING,
        /** The file is there, but could not be read. */
        UNREADABLE,
        /** The file is not JSON, or a value in it is missing or wrong. */
        INVALID,
        /** The file was written by a newer version of the app, which this version cannot read; it is not replaced. */
        NEWER_VERSION
    }

    private final Kind kind;
    private final Path file;
    private final String detail;

    /**
     * Makes the problem.
     *
     * @param kind what is wrong with the file; not null
     * @param file the config file's path, as the engine was given it; not null
     * @param detail in English: the system's error for an unreadable file, for an invalid one the setting and what is
     *     wrong with it, such as "system.car.usableBatteryKwh: must be more than 0", and for one of a newer version
     *     its version and the newest this version reads; empty for a missing file
     */
    public ConfigProblem(Kind kind, Path file, String detail) {
        super(message(kind, file, detail));
        this.kind = kind;
        this.file = file;
        this.detail = detail;
    }

    /**
     * What is wrong with the file.
     *
     * @return the kind this problem was made with
     */
    public Kind kind() {
        return kind;
    }

    /**
     * The config file.
     *
     * @return the path this problem was made with
     */
    public Path file() {
        return file;
    }

    /**
     * The engine's details, in English.
     *
     * @return the detail this problem was made with; empty for a missing file
     */
    public String detail() {
        return detail;
    }

    private static String message(Kind kind, Path file, String detail) {
        return switch (kind) {
            case MISSING -> "There is no config file at " + file;
            case UNREADABLE -> "Could not read " + file + ": " + detail;
            case INVALID, NEWER_VERSION -> file.getFileName() + ": " + detail;
        };
    }
}

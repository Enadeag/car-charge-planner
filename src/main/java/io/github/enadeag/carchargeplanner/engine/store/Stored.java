package io.github.enadeag.carchargeplanner.engine.store;

/**
 * What a {@link StoredFile} holds: nothing yet, a value, or a file this version cannot use.
 *
 * @param <T> the kind of value the file keeps
 */
public sealed interface Stored<T> {

    /**
     * There is no file: nothing was stored yet.
     *
     * @param <T> the kind of value the file keeps
     */
    record Absent<T>() implements Stored<T> {
    }

    /**
     * The file holds a value this version can use.
     *
     * @param <T> the kind of value the file keeps
     * @param value the value read from the file, or the one written last
     */
    record Found<T>(T value) implements Stored<T> {
    }

    /**
     * The file is there, but this version cannot use it. It is kept as it is: a write first moves it aside.
     *
     * @param <T> the kind of value the file keeps
     * @param reason why it cannot be used
     * @param detail in English, what is wrong and where, such as {@code not valid JSON (line 3, column 7)} or
     *     {@code written by a newer version of the app}; never a secret from the file
     */
    record Unusable<T>(Reason reason, String detail) implements Stored<T> {
    }

    /** Why a file cannot be used. */
    enum Reason {
        /** The system refused to read it, such as for its permissions. */
        UNREADABLE,
        /** It is not the JSON this version expects: damaged, wrongly edited, or written by a newer version. */
        INVALID
    }
}

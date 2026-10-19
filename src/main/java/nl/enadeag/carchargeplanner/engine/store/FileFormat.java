package nl.enadeag.carchargeplanner.engine.store;

import tools.jackson.databind.JsonNode;

/**
 * How one kind of file is read and written: the JSON shape of its value, side by side.
 *
 * @param <T> the kind of value the file keeps
 */
public interface FileFormat<T> {

    /**
     * Reads the value from the file's JSON.
     *
     * @param root the file's JSON, which may be of any shape
     * @return the value
     * @throws FormatProblem when this version cannot use the JSON; the message says what is wrong and where, and
     *     never quotes a secret
     */
    T read(JsonNode root) throws FormatProblem;

    /**
     * Writes a value as JSON, in the shape {@link #read(JsonNode)} reads.
     *
     * @param value the value to keep
     * @return its JSON
     */
    JsonNode write(T value);

    /**
     * Whether the file holds secrets, so that no message or log quotes a part of it, not even of a damaged one.
     *
     * @return false unless the format says otherwise
     */
    default boolean holdsSecrets() {
        return false;
    }
}

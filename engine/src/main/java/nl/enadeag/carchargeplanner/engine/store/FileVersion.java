package nl.enadeag.carchargeplanner.engine.store;

import java.util.List;
import java.util.function.UnaryOperator;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The version of a file's JSON shape, kept as {@code "version"} in the file's object, and the one place where an
 * older shape is brought up to the current one. A file without it is version 1, which is what files were before they
 * had one; a file of a newer version is refused, as this version cannot know what it holds.
 *
 * @param current the version this app writes, 1 or more
 * @param migrations the steps from each older version to the next: the first turns version 1 into version 2, and so
 *     on, so there is one less than {@code current}
 */
public record FileVersion(int current, List<UnaryOperator<ObjectNode>> migrations) {

    /** The name of the version in the file's object. */
    static final String FIELD = "version";

    /**
     * Checks that there is a step from every older version, and copies the list.
     *
     * @throws IllegalArgumentException when the version is below 1 or the steps do not reach it from version 1
     */
    public FileVersion {
        if (current < 1) {
            throw new IllegalArgumentException("a file version is 1 or more, not " + current);
        }
        if (migrations.size() != current - 1) {
            throw new IllegalArgumentException("version " + current + " needs " + (current - 1)
                    + " migrations from the older versions, not " + migrations.size());
        }
        migrations = List.copyOf(migrations);
    }

    /**
     * A file's object in the current shape, without its version.
     *
     * @param root the file's JSON as it was read; changed in place
     * @return the object, brought up to the current version
     * @throws FormatProblem when the file is not one object, its version is not a whole number from 1, or it was
     *     written by a newer version of the app
     */
    public ObjectNode upToDate(JsonNode root) throws FormatProblem {
        if (!(root instanceof ObjectNode file)) {
            throw new FormatProblem("the file must hold one JSON object");
        }
        int version = 1;
        var stamp = file.remove(FIELD);
        if (stamp != null) {
            if (!stamp.isIntegralNumber() || !stamp.canConvertToInt() || stamp.intValue() < 1) {
                throw new FormatProblem(FIELD + ": must be a whole number from 1");
            }
            version = stamp.intValue();
        }
        if (version > current) {
            throw new FormatProblem("written by a newer version of the app (file version " + version
                    + "; this version reads up to " + current + ")");
        }
        var upToDate = file;
        for (int older = version; older < current; older++) {
            upToDate = migrations.get(older - 1).apply(upToDate);
        }
        return upToDate;
    }

    /**
     * A file's object with the current version first.
     *
     * @param body the values the file holds, without a version
     * @return a new object: the version, then the body's values in their order
     */
    public ObjectNode stamped(ObjectNode body) {
        var file = StoredJson.object().put(FIELD, current);
        file.setAll(body);
        return file;
    }
}

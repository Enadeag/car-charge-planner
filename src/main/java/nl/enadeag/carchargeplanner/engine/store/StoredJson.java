package nl.enadeag.carchargeplanner.engine.store;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The one JSON mapper for the app's own stored data, as plain trees: each file reads and writes its own shape by hand,
 * so no record of the domain knows how it is stored. The providers' answers have a mapper of their own.
 */
public final class StoredJson {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private StoredJson() {
    }

    /**
     * Parses stored text.
     *
     * @param text the whole content of a file or a stored value
     * @return the tree it holds
     * @throws JacksonException when the text is not JSON
     */
    public static JsonNode parse(String text) {
        return MAPPER.readTree(text);
    }

    /**
     * Writes a tree as text for a file the owner may read, one value per line.
     *
     * @param tree the tree to write
     * @return the JSON text
     */
    public static String pretty(JsonNode tree) {
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(tree);
    }

    /**
     * Writes a tree as text on one line, for data nobody reads by hand, such as a cache.
     *
     * @param tree the tree to write
     * @return the JSON text
     */
    public static String compact(JsonNode tree) {
        return MAPPER.writeValueAsString(tree);
    }

    /**
     * A new, empty JSON object to fill.
     *
     * @return the object
     */
    public static ObjectNode object() {
        return MAPPER.createObjectNode();
    }

    /**
     * A new, empty JSON list to fill.
     *
     * @return the list
     */
    public static ArrayNode array() {
        return MAPPER.createArrayNode();
    }
}

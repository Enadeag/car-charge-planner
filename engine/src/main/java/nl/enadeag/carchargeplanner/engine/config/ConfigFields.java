package nl.enadeag.carchargeplanner.engine.config;

import nl.enadeag.carchargeplanner.engine.store.FormatProblem;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * One JSON object of the config file, read value by value. Every problem names its place in the file, such as
 * {@code system.panels[0].kwp}, so the owner finds it. A number may also be given in quotes, such as {@code "30"}, as
 * Jackson reads it; a text must be in quotes.
 */
final class ConfigFields {

    private final ObjectNode object;
    private final String place;

    private ConfigFields(ObjectNode object, String place) {
        this.object = object;
        this.place = place;
    }

    /**
     * The fields of a JSON object.
     *
     * @param node the value that must be an object
     * @param place where it is in the file, such as {@code system.car}; empty for the whole file
     * @return its fields
     * @throws FormatProblem when the value is not a JSON object
     */
    static ConfigFields of(JsonNode node, String place) throws FormatProblem {
        if (!(node instanceof ObjectNode found)) {
            throw new FormatProblem(place.isEmpty() ? "the file must hold one JSON object"
                    : place + ": must be a JSON object, not " + node);
        }
        return new ConfigFields(found, place);
    }

    /**
     * Refuses a name the object may not have, such as a misspelt one, so a value is never silently ignored.
     *
     * @param names every name the object may have, in the order they are listed in the message
     * @return these fields
     * @throws FormatProblem when the object has another name; the message lists the allowed ones
     */
    ConfigFields only(String... names) throws FormatProblem {
        var allowed = List.of(names);
        for (var name : object.propertyNames()) {
            if (!allowed.contains(name)) {
                throw new FormatProblem(prefix() + "unknown setting '" + name + "'; allowed are '"
                        + String.join("', '", allowed) + "'");
            }
        }
        return this;
    }

    /**
     * Whether the object has a value of this name, for an optional one.
     *
     * @param name the name to look for
     * @return true when it is there, even as {@code null}
     */
    boolean has(String name) {
        return object.has(name);
    }

    /**
     * The names in the object, in the order of the file, for an object whose names are data, such as provider ids.
     *
     * @return the names, possibly none
     */
    List<String> names() {
        return List.copyOf(object.propertyNames());
    }

    /**
     * A number.
     *
     * @param name the value's name
     * @return the number, which may be NaN or infinite when the file says so in quotes or in a huge number; the
     *     records refuse those
     * @throws FormatProblem when the value is missing or is not a number
     */
    double number(String name) throws FormatProblem {
        var value = value(name);
        if (value.isNumber()) {
            return value.doubleValue();
        }
        if (value.isString()) {
            try {
                return new BigDecimal(value.stringValue().trim()).doubleValue();
            } catch (NumberFormatException ignored) {
                // reported below
            }
        }
        throw new FormatProblem(placeOf(name) + ": must be a number, not " + value);
    }

    /**
     * A whole number, never one with a fraction such as {@code 1.5}.
     *
     * @param name the value's name
     * @return the number
     * @throws FormatProblem when the value is missing or is not a whole number that fits an {@code int}
     */
    int wholeNumber(String name) throws FormatProblem {
        var value = value(name);
        if (value.isIntegralNumber() && value.canConvertToInt()) {
            return value.intValue();
        }
        if (value.isString()) {
            try {
                return Integer.parseInt(value.stringValue().trim());
            } catch (NumberFormatException ignored) {
                // reported below
            }
        }
        throw new FormatProblem(placeOf(name) + ": must be a whole number, not " + value);
    }

    /**
     * A text.
     *
     * @param name the value's name
     * @return the text, possibly empty
     * @throws FormatProblem when the value is missing or is not a text in quotes
     */
    String text(String name) throws FormatProblem {
        return text(value(name), placeOf(name));
    }

    /**
     * A list of texts.
     *
     * @param name the value's name
     * @return the texts in their order, possibly none
     * @throws FormatProblem when the value is missing or is not a list of texts
     */
    List<String> texts(String name) throws FormatProblem {
        var texts = new ArrayList<String>();
        var values = list(name);
        for (int i = 0; i < values.size(); i++) {
            texts.add(text(values.get(i), placeOf(name) + "[" + i + "]"));
        }
        return texts;
    }

    /**
     * A list of lists of texts.
     *
     * @param name the value's name
     * @return the lists in their order, possibly none
     * @throws FormatProblem when the value is missing or is not a list of lists of texts
     */
    List<List<String>> textLists(String name) throws FormatProblem {
        var lists = new ArrayList<List<String>>();
        var values = list(name);
        for (int i = 0; i < values.size(); i++) {
            var inner = placeOf(name) + "[" + i + "]";
            if (!values.get(i).isArray()) {
                throw new FormatProblem(inner + ": must be a list, not " + values.get(i));
            }
            var texts = new ArrayList<String>();
            int j = 0;
            for (var value : values.get(i).values()) {
                texts.add(text(value, inner + "[" + j++ + "]"));
            }
            lists.add(texts);
        }
        return lists;
    }

    /**
     * An object inside this one.
     *
     * @param name the value's name
     * @return its fields
     * @throws FormatProblem when the value is missing or is not a JSON object
     */
    ConfigFields object(String name) throws FormatProblem {
        return of(value(name), placeOf(name));
    }

    /**
     * A list of objects.
     *
     * @param name the value's name
     * @return each object's fields, in their order, possibly none
     * @throws FormatProblem when the value is missing, is not a list, or holds something else than objects
     */
    List<ConfigFields> objects(String name) throws FormatProblem {
        var objects = new ArrayList<ConfigFields>();
        var values = list(name);
        for (int i = 0; i < values.size(); i++) {
            objects.add(of(values.get(i), placeOf(name) + "[" + i + "]"));
        }
        return objects;
    }

    /**
     * Makes a value from what was read, giving a check that refuses it the place of this object, as in
     * {@code system.car: usableBatteryKwh must be more than 0}.
     *
     * @param <T> the kind of value
     * @param make makes the value; its {@link IllegalArgumentException} says what is wrong
     * @return the value
     * @throws FormatProblem when the value is refused
     */
    <T> T make(Supplier<T> make) throws FormatProblem {
        try {
            return make.get();
        } catch (IllegalArgumentException e) {
            throw new FormatProblem(prefix() + e.getMessage());
        }
    }

    /**
     * Makes a value from one of the values read, giving a check that refuses it the place of that value, as in
     * {@code planning.minimumPct: a percentage must be from 0 to 100, not -1.0}.
     *
     * @param <T> the kind of value
     * @param name the name of the value it is made from
     * @param make makes the value; its {@link IllegalArgumentException} says what is wrong
     * @return the value
     * @throws FormatProblem when the value is refused
     */
    <T> T make(String name, Supplier<T> make) throws FormatProblem {
        try {
            return make.get();
        } catch (IllegalArgumentException e) {
            throw new FormatProblem(placeOf(name) + ": " + e.getMessage());
        }
    }

    private JsonNode value(String name) throws FormatProblem {
        var value = object.get(name);
        if (value == null) {
            throw new FormatProblem(prefix() + "'" + name + "' is missing");
        }
        return value;
    }

    private List<JsonNode> list(String name) throws FormatProblem {
        var value = value(name);
        if (!value.isArray()) {
            throw new FormatProblem(placeOf(name) + ": must be a list, not " + value);
        }
        return List.copyOf(value.values());
    }

    private static String text(JsonNode value, String place) throws FormatProblem {
        if (!value.isString()) {
            throw new FormatProblem(place + ": must be a text in quotes, not " + value);
        }
        return value.stringValue();
    }

    private String placeOf(String name) {
        return place.isEmpty() ? name : place + "." + name;
    }

    private String prefix() {
        return place.isEmpty() ? "" : place + ": ";
    }
}

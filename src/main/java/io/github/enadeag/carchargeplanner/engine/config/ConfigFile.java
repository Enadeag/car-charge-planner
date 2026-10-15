package io.github.enadeag.carchargeplanner.engine.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.exc.ValueInstantiationException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.type.LogicalType;

/**
 * The engine's own config file, in JSON. Every value must be given, as there are no typical values yet, except the
 * {@code away} section: a file without it, such as one written before away periods existed, has none.
 */
public final class ConfigFile {

    private final Path path;
    private final JsonMapper json = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT) // Jackson would read 1.5 phases as 1
            // and it would read a setting of 5 as the text "5"
            .withCoercionConfig(LogicalType.Textual, texts -> texts
                    .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
            .build();

    /**
     * Makes the reader for one config file; nothing is read yet.
     *
     * @param path where the file is, or should be; it need not exist, a missing file is reported when it is read
     */
    public ConfigFile(Path path) {
        this.path = path;
    }

    /**
     * Where the file is, for messages to the owner.
     *
     * @return the path this reader was made with
     */
    public Path path() {
        return path;
    }

    /**
     * Reads and checks the file again; nothing is kept between reads.
     *
     * @return the whole config, every value present and valid
     * @throws ConfigProblem when the file is missing, unreadable, not JSON, or a value is missing or wrong; the problem
     *     names the file and the setting
     */
    public EngineConfig read() throws ConfigProblem {
        String text;
        try {
            text = Files.readString(path);
        } catch (NoSuchFileException e) {
            throw new ConfigProblem(ConfigProblem.Kind.MISSING, path, "");
        } catch (IOException e) {
            throw new ConfigProblem(ConfigProblem.Kind.UNREADABLE, path, String.valueOf(e.getMessage()));
        }
        try {
            if (!(json.readTree(text) instanceof ObjectNode file)) {
                throw new ConfigProblem(ConfigProblem.Kind.INVALID, path, "the file must hold one JSON object");
            }
            // the mapper refuses any missing value, so the one optional section is filled in before it reads the file
            if (!file.has("away")) {
                file.putObject("away").putArray("recurring");
            }
            return json.treeToValue(file, EngineConfig.class);
        } catch (ValueInstantiationException e) {
            // a check in one of the records failed: its own message names the setting
            throw new ConfigProblem(ConfigProblem.Kind.INVALID, path, where(e) + e.getCause().getMessage());
        } catch (JacksonException e) {
            throw new ConfigProblem(ConfigProblem.Kind.INVALID, path, where(e) + e.getOriginalMessage());
        }
    }

    /** The place in the file, such as "system.panels[0]: ", or nothing when the problem is not in one setting. */
    private String where(JacksonException e) {
        var place = new StringBuilder();
        for (var step : e.getPath()) {
            if (step.getPropertyName() != null) {
                place.append(place.isEmpty() ? "" : ".").append(step.getPropertyName());
            } else if (step.getIndex() >= 0) {
                place.append('[').append(step.getIndex()).append(']');
            }
        }
        return place.isEmpty() ? "" : place + ": ";
    }
}

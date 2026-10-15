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
import tools.jackson.databind.type.LogicalType;

/** The engine's own config file, in JSON. Every value must be given: there are no typical values yet. */
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
     * @throws ConfigProblem when the file is missing, unreadable, not JSON, or a value is missing or wrong; the message
     *     names the file and the setting
     */
    public EngineConfig read() throws ConfigProblem {
        String text;
        try {
            text = Files.readString(path);
        } catch (NoSuchFileException e) {
            throw new ConfigProblem("There is no config file at " + path);
        } catch (IOException e) {
            throw new ConfigProblem("Could not read " + path + ": " + e.getMessage());
        }
        try {
            return json.readValue(text, EngineConfig.class);
        } catch (ValueInstantiationException e) {
            // a check in one of the records failed: its own message names the setting
            throw new ConfigProblem(where(e) + ": " + e.getCause().getMessage());
        } catch (JacksonException e) {
            throw new ConfigProblem(where(e) + ": " + e.getOriginalMessage());
        }
    }

    /** The file and the place in it, such as "engine.json, system.panels[0]". */
    private String where(JacksonException e) {
        var place = new StringBuilder();
        for (var step : e.getPath()) {
            if (step.getPropertyName() != null) {
                place.append(place.isEmpty() ? "" : ".").append(step.getPropertyName());
            } else if (step.getIndex() >= 0) {
                place.append('[').append(step.getIndex()).append(']');
            }
        }
        return path.getFileName() + (place.isEmpty() ? "" : ", " + place);
    }
}

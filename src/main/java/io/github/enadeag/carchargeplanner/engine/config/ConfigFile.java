package io.github.enadeag.carchargeplanner.engine.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.exc.ValueInstantiationException;
import tools.jackson.databind.json.JsonMapper;

/** The engine's own config file, in JSON. Every value must be given: there are no typical values yet. */
public final class ConfigFile {

    private final Path path;
    private final JsonMapper json = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();

    public ConfigFile(Path path) {
        this.path = path;
    }

    public Path path() {
        return path;
    }

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

package io.github.enadeag.carchargeplanner.engine.config;

import io.github.enadeag.carchargeplanner.engine.store.PrivateFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.exc.ValueInstantiationException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.type.LogicalType;

/**
 * The engine's own config file, in JSON, read and written whole. Every value must be given, except the {@code away}
 * and {@code typical} sections: a file without them, such as one written before they existed, has no away periods and
 * no typical values.
 */
public final class ConfigFile {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

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
     * Makes the reader and writer for one config file; nothing is read yet.
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
            if (!file.has("typical")) {
                var typical = file.putObject("typical");
                typical.putArray("system");
                typical.putArray("panels");
            }
            return json.treeToValue(file, EngineConfig.class);
        } catch (ValueInstantiationException e) {
            // a check in one of the records failed: its own message names the setting
            throw new ConfigProblem(ConfigProblem.Kind.INVALID, path, where(e) + e.getCause().getMessage());
        } catch (JacksonException e) {
            throw new ConfigProblem(ConfigProblem.Kind.INVALID, path, where(e) + e.getOriginalMessage());
        }
    }

    /**
     * Replaces the file by this config, in the form {@link #read()} reads, through {@link PrivateFiles}: a reader sees
     * the old file or the new one, never half of one, and only the owner can read it.
     *
     * @param config the whole config to keep; its records have checked their values already
     * @throws IOException when the file or its folder could not be written; the file is then left as it was
     */
    public void write(EngineConfig config) throws IOException {
        PrivateFiles.write(path, json.writerWithDefaultPrettyPrinter().writeValueAsString(tree(config)));
    }

    private static ObjectNode tree(EngineConfig config) {
        var file = JsonNodeFactory.instance.objectNode();
        system(file.putObject("system"), config.system());
        var planning = config.planning();
        file.putObject("planning")
                .put("minimumPct", planning.minimumPct().value())
                .put("targetPct", planning.targetPct().value())
                .put("margin", planning.margin())
                .put("importToleranceKwh", planning.importToleranceKwh())
                .put("minWindowMinutes", planning.minWindowMinutes())
                .put("notifyLeadMinutes", planning.notifyLeadMinutes());
        forecast(file.putObject("forecast"), config.forecast());
        var recurring = file.putObject("away").putArray("recurring");
        config.away().recurring().forEach(period -> recurring.addObject()
                .put("weekday", period.weekday().name())
                .put("start", TIME.format(period.start()))
                .put("end", TIME.format(period.end())));
        var typical = file.putObject("typical");
        var system = typical.putArray("system");
        config.typical().system().stream().sorted().forEach(setting -> system.add(setting.name()));
        var panels = typical.putArray("panels");
        config.typical().panels().forEach(array -> {
            var settings = panels.addArray();
            array.stream().sorted().forEach(setting -> settings.add(setting.name()));
        });
        return file;
    }

    private static void system(ObjectNode node, HomeSystem system) {
        node.put("latitude", system.location().latitude())
                .put("longitude", system.location().longitude())
                .put("timezone", system.timezone().getId());
        var panels = node.putArray("panels");
        system.panels().forEach(array -> panels.addObject()
                .put("name", array.name())
                .put("kwp", array.kwp())
                .put("tiltDeg", array.tiltDeg())
                .put("azimuthDeg", array.azimuthDeg())
                .put("systemLosses", array.systemLosses()));
        node.putObject("house").put("baseloadKw", system.house().baseloadKw());
        var car = system.car();
        node.putObject("car")
                .put("usableBatteryKwh", car.usableBatteryKwh())
                .put("consumptionKwhPer100km", car.consumptionKwhPer100km())
                .put("kmPerDay", car.kmPerDay());
        var charger = system.charger();
        node.putObject("charger")
                .put("phases", charger.phases().count())
                .put("minAmps", charger.minAmps())
                .put("maxAmps", charger.maxAmps())
                .put("volts", charger.volts())
                .put("efficiency", charger.efficiency());
    }

    private static void forecast(ObjectNode node, ForecastSelection forecast) {
        node.put("primary", forecast.primary().value())
                .put("fallback", forecast.fallback().map(ProviderId::value).orElse(""));
        var settings = node.putObject("settings");
        forecast.settings().entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().value()))
                .forEach(entry -> {
                    var values = settings.putObject(entry.getKey().value());
                    entry.getValue().entrySet().stream().sorted(Map.Entry.comparingByKey())
                            .forEach(value -> values.put(value.getKey(), value.getValue()));
                });
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

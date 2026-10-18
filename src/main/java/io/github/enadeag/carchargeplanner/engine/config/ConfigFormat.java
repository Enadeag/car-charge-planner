package io.github.enadeag.carchargeplanner.engine.config;

import io.github.enadeag.carchargeplanner.engine.away.RecurringAwayPeriod;
import io.github.enadeag.carchargeplanner.engine.setting.Setting;
import io.github.enadeag.carchargeplanner.engine.store.FileFormat;
import io.github.enadeag.carchargeplanner.engine.store.FileVersion;
import io.github.enadeag.carchargeplanner.engine.store.FormatProblem;
import io.github.enadeag.carchargeplanner.engine.store.StoredJson;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The JSON shape of {@code engine.json}, read and written side by side, as {@code docs/example-engine.json} shows it.
 * Every value must be given, except the {@code away} and {@code typical} sections; a name the shape does not have is
 * refused. Percentages, charger phases and provider ids are plain numbers and texts; an empty fallback means none; a
 * weekday is its English name in any case and a time is {@code "HH:mm"}; a typical value is its {@link Setting}'s
 * name. The file's {@code version} is 1; a file without one is read as version 1.
 */
final class ConfigFormat implements FileFormat<EngineConfig> {

    /** The shape this app writes, and where the migrations from older shapes go: none yet, 1 is the first. */
    private static final FileVersion VERSION = new FileVersion(1, List.of());

    /** Strict, so that "24:00" is refused rather than read as the midnight that starts the day. */
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);

    /** Makes the format; it keeps nothing. */
    ConfigFormat() {
    }

    /**
     * Reads a whole config.
     *
     * @param root the file's JSON
     * @return the config, every value checked by its record
     * @throws FormatProblem when a value is missing, of the wrong kind, refused by its record, or a name is not known,
     *     or the file was written by a newer version of the app; the message names the place in the file and what is
     *     allowed
     */
    @Override
    public EngineConfig read(JsonNode root) throws FormatProblem {
        var file = ConfigFields.of(VERSION.upToDate(root), "").only("system", "planning", "forecast", "away", "typical");
        var system = system(file.object("system"));
        var planning = planning(file.object("planning"));
        var forecast = forecast(file.object("forecast"));
        var away = file.has("away") ? away(file.object("away")) : AwaySchedule.NONE;
        var typical = file.has("typical") ? typical(file.object("typical")) : TypicalValues.NONE;
        return file.make(() -> new EngineConfig(system, planning, forecast, away, typical));
    }

    /**
     * Writes a whole config in the shape {@link #read(JsonNode)} reads.
     *
     * @param config the config to write
     * @return its JSON, with the version first
     */
    @Override
    public ObjectNode write(EngineConfig config) {
        var file = StoredJson.object();
        writeSystem(file.putObject("system"), config.system());
        var planning = config.planning();
        file.putObject("planning")
                .put("minimumPct", planning.minimumPct().value())
                .put("targetPct", planning.targetPct().value())
                .put("margin", planning.margin())
                .put("importToleranceKwh", planning.importToleranceKwh())
                .put("minWindowMinutes", planning.minWindow().toMinutes())
                .put("notifyLeadMinutes", planning.notifyLead().toMinutes());
        writeForecast(file.putObject("forecast"), config.forecast());
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
        return VERSION.stamped(file);
    }

    private static HomeSystem system(ConfigFields system) throws FormatProblem {
        system.only("latitude", "longitude", "timezone", "panels", "house", "car", "charger");
        double latitude = system.number("latitude");
        double longitude = system.number("longitude");
        var location = system.make(() -> new Coordinates(latitude, longitude));
        var timezone = zone(system);
        var panels = new ArrayList<PanelArray>();
        for (var array : system.objects("panels")) {
            panels.add(panelArray(array));
        }
        var house = house(system.object("house"));
        var car = car(system.object("car"));
        var charger = charger(system.object("charger"));
        return system.make(() -> new HomeSystem(location, timezone, panels, house, car, charger));
    }

    private static House house(ConfigFields house) throws FormatProblem {
        house.only("baseloadKw");
        double baseload = house.number("baseloadKw");
        return house.make(() -> new House(baseload));
    }

    private static Car car(ConfigFields car) throws FormatProblem {
        car.only("usableBatteryKwh", "consumptionKwhPer100km", "kmPerDay");
        double battery = car.number("usableBatteryKwh");
        double consumption = car.number("consumptionKwhPer100km");
        double distance = car.number("kmPerDay");
        return car.make(() -> new Car(battery, consumption, distance));
    }

    private static ZoneId zone(ConfigFields system) throws FormatProblem {
        var id = system.text("timezone");
        return system.make("timezone", () -> {
            try {
                return ZoneId.of(id);
            } catch (DateTimeException e) {
                throw new IllegalArgumentException("'" + id + "' is not a time zone such as Europe/Amsterdam", e);
            }
        });
    }

    private static PanelArray panelArray(ConfigFields array) throws FormatProblem {
        array.only("name", "kwp", "tiltDeg", "azimuthDeg", "systemLosses");
        var name = array.text("name");
        double kwp = array.number("kwp");
        double tilt = array.number("tiltDeg");
        double azimuth = array.number("azimuthDeg");
        double losses = array.number("systemLosses");
        return array.make(() -> new PanelArray(name, kwp, tilt, azimuth, losses));
    }

    private static Charger charger(ConfigFields charger) throws FormatProblem {
        charger.only("phases", "minAmps", "maxAmps", "volts", "efficiency");
        int count = charger.wholeNumber("phases");
        var phases = charger.make(() -> Phases.of(count));
        int minAmps = charger.wholeNumber("minAmps");
        int maxAmps = charger.wholeNumber("maxAmps");
        double volts = charger.number("volts");
        double efficiency = charger.number("efficiency");
        return charger.make(() -> new Charger(phases, minAmps, maxAmps, volts, efficiency));
    }

    private static PlanningPreferences planning(ConfigFields planning) throws FormatProblem {
        planning.only("minimumPct", "targetPct", "margin", "importToleranceKwh", "minWindowMinutes",
                "notifyLeadMinutes");
        var minimum = percent(planning, "minimumPct");
        var target = percent(planning, "targetPct");
        double margin = planning.number("margin");
        double importTolerance = planning.number("importToleranceKwh");
        var minWindow = Duration.ofMinutes(planning.wholeNumber("minWindowMinutes"));
        var notifyLead = Duration.ofMinutes(planning.wholeNumber("notifyLeadMinutes"));
        return planning.make(() -> new PlanningPreferences(minimum, target, margin, importTolerance, minWindow,
                notifyLead));
    }

    private static Percent percent(ConfigFields fields, String name) throws FormatProblem {
        double value = fields.number(name);
        return fields.make(name, () -> new Percent(value));
    }

    private static ForecastSelection forecast(ConfigFields forecast) throws FormatProblem {
        forecast.only("primary", "fallback", "settings");
        var primaryId = forecast.text("primary");
        var primary = forecast.make("primary", () -> new ProviderId(primaryId));
        var fallbackId = forecast.text("fallback");
        if (!fallbackId.isEmpty() && !ProviderId.isValid(fallbackId)) {
            throw new FormatProblem("forecast.fallback: must be empty or a provider id such as forecast-solar, not '"
                    + fallbackId + "'");
        }
        var fallback = fallbackId.isEmpty() ? Optional.<ProviderId>empty() : Optional.of(new ProviderId(fallbackId));
        var settings = forecast.object("settings");
        var byProvider = new LinkedHashMap<ProviderId, Map<String, String>>();
        for (var id : settings.names()) {
            if (!ProviderId.isValid(id)) {
                throw new FormatProblem("forecast.settings: '" + id + "' is not a provider id such as open-meteo");
            }
            var values = settings.object(id);
            var texts = new LinkedHashMap<String, String>();
            for (var key : values.names()) {
                texts.put(key, values.text(key));
            }
            byProvider.put(new ProviderId(id), texts);
        }
        return forecast.make(() -> new ForecastSelection(primary, fallback, byProvider));
    }

    private static AwaySchedule away(ConfigFields away) throws FormatProblem {
        away.only("recurring");
        var recurring = new ArrayList<RecurringAwayPeriod>();
        for (var period : away.objects("recurring")) {
            period.only("weekday", "start", "end");
            var weekday = weekday(period);
            var start = time(period, "start");
            var end = time(period, "end");
            recurring.add(period.make(() -> new RecurringAwayPeriod(weekday, start, end)));
        }
        return away.make(() -> new AwaySchedule(recurring));
    }

    private static DayOfWeek weekday(ConfigFields period) throws FormatProblem {
        var name = period.text("weekday");
        var upper = name.toUpperCase(Locale.ROOT);
        return period.make("weekday", () -> EnumSet.allOf(DayOfWeek.class).stream()
                .filter(day -> day.name().equals(upper)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "must be a day of the week such as MONDAY, not '" + name + "'")));
    }

    private static LocalTime time(ConfigFields period, String name) throws FormatProblem {
        var text = period.text(name);
        return period.make(name, () -> {
            try {
                return LocalTime.parse(text, TIME);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("must be a time such as \"06:00\", not '" + text + "'", e);
            }
        });
    }

    private static TypicalValues typical(ConfigFields typical) throws FormatProblem {
        typical.only("system", "panels");
        var system = settings(typical.texts("system"), "typical.system");
        var panels = new ArrayList<Set<Setting>>();
        var lists = typical.textLists("panels");
        for (int i = 0; i < lists.size(); i++) {
            panels.add(settings(lists.get(i), "typical.panels[" + i + "]"));
        }
        return typical.make(() -> new TypicalValues(system, panels));
    }

    private static Set<Setting> settings(List<String> names, String place) throws FormatProblem {
        var settings = EnumSet.noneOf(Setting.class);
        for (var name : names) {
            try {
                settings.add(Setting.valueOf(name));
            } catch (IllegalArgumentException e) {
                throw new FormatProblem(place + ": '" + name + "' is not a setting such as CAR_BATTERY");
            }
        }
        return settings;
    }

    private static void writeSystem(ObjectNode node, HomeSystem system) {
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

    private static void writeForecast(ObjectNode node, ForecastSelection forecast) {
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
}

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * A stand-in for Open-Meteo's forecast, for the pictures of the Android app's listing (#486): it answers
 * {@code /v1/forecast} as Open-Meteo does, with a made-up forecast that starts on a given day, so the pictures never
 * show a live forecast and come out the same on every run. The day is sunny, the next a little less, the third hazy
 * and the fourth grey.
 *
 * <pre>java tools/android-listing/MadeUpOpenMeteo.java 8765 2026-07-03</pre>
 *
 * <p>It listens on 127.0.0.1 only, and prints a line for each forecast it served. {@code
 * tools/android-listing-pictures.sh} runs it and lets the emulator's debug build reach it through {@code adb reverse},
 * as the debug build allows plain http to 127.0.0.1. A single source file that the JDK runs as it is. Exits with code 2
 * on wrong arguments.
 */
public final class MadeUpOpenMeteo {

    /**
     * The sunlight on the panels at its highest, in W/m², from the first day on. The first day's is one at which the
     * example home's charge window takes nothing from the grid, so the first picture shows no "0.1 kWh from the grid"
     * beside a rounded "100% solar".
     */
    private static final double[] PEAKS = {840, 640, 560, 190};

    private MadeUpOpenMeteo() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2 || !args[0].matches("[1-9][0-9]{0,4}")) {
            System.err.println("usage: java tools/android-listing/MadeUpOpenMeteo.java <port> <first day, yyyy-mm-dd>");
            System.exit(2);
        }
        var port = Integer.parseInt(args[0]);
        var first = LocalDate.parse(args[1]);
        var loopback = InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
        var server = HttpServer.create(new InetSocketAddress(loopback, port), 0);
        server.createContext("/v1/forecast", exchange -> answer(exchange, first));
        server.start();
        System.out.println("made-up Open-Meteo on http://127.0.0.1:" + port + " from " + first);
    }

    private static void answer(HttpExchange exchange, LocalDate first) throws IOException {
        try (exchange) {
            var query = query(exchange.getRequestURI().getRawQuery());
            ZoneId zone;
            int days;
            try {
                zone = ZoneId.of(query.getOrDefault("timezone", "UTC"));
                days = Math.min(Integer.parseInt(query.getOrDefault("forecast_days", "1")), PEAKS.length);
            } catch (RuntimeException e) {
                send(exchange, 400, "{\"error\":true,\"reason\":\"" + e.getClass().getSimpleName() + "\"}");
                return;
            }
            send(exchange, 200, forecast(first, zone, days));
            System.out.println("served " + days + " days for tilt " + query.get("tilt") + ", azimuth "
                    + query.get("azimuth"));
        }
    }

    /**
     * The hours from midnight of the first day as Open-Meteo stamps them, in seconds: the value stamped T is the mean
     * of the hour before it.
     */
    private static String forecast(LocalDate first, ZoneId zone, int days) {
        var times = new StringJoiner(",", "[", "]");
        var values = new StringJoiner(",", "[", "]");
        for (int day = 0; day < days; day++) {
            var midnight = first.plusDays(day).atStartOfDay(zone).toEpochSecond();
            for (int hour = 0; hour < 24; hour++) {
                // sun from 06:00 to 21:00, highest at half past one; a hazy or grey day comes and goes in waves
                double middle = hour - 0.5;
                double bell = middle > 6 && middle < 21 ? Math.sin(Math.PI * (middle - 6) / 15) : 0;
                double clouds = PEAKS[day] < 700 ? 0.8 + 0.2 * Math.cos(hour * 1.3) : 1;
                times.add(String.valueOf(midnight + hour * 3600L));
                values.add(String.valueOf(Math.round(PEAKS[day] * bell * clouds * 10) / 10.0));
            }
        }
        return "{\"timezone\":\"" + zone.getId() + "\",\"hourly_units\":{\"time\":\"unixtime\","
                + "\"global_tilted_irradiance\":\"W/m²\"},\"hourly\":{\"time\":" + times + ","
                + "\"global_tilted_irradiance\":" + values + "}}";
    }

    private static Map<String, String> query(String raw) {
        var query = new HashMap<String, String>();
        if (raw != null) {
            for (var pair : raw.split("&")) {
                var parts = pair.split("=", 2);
                query.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                        parts.length == 2 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "");
            }
        }
        return query;
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
}

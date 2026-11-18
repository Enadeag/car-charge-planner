package nl.enadeag.carchargeplanner.android.minified;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The release build as R8 makes it, through the few entry points Android and slf4j reach by name: the slf4j provider,
 * the activity on a settings file (Compose, Jackson and the planner), and a round on WorkManager. The tests touch the
 * app only from outside, by class name, intent, resource name, Logcat and the accessibility tree, as the app's classes
 * keep only what the app itself uses. In Java, so the test APK calls nothing of Kotlin's library, which the app keeps
 * only in part too. Run with {@code -PtestBuildType=minified} (docs/dev/android.md).
 */
public class MinifiedAppTest {

    private static final String APP_PACKAGE = "nl.enadeag.carchargeplanner.android";
    private static final String ROUND_WORKER = APP_PACKAGE + ".background.RoundWorker";
    private static final ZoneId ZONE = ZoneId.of("Europe/Brussels");

    private final Instrumentation instrumentation = MinifiedTestRunner.instrumentation();
    private final Context app = instrumentation.getTargetContext();
    private final List<Activity> started = new ArrayList<>();

    @Before
    public void prepareTheSettingsFileAndAForecast() throws IOException {
        try (InputStream example = instrumentation.getContext().getAssets().open("example-engine.json");
                OutputStream out = new FileOutputStream(new File(app.getFilesDir(), "engine.json"))) {
            byte[] buffer = new byte[8192];
            for (int read = example.read(buffer); read >= 0; read = example.read(buffer)) {
                out.write(buffer, 0, read);
            }
        }
        File forecasts = new File(app.getFilesDir(), "forecasts");
        if (!forecasts.isDirectory() && !forecasts.mkdirs()) {
            throw new IOException("cannot make " + forecasts);
        }
        try (OutputStream out = new FileOutputStream(new File(forecasts, "forecast-open-meteo.json"))) {
            out.write(forecast().getBytes(StandardCharsets.UTF_8));
        }
    }

    @After
    public void finishTheActivity() {
        for (Activity activity : started) {
            instrumentation.runOnMainSync(activity::finish);
        }
    }

    /**
     * slf4j finds the app's provider, whose logger keeps its name, and a stack trace through the logger names its class
     * and method, and has a source file and a line: the logger writes the exception's trace, which asks the exception's
     * {@code toString}. Only the logger's calls the app makes itself are left, such as a warning with an exception. R8
     * names the file {@code r8-map-id-<id>} and numbers the lines anew, so both need the build's mapping file.
     */
    @Test
    public void slf4jUsesTheAppsLoggerWhoseFramesKeepTheirNamesAndALine() {
        Logger log = LoggerFactory.getLogger("minified");
        StackTraceElement[][] trace = new StackTraceElement[1][];
        Exception problem = new Exception() {
            @Override
            public String toString() {
                trace[0] = new Throwable().getStackTrace();
                return "a made-up problem";
            }
        };

        log.warn("logged by the minified suite", problem);

        String logger = APP_PACKAGE + ".logging.LogcatLogger";
        assertEquals(logger, log.getClass().getName());
        assertTrue("the logger wrote the exception", trace[0] != null);
        StackTraceElement frame = null;
        for (StackTraceElement element : trace[0]) {
            if (frame == null && element.getClassName().equals(logger)) {
                frame = element;
            }
        }
        assertTrue("a frame of the logger in " + Arrays.toString(trace[0]), frame != null);
        assertEquals("handleNormalizedLoggingCall", frame.getMethodName());
        assertTrue("a source file in " + frame, frame.getFileName() != null);
        assertTrue("a line number in " + frame, frame.getLineNumber() > 0);
    }

    /** The plan's note on the battery level, which only a made plan shows, is read from the screen. */
    @Test
    public void theAppStartsOnTheSettingsFileAndShowsAPlan() {
        String note = text("day_battery_assumed");
        String shown = note.substring(0, note.indexOf('%'));

        start();

        long deadline = SystemClock.uptimeMillis() + TimeUnit.SECONDS.toMillis(30);
        boolean found = false;
        while (!found && SystemClock.uptimeMillis() < deadline) {
            found = onScreen(shown);
            if (!found) {
                SystemClock.sleep(250);
            }
        }
        assertTrue("the screen shows \"" + shown + "…\"", found);
    }

    /** The start of the activity with a usable settings file asks a round now, which WorkManager runs. */
    @Test
    public void aRoundRunsOnWorkManager() throws IOException {
        int before = roundsDone();

        start();

        long deadline = SystemClock.uptimeMillis() + TimeUnit.SECONDS.toMillis(60);
        int after = before;
        while (after == before && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(500);
            after = roundsDone();
        }
        assertTrue("a round ended as a success, " + before + " before the start", after > before);
    }

    /** Starts the app's activity as the launcher does; notifications are allowed, so the start schedules the work. */
    private void start() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            UiAutomation automation = instrumentation.getUiAutomation();
            automation.grantRuntimePermission(app.getPackageName(), Manifest.permission.POST_NOTIFICATIONS);
        }
        Intent intent = new Intent(Intent.ACTION_MAIN).setClassName(app, APP_PACKAGE + ".MainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        started.add(instrumentation.startActivitySync(intent));
    }

    /** A text of the app by its resource name, as the build left the resources unshrunk. */
    private String text(String name) {
        int id = app.getResources().getIdentifier(name, "string", app.getPackageName());
        assertTrue("the app has the text " + name, id != 0);
        return app.getString(id);
    }

    /** Whether a node of the active window's accessibility tree has a text that contains [part]. */
    private boolean onScreen(String part) {
        AccessibilityNodeInfo root = instrumentation.getUiAutomation().getRootInActiveWindow();
        return root != null && contains(root, part);
    }

    private static boolean contains(AccessibilityNodeInfo node, String part) {
        CharSequence text = node.getText();
        if (text != null && text.toString().contains(part)) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null && contains(child, part)) {
                return true;
            }
        }
        return false;
    }

    /** How many of the round's works ended as a success, by WorkManager's own Logcat line for each. */
    private static int roundsDone() throws IOException {
        Process logcat = new ProcessBuilder("logcat", "-d", "-v", "tag").redirectErrorStream(true).start();
        int done = 0;
        try (BufferedReader lines = new BufferedReader(
                new InputStreamReader(logcat.getInputStream(), StandardCharsets.UTF_8))) {
            for (String line = lines.readLine(); line != null; line = lines.readLine()) {
                if (line.contains("Worker result SUCCESS for") && line.contains(ROUND_WORKER)) {
                    done++;
                }
            }
        }
        return done;
    }

    /**
     * Two cloudless days, today and tomorrow, of hourly steps in the example's cache file of Open-Meteo, under the key
     * the engine gives the example's home, as if Open-Meteo had answered five minutes ago: the plan needs no network.
     */
    private static String forecast() {
        StringBuilder steps = new StringBuilder();
        LocalDate today = LocalDate.now(ZONE);
        for (LocalDate day : Arrays.asList(today, today.plusDays(1))) {
            Instant start = day.atStartOfDay(ZONE).toInstant();
            for (int hour = 0; hour < 24; hour++) {
                Instant stepStart = start.plus(Duration.ofHours(hour));
                double sun = Math.max(0.0, 4.5 * Math.sin(Math.PI * (hour + 0.5 - 6.0) / 14.0));
                steps.append(steps.length() == 0 ? "" : ",").append("[\"").append(stepStart).append("\",\"")
                        .append(stepStart.plus(Duration.ofHours(1))).append("\",").append(sun).append(']');
            }
        }
        String key = "{\"latitude\":51.0,\"longitude\":5.0,\"timezone\":\"Europe/Brussels\",\"arrays\":[{\"kwp\":5.0,"
                + "\"tiltDeg\":35.0,\"azimuthDeg\":0.0,\"systemLosses\":0.14}],\"settings\":{}}";
        return "{\"key\":" + key + ",\"fetchedAt\":\"" + Instant.now().minus(Duration.ofMinutes(5)) + "\",\"steps\":["
                + steps + "],\"cooldownUntil\":null,\"refreshFailed\":false}";
    }
}

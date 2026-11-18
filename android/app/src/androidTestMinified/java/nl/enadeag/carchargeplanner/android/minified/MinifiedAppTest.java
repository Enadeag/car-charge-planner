package nl.enadeag.carchargeplanner.android.minified;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.junit.After;
import org.junit.Before;
import org.json.JSONObject;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The release build as R8 makes it, through the few entry points Android and slf4j reach by name: the slf4j provider,
 * the activity on a settings file (Compose, Jackson and the planner), and a round on WorkManager. The tests touch the
 * app only from outside, by class name, intent, resource name, Logcat and the accessibility tree, as the app's classes
 * keep only what the app itself uses. In Java, so the test APK calls nothing of Kotlin's library, which the app keeps
 * only in part too. Run with {@code -PtestBuildType=minified} (docs/dev/android.md). What earlier runs leave on the
 * installation (the keychain's file, the Keystore's key, WorkManager's work) changes nothing: each test writes the
 * settings and forecast it starts on, types a key of its own and counts only the rounds that end after it starts.
 */
public class MinifiedAppTest {

    private static final String APP_PACKAGE = "nl.enadeag.carchargeplanner.android";
    private static final String ROUND_WORKER = APP_PACKAGE + ".background.RoundWorker";
    private static final ZoneId ZONE = ZoneId.of("Europe/Brussels");
    /** The alias of the app's first Keystore key, and of later ones with -2, -3 (AndroidKeystoreKeys.ALIAS). */
    private static final Pattern APP_ALIAS = Pattern.compile("keychain(-[0-9]+)?");
    private static final String TAG = "MinifiedAppTest";

    private final Instrumentation instrumentation = MinifiedTestRunner.instrumentation();
    private final Context app = instrumentation.getTargetContext();
    private final List<Activity> started = new ArrayList<>();

    @Before
    public void prepareTheSettingsFileAndAForecast() throws IOException {
        byte[] example = bytes(instrumentation.getContext().getAssets().open("example-engine.json"));
        try (OutputStream out = new FileOutputStream(new File(app.getFilesDir(), "engine.json"))) {
            out.write(example);
        }
        File forecasts = new File(app.getFilesDir(), "forecasts");
        if (!forecasts.isDirectory() && !forecasts.mkdirs()) {
            throw new IOException("cannot make " + forecasts);
        }
        try (OutputStream out = new FileOutputStream(new File(forecasts, "forecast-open-meteo.json"))) {
            out.write(forecast().getBytes(StandardCharsets.UTF_8));
        }
    }

    /** Finishes the started activities and waits until they are destroyed, before the next test writes its files. */
    @After
    public void finishTheActivity() {
        for (Activity activity : started) {
            instrumentation.runOnMainSync(activity::finish);
        }
        long deadline = SystemClock.uptimeMillis() + TimeUnit.SECONDS.toMillis(10);
        for (Activity activity : started) {
            boolean[] destroyed = new boolean[1];
            do {
                instrumentation.runOnMainSync(() -> destroyed[0] = activity.isDestroyed());
            } while (!destroyed[0] && SystemClock.uptimeMillis() < deadline && sleep());
            assertTrue("the activity is destroyed", destroyed[0]);
        }
        started.clear();
    }

    /**
     * slf4j finds the app's provider, whose logger keeps its name, and a stack trace through the logger names its class,
     * method, source file and the line of the source that runs there: the logger writes the exception's trace, which
     * asks the exception's {@code toString}. The test APK carries the logger's source, so the line is read, not
     * written into the test. Only the logger's calls the app makes itself are left, such as a warning with an
     * exception.
     */
    @Test
    public void slf4jUsesTheAppsLoggerWhoseFramesKeepTheirFileAndLine() throws IOException {
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
        assertNotNull("the logger wrote the exception", trace[0]);
        StackTraceElement frame = null;
        for (StackTraceElement element : trace[0]) {
            if (element.getClassName().equals(logger)) {
                frame = element;
                break;
            }
        }
        assertNotNull("a frame of the logger in " + Arrays.toString(trace[0]), frame);
        assertEquals("handleNormalizedLoggingCall", frame.getMethodName());
        assertEquals("LogcatLogger.java", frame.getFileName());
        String[] source = text(instrumentation.getContext().getAssets().open("LogcatLogger.java")).split("\n", -1);
        int line = frame.getLineNumber();
        assertTrue("line " + line + " of " + source.length, line >= 1 && line <= source.length);
        assertTrue("the trace is written on line " + line + ": " + source[line - 1],
                source[line - 1].contains("printStackTrace"));
    }

    /** The plan's note on the battery level, which only a made plan shows, is read from the screen. */
    @Test
    public void theAppStartsOnTheSettingsFileAndShowsAPlan() {
        String shown = prefix("day_battery_assumed");

        start();

        waitFor("the plan's note", node -> has(node.getText(), shown));
    }

    /**
     * A made-up API key typed into Settings, Forecast and saved is encrypted with a key of the Android Keystore into
     * the keychain's file, shows again after the activity is made anew, and never reaches the app's Logcat lines. In
     * the .minified installation, so the app's own Keystore alias there is no one's real key. The key shown again
     * comes from the engine's memory of the save: only a new process makes the app decrypt it anew.
     */
    @Test
    public void anApiKeySavedInSettingsIsKeptThroughTheKeystore() throws Exception {
        String key = "MADE-UP-MINIFIED-" + Long.toHexString(System.nanoTime());
        start();
        waitFor("the plan's note", node -> has(node.getText(), prefix("day_battery_assumed")));

        openTheApiKeyOfOpenMeteo();
        AccessibilityNodeInfo field = waitFor("the API key's field", this::isTheApiKeyOfOpenMeteo);
        Bundle typed = new Bundle();
        typed.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, key);
        assertTrue("the key is typed", field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, typed));
        click(waitFor("Save", node -> same(node.getText(), text("action_save")) && tappable(node) != null));
        waitFor("the saved message", node -> same(node.getText(), text("settings_saved")));

        String stored = text(new FileInputStream(new File(app.getNoBackupFilesDir(), "keychain.json")));
        assertTrue("the keychain's file names the entry: " + stored, stored.contains("forecast/open-meteo/api_key"));
        assertFalse("the keychain's file does not hold the key itself", stored.contains(key));
        String alias = new JSONObject(stored).getString("key");
        assertTrue("the app's own alias: " + alias, APP_ALIAS.matcher(alias).matches());
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        assertTrue("the Android Keystore holds the app's key " + alias, keyStore.containsAlias(alias));

        finishTheActivity();
        start();
        waitFor("the plan's note", node -> has(node.getText(), prefix("day_battery_assumed")));
        openTheApiKeyOfOpenMeteo();
        click(waitFor("Show", node -> has(node.getContentDescription(), prefix("key_show_of"))
                && has(node.getContentDescription(), "Open-Meteo") && tappable(node) != null));
        waitFor("the key, shown", node -> isTheApiKeyOfOpenMeteo(node) && same(node.getText(), key));

        assertFalse("the app's Logcat lines do not hold the key", logcat().contains(key));
    }

    /**
     * The start of the activity with a usable settings file asks a round now, which WorkManager runs. Only the rounds
     * that end after a marker line count, written once the rounds of earlier starts have stopped ending; a round of an
     * earlier start that waits longer than that and ends after the marker would still count.
     */
    @Test
    public void aRoundRunsOnWorkManager() throws IOException {
        String marker = "rounds counted from here " + System.nanoTime();
        int settled = roundsDone(null);
        long quiet = SystemClock.uptimeMillis() + TimeUnit.SECONDS.toMillis(5);
        long settling = SystemClock.uptimeMillis() + TimeUnit.SECONDS.toMillis(30);
        while (SystemClock.uptimeMillis() < quiet && SystemClock.uptimeMillis() < settling && sleep()) {
            int now = roundsDone(null);
            if (now != settled) {
                settled = now;
                quiet = SystemClock.uptimeMillis() + TimeUnit.SECONDS.toMillis(5);
            }
        }
        Log.i(TAG, marker);

        start();

        long deadline = SystemClock.uptimeMillis() + TimeUnit.SECONDS.toMillis(60);
        int done = roundsDone(marker);
        while (done == 0 && SystemClock.uptimeMillis() < deadline && sleep()) {
            done = roundsDone(marker);
        }
        assertTrue("a round ended as a success after the start", done > 0);
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

    /** The text of the app's resource [name] up to its first placeholder. */
    private String prefix(String name) {
        String whole = text(name);
        int placeholder = whole.indexOf('%');
        return placeholder < 0 ? whole : whole.substring(0, placeholder);
    }

    /** From the dashboard: Settings, its Forecast page, and the fold of Open-Meteo's API key and server. */
    private void openTheApiKeyOfOpenMeteo() {
        click(waitFor("Settings", node -> same(node.getContentDescription(), text("home_settings"))
                && tappable(node) != null));
        click(waitFor("the Forecast row", node -> same(node.getText(), text("settings_forecast"))
                && tappable(node) != null));
        AccessibilityNodeInfo fold = waitFor("the fold of the API key and server",
                node -> same(node.getText(), text("provider_fold_key_and_server")) && tappable(node) != null);
        // The fold shows closed until the stored keys are read, and opens then if a key is stored, so it is clicked
        // only once they are read and only when no field of the key shows
        String reading = text("keys_reading");
        long deadline = SystemClock.uptimeMillis() + TimeUnit.SECONDS.toMillis(30);
        while (onScreen(node -> same(node.getText(), reading))) {
            assertTrue("the API keys are still being read", SystemClock.uptimeMillis() < deadline && sleep());
        }
        if (!onScreen(this::isTheApiKeyOfOpenMeteo)) {
            click(fold);
        }
        waitFor("the API key's field", this::isTheApiKeyOfOpenMeteo);
    }

    /** Whether [node] is the field of Open-Meteo's API key: Compose puts the field's label inside it. */
    private boolean isTheApiKeyOfOpenMeteo(AccessibilityNodeInfo node) {
        if (!node.isEditable()) {
            return false;
        }
        String label = String.format(text("provider_setting_of"), text("provider_api_key"), "Open-Meteo");
        List<AccessibilityNodeInfo> inside = new ArrayList<>();
        collect(node, inside);
        for (AccessibilityNodeInfo part : inside) {
            if (same(part.getText(), label)) {
                return true;
            }
        }
        return false;
    }

    private static boolean has(CharSequence text, String part) {
        return text != null && text.toString().contains(part);
    }

    private static boolean same(CharSequence text, String whole) {
        return text != null && text.toString().equals(whole);
    }

    /**
     * The first node of the active window's accessibility tree that [wanted] accepts, once there is one; scrolls the
     * window's lists forward while it looks, and fails after 30 seconds, showing the tree.
     */
    private AccessibilityNodeInfo waitFor(String what, Predicate<AccessibilityNodeInfo> wanted) {
        long deadline = SystemClock.uptimeMillis() + TimeUnit.SECONDS.toMillis(30);
        StringBuilder tree = new StringBuilder();
        for (int look = 0; SystemClock.uptimeMillis() < deadline; look++) {
            AccessibilityNodeInfo root = instrumentation.getUiAutomation().getRootInActiveWindow();
            if (root != null) {
                List<AccessibilityNodeInfo> nodes = new ArrayList<>();
                collect(root, nodes);
                for (AccessibilityNodeInfo node : nodes) {
                    if (wanted.test(node)) {
                        return node;
                    }
                }
                tree.setLength(0);
                for (AccessibilityNodeInfo node : nodes) {
                    tree.append("\n").append(node.getClassName()).append(" text=").append(node.getText())
                            .append(" description=").append(node.getContentDescription())
                            .append(" hint=").append(node.getHintText());
                    // only after a second of looks, so a node that is just being drawn is not scrolled away
                    if (look >= 4 && node.isScrollable()) {
                        node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
                    }
                }
            }
            sleep();
        }
        throw new AssertionError("no " + what + " on the screen:" + tree);
    }

    private static void collect(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> into) {
        into.add(node);
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                collect(child, into);
            }
        }
    }

    /** Whether a node of the active window's accessibility tree is one [wanted] accepts, now. */
    private boolean onScreen(Predicate<AccessibilityNodeInfo> wanted) {
        AccessibilityNodeInfo root = instrumentation.getUiAutomation().getRootInActiveWindow();
        List<AccessibilityNodeInfo> nodes = new ArrayList<>();
        if (root != null) {
            collect(root, nodes);
        }
        for (AccessibilityNodeInfo node : nodes) {
            if (wanted.test(node)) {
                return true;
            }
        }
        return false;
    }

    /** [node], or the nearest node above it that can be clicked; null when there is none. */
    private static AccessibilityNodeInfo tappable(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo target = node;
        while (target != null && !target.isClickable()) {
            target = target.getParent();
        }
        return target;
    }

    /** Clicks [node], or the nearest node above it that can be clicked, as a tap there would. */
    private static void click(AccessibilityNodeInfo node) {
        assertTrue("clicked " + node.getText(), tappable(node).performAction(AccessibilityNodeInfo.ACTION_CLICK));
    }

    /** Waits a quarter of a second before the next look, not for something to happen; always true, for a loop. */
    private static boolean sleep() {
        SystemClock.sleep(250);
        return true;
    }

    /** All of [stream]'s bytes; closes it. */
    private static byte[] bytes(InputStream stream) throws IOException {
        try (InputStream in = stream) {
            ByteArrayOutputStream all = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            for (int read = in.read(buffer); read >= 0; read = in.read(buffer)) {
                all.write(buffer, 0, read);
            }
            return all.toByteArray();
        }
    }

    /** All of [stream] as UTF-8 text; closes it. */
    private static String text(InputStream stream) throws IOException {
        return new String(bytes(stream), StandardCharsets.UTF_8);
    }

    /** The app's own Logcat lines so far, as Android shows an app only its own. */
    private static String logcat() throws IOException {
        Process logcat = new ProcessBuilder("logcat", "-d", "-v", "tag").redirectErrorStream(true).start();
        return text(logcat.getInputStream());
    }

    /**
     * How many of the round's works ended as a success, by WorkManager's own Logcat line for each: after the line
     * [marker], or in the whole buffer when it is null. Fails when the marker is not in the buffer.
     */
    private static int roundsDone(String marker) throws IOException {
        String[] lines = logcat().split("\n");
        int from = 0;
        if (marker != null) {
            from = -1;
            for (int i = 0; i < lines.length && from < 0; i++) {
                if (lines[i].contains(marker)) {
                    from = i + 1;
                }
            }
            assertTrue("the marker line is in the Logcat buffer: " + marker, from >= 0);
        }
        int done = 0;
        for (int i = from; i < lines.length; i++) {
            if (lines[i].contains("Worker result SUCCESS for") && lines[i].contains(ROUND_WORKER)) {
                done++;
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

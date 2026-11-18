package nl.enadeag.carchargeplanner.android.minified;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

/**
 * Runs the minified suite with JUnit 4 alone and reports each test to {@code am instrument -r} in the form AndroidX
 * Test's runner uses, so Gradle reads the results as usual. AndroidX Test's runner cannot run here: it calls
 * androidx.tracing and Kotlin's library, which the test APK leaves to the app, as the app has them, and R8 has removed
 * from the app what the app itself does not use.
 */
public final class MinifiedTestRunner extends Instrumentation {

    private static final Class<?>[] SUITE = {MinifiedAppTest.class};

    // the status codes and keys of am instrument's protocol, as android.test.InstrumentationTestRunner sends them
    private static final int START = 1;
    private static final int OK = 0;
    private static final int FAILURE = -2;
    private static final int IGNORED = -3;
    private static final int ASSUMPTION_FAILURE = -4;

    /** The one runner of the process, for the tests; set before they run. */
    private static volatile Instrumentation running;

    /** Makes the runner; Android calls it. */
    public MinifiedTestRunner() {
    }

    /** The instrumentation that runs the tests, as AndroidX Test's {@code InstrumentationRegistry} would give it. */
    static Instrumentation instrumentation() {
        return running;
    }

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        running = this;
        start();
    }

    @Override
    public void onStart() {
        var core = new JUnitCore();
        core.addListener(new Reporter());
        Result result = core.run(SUITE);
        var summary = new Bundle();
        summary.putString(REPORT_KEY_STREAMRESULT, "\nTests run: " + result.getRunCount() + ", failures: "
                + result.getFailureCount() + "\n");
        finish(Activity.RESULT_OK, summary);
    }

    private final class Reporter extends RunListener {

        private int count;
        private int current;
        private Bundle status;
        private int code;

        @Override
        public void testRunStarted(Description description) {
            count = description.testCount();
        }

        @Override
        public void testStarted(Description description) {
            current++;
            status = new Bundle();
            status.putString("id", "AndroidJUnitRunner");
            status.putString("class", description.getClassName());
            var method = description.getMethodName();
            status.putString("test", method == null ? "classSetUp" : method);
            status.putInt("numtests", count);
            status.putInt("current", current);
            code = OK;
            sendStatus(START, new Bundle(status));
        }

        @Override
        public void testFailure(Failure failure) {
            // a failure outside a test, such as the class's, is reported as a test of its own
            var outside = status == null;
            if (outside) {
                testStarted(failure.getDescription());
            }
            code = FAILURE;
            status.putString("stack", failure.getTrace());
            status.putString(REPORT_KEY_STREAMRESULT, "\nError in " + status.getString("test") + ":\n"
                    + failure.getTrace());
            if (outside) {
                testFinished(failure.getDescription());
            }
        }

        @Override
        public void testAssumptionFailure(Failure failure) {
            code = ASSUMPTION_FAILURE;
            status.putString("stack", failure.getTrace());
        }

        @Override
        public void testIgnored(Description description) {
            testStarted(description);
            code = IGNORED;
            testFinished(description);
        }

        @Override
        public void testFinished(Description description) {
            sendStatus(code, status);
            status = null;
        }
    }
}

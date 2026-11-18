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
            // a failure outside a test, before or after the class's tests, has no method: a neutral name of its own
            status.putString("test", method == null ? "outsideATest" : method);
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
            // a second failure of the test, such as its @After's, follows the first
            var stack = (code == FAILURE ? status.getString("stack") + "\nThen:\n" : "") + failure.getTrace();
            code = FAILURE;
            status.putString("stack", stack);
            status.putString(REPORT_KEY_STREAMRESULT, "\nError in " + status.getString("test") + ":\n" + stack);
            if (outside) {
                testFinished(failure.getDescription());
            }
        }

        // the suite skips nothing: a test that does not check what it should fails
        @Override
        public void testAssumptionFailure(Failure failure) {
            testFailure(failure);
        }

        @Override
        public void testIgnored(Description description) {
            testFailure(new Failure(description, new AssertionError("the minified suite ignores no test")));
        }

        @Override
        public void testFinished(Description description) {
            sendStatus(code, status);
            status = null;
        }
    }
}

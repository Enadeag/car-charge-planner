package nl.enadeag.carchargeplanner.platform.linux;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import org.junit.jupiter.api.Assumptions;

/**
 * Starts the programs of the system that the Linux tests need, such as dbus-daemon. Without the program the test is
 * skipped, so the build passes on a machine that lacks it; with {@code CCP_REQUIRE_PLATFORM=1}, as in CI, the test
 * fails instead, so a missing package cannot turn these tests into silent skips.
 */
public final class SystemPrograms {

    private SystemPrograms() {
    }

    public static Process start(ProcessBuilder program) {
        try {
            return program.start();
        } catch (IOException e) {
            var missing = program.command().getFirst() + " is not installed";
            if ("1".equals(System.getenv("CCP_REQUIRE_PLATFORM"))) {
                return fail(missing + ", and CCP_REQUIRE_PLATFORM=1 requires it", e);
            }
            return Assumptions.abort(missing);
        }
    }
}

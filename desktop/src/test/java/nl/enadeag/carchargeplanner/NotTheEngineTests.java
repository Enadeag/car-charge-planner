package nl.enadeag.carchargeplanner;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.Location;

/**
 * Leaves the engine's test classes out of the desktop's ArchUnit tests. They come on the class path for their test
 * helpers (the engine's test-jar), as a jar ArchUnit does not see as tests; the engine module checks them itself.
 */
final class NotTheEngineTests implements ImportOption {

    @Override
    public boolean includes(Location location) {
        return !location.contains("/engine/target/test-classes/")
                && !(location.contains("car-charge-planner-engine") && location.contains("-tests.jar"));
    }
}

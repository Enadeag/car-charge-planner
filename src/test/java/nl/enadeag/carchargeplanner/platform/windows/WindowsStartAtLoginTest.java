package nl.enadeag.carchargeplanner.platform.windows;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WindowsStartAtLoginTest {

    private static final Path PROGRAM =
            Path.of("C:\\Users\\Some One\\AppData\\Local\\Programs\\car-charge-planner\\car-charge-planner.exe");

    /** Stands in for the real Run key, which no test may change. */
    private static final class MemoryRunKey implements RunKey {
        final Map<String, String> values = new HashMap<>();

        @Override
        public Optional<String> get(String name) {
            return Optional.ofNullable(values.get(name));
        }

        @Override
        public void set(String name, String commandLine) {
            values.put(name, commandLine);
        }

        @Override
        public void delete(String name) {
            values.remove(name);
        }
    }

    private final MemoryRunKey runKey = new MemoryRunKey();
    private final WindowsStartAtLogin startAtLogin = new WindowsStartAtLogin(runKey, "Car charge planner", PROGRAM);

    @Test
    void switchedOnTheRunKeyStartsTheQuotedLauncherHidden() throws Exception {
        startAtLogin.switchOn();
        assertThat(runKey.values).containsExactly(Map.entry("Car charge planner",
                "\"C:\\Users\\Some One\\AppData\\Local\\Programs\\car-charge-planner\\car-charge-planner.exe\""
                        + " --background"));
    }

    @Test
    void theStateFollowsTheRunKeyAndOtherProgramsAreLeftAlone() throws Exception {
        runKey.values.put("Other program", "other.exe");
        assertThat(startAtLogin.isOn()).isFalse();
        startAtLogin.switchOn();
        assertThat(startAtLogin.isOn()).isTrue();
        startAtLogin.switchOff();
        assertThat(startAtLogin.isOn()).isFalse();
        assertThat(runKey.values).containsExactly(Map.entry("Other program", "other.exe"));
    }
}

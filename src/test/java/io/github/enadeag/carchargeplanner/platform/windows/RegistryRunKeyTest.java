package io.github.enadeag.carchargeplanner.platform.windows;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** On a key of its own below HKEY_CURRENT_USER\Software: the real Run key is never touched by a test. */
@EnabledOnOs(OS.WINDOWS)
class RegistryRunKeyTest {

    private final String key = "Software\\car-charge-planner-test-" + UUID.randomUUID();
    private final RegistryRunKey runKey = new RegistryRunKey(key);

    @AfterEach
    void removeTheKey() throws Exception {
        new ProcessBuilder("reg.exe", "delete", "HKCU\\" + key, "/f").inheritIO().start().waitFor(30, TimeUnit.SECONDS);
    }

    @Test
    void aValueIsWrittenReadAndRemoved() throws Exception {
        var commandLine = "\"C:\\Program Files\\Ünïcode\\app.exe\" --background";
        assertThat(runKey.get("Car charge planner")).isEmpty();
        runKey.set("Car charge planner", commandLine);
        assertThat(runKey.get("Car charge planner")).contains(commandLine);
        runKey.delete("Car charge planner");
        assertThat(runKey.get("Car charge planner")).isEmpty();
        runKey.delete("Car charge planner");
    }
}

package nl.enadeag.carchargeplanner.platform.windows;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.desktop.system.ClockFormat;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * On a key of its own below HKEY_CURRENT_USER\Software: the user's own time format is only read, by the last test,
 * never written.
 */
@EnabledOnOs(OS.WINDOWS)
class RegistryClockTest {

    private static final Pattern SHORT_TIME = Pattern.compile("sShortTime\\s+REG_SZ\\s+(.*)");
    private static final Pattern TIME = Pattern.compile("iTime\\s+REG_SZ\\s+(.*)");

    private final String key = "Software\\car-charge-planner-test-" + UUID.randomUUID();
    private final BlockingQueue<ClockFormat> clocks = new LinkedBlockingQueue<>();

    @AfterEach
    void removeTheKey() throws Exception {
        reg("delete", "HKCU\\" + key, "/f");
    }

    private static String reg(String... arguments) throws IOException, InterruptedException {
        var command = new ArrayList<>(List.of("reg.exe"));
        command.addAll(List.of(arguments));
        var process = new ProcessBuilder(command).redirectErrorStream(true).start();
        var output = new String(process.getInputStream().readAllBytes(), Charset.defaultCharset());
        process.waitFor(30, TimeUnit.SECONDS);
        return output;
    }

    private void write(String name, String value) throws Exception {
        reg("add", "HKCU\\" + key, "/v", name, "/t", "REG_SZ", "/d", value, "/f");
    }

    /** The next clock the setting gives, or null when none came within a while. */
    private ClockFormat next() throws InterruptedException {
        return clocks.poll(10, TimeUnit.SECONDS);
    }

    @Test
    void withoutTheKeyTheClockIsTheLanguages() {
        new RegistryClock(key).follow(clocks::add);

        assertThat(clocks).containsExactly(ClockFormat.LANGUAGE);
    }

    @Test
    void theShortTimeFormatIsReadAtOnce() throws Exception {
        write(RegistryClock.SHORT_TIME, "h:mm tt");
        write(RegistryClock.TIME, "1");

        new RegistryClock(key).follow(clocks::add);

        assertThat(clocks.poll()).isEqualTo(ClockFormat.TWELVE_HOUR);
    }

    @Test
    void withoutTheShortTimeFormatITimeIsRead() throws Exception {
        write(RegistryClock.TIME, "1");

        new RegistryClock(key).follow(clocks::add);

        assertThat(clocks.poll()).isEqualTo(ClockFormat.TWENTY_FOUR_HOUR);
    }

    @Test
    void aChangeWhileFollowingIsGiven() throws Exception {
        write(RegistryClock.SHORT_TIME, "HH:mm");
        new RegistryClock(key).follow(clocks::add);
        assertThat(next()).isEqualTo(ClockFormat.TWENTY_FOUR_HOUR);

        write(RegistryClock.SHORT_TIME, "h:mm tt");
        assertThat(next()).isEqualTo(ClockFormat.TWELVE_HOUR);
        reg("delete", "HKCU\\" + key, "/v", RegistryClock.SHORT_TIME, "/f");
        assertThat(next()).isEqualTo(ClockFormat.LANGUAGE);
    }

    @Test
    void theUsersOwnSettingIsRead() throws Exception {
        var shortTime = SHORT_TIME.matcher(reg("query", "HKCU\\" + RegistryClock.INTERNATIONAL_KEY, "/v",
                RegistryClock.SHORT_TIME));
        var time = TIME.matcher(reg("query", "HKCU\\" + RegistryClock.INTERNATIONAL_KEY, "/v", RegistryClock.TIME));
        var expected = RegistryClock.of(shortTime.find() ? shortTime.group(1).strip() : "",
                time.find() ? time.group(1).strip() : "");

        new RegistryClock(RegistryClock.INTERNATIONAL_KEY).follow(clocks::add);

        assertThat(clocks.poll()).isEqualTo(expected);
    }
}

package nl.enadeag.carchargeplanner.platform.windows;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.desktop.system.TextSize;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * On a key of its own below HKEY_CURRENT_USER\Software: the user's own text size is only read, by the last test,
 * never written.
 */
@EnabledOnOs(OS.WINDOWS)
class RegistryTextSizeTest {

    private static final Pattern NUMBER = Pattern.compile("TextScaleFactor\\s+REG_DWORD\\s+0x([0-9a-fA-F]+)");

    private final String key = "Software\\car-charge-planner-test-" + UUID.randomUUID();
    private final BlockingQueue<TextSize> sizes = new LinkedBlockingQueue<>();

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

    private void write(int percent) throws Exception {
        reg("add", "HKCU\\" + key, "/v", RegistryTextSize.VALUE, "/t", "REG_DWORD", "/d", String.valueOf(percent),
                "/f");
    }

    /** The next size the setting gives, or null when none came within a while. */
    private TextSize next() throws InterruptedException {
        return sizes.poll(10, TimeUnit.SECONDS);
    }

    @Test
    void withoutTheKeyTheTextIsNormal() throws Exception {
        new RegistryTextSize(key).follow(sizes::add);

        assertThat(sizes).containsExactly(TextSize.NORMAL);
    }

    @Test
    void withoutTheValueTheTextIsNormal() throws Exception {
        reg("add", "HKCU\\" + key, "/f");

        new RegistryTextSize(key).follow(sizes::add);

        assertThat(next()).isEqualTo(TextSize.NORMAL);
    }

    @ParameterizedTest
    @CsvSource({"100, 100", "150, 150", "225, 225", "300, 225", "50, 100"})
    void theStoredSizeIsGivenAtOnce(int stored, int percent) throws Exception {
        write(stored);

        new RegistryTextSize(key).follow(sizes::add);

        assertThat(sizes.poll()).isEqualTo(new TextSize(percent));
    }

    @Test
    void aChangeWhileFollowingIsGiven() throws Exception {
        write(125);
        new RegistryTextSize(key).follow(sizes::add);
        assertThat(next()).isEqualTo(new TextSize(125));

        write(175);
        assertThat(next()).isEqualTo(new TextSize(175));
        reg("delete", "HKCU\\" + key, "/v", RegistryTextSize.VALUE, "/f");
        assertThat(next()).isEqualTo(TextSize.NORMAL);
    }

    @Test
    void theUsersOwnSettingIsRead() throws Exception {
        var query = NUMBER.matcher(reg("query", "HKCU\\" + RegistryTextSize.ACCESSIBILITY_KEY, "/v",
                RegistryTextSize.VALUE));
        var expected = query.find() ? TextSize.clamped(Integer.parseInt(query.group(1), 16)) : TextSize.NORMAL;

        new RegistryTextSize(RegistryTextSize.ACCESSIBILITY_KEY).follow(sizes::add);

        assertThat(sizes.poll()).isEqualTo(expected);
    }
}

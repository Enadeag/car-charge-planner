package nl.enadeag.carchargeplanner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import nl.enadeag.carchargeplanner.CommandLine.Command;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CommandLineTest {

    @Test
    void withoutArgumentsTheAppRunsInTheForeground() {
        assertThat(CommandLine.parse()).isEqualTo(new CommandLine(Command.RUN, Optional.empty(), false));
    }

    @Test
    void theOptionsAreReadWhereverTheyStand() {
        assertThat(CommandLine.parse("--background", "run", "--config=/x/engine.json", "--unknown"))
                .isEqualTo(new CommandLine(Command.RUN, Optional.of(Path.of("/x/engine.json")), true));
    }

    @ParameterizedTest
    @CsvSource({"quit, QUIT", "test-notification, TEST_NOTIFICATION", "start-at-login on, START_AT_LOGIN_ON",
            "start-at-login off, START_AT_LOGIN_OFF", "install-launcher, INSTALL_LAUNCHER",
            "uninstall-launcher, UNINSTALL_LAUNCHER"})
    void theWordsNameTheCommand(String words, Command command) {
        assertThat(CommandLine.parse(words.split(" ")).command()).isEqualTo(command);
    }

    @ParameterizedTest
    @CsvSource({"restart", "start-at-login", "start-at-login maybe", "quit now"})
    void otherWordsAreRefused(String words) {
        assertThatIllegalArgumentException().isThrownBy(() -> CommandLine.parse(words.split(" ")));
    }
}

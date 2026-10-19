package nl.enadeag.carchargeplanner.engine.forecast;

import static org.assertj.core.api.Assertions.assertThat;

import nl.enadeag.carchargeplanner.engine.forecast.HttpGet.Reply;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HttpGetReplyTest {

    @ParameterizedTest
    @CsvSource({"199, false", "200, true", "299, true", "300, false", "404, false"})
    void onlyA2xxStatusIsOk(int status, boolean ok) {
        assertThat(new Reply(status, "").ok()).isEqualTo(ok);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"90|90", " 30 |30", "0|0", "999999999|999999999"})
    void aRetryAfterInSecondsIsTheWait(String header, long seconds) {
        assertThat(new Reply(429, "", Map.of("retry-after", header)).retryAfter()).contains(Duration.ofSeconds(seconds));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"soon", "-5", "1.5", "1234567890", "Wed, 21 Oct 2026 07:28:00 GMT"})
    void aRetryAfterThatIsNotSecondsIsIgnored(String header) {
        assertThat(new Reply(429, "", Map.of("Retry-After", header)).retryAfter()).isEqualTo(Optional.empty());
    }

    @Test
    void aHeaderIsFoundByItsOwnNameInAnyCase() {
        var reply = new Reply(200, "", Map.of("Content-Type", "text/plain", "Retry-After", "90"));

        assertThat(reply.header("retry-after")).contains("90");
        assertThat(reply.header("Content-Type")).contains("text/plain");
        assertThat(reply.header("X-Missing")).isEmpty();
    }
}

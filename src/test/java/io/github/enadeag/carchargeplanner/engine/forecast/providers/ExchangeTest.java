package io.github.enadeag.carchargeplanner.engine.forecast.providers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.enadeag.carchargeplanner.engine.forecast.ByteSize;
import io.github.enadeag.carchargeplanner.engine.forecast.HttpGet;
import io.github.enadeag.carchargeplanner.engine.forecast.ProviderError;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExchangeTest {

    @Test
    void anAnswerOfJsonWithinTheLimitsIsRead() {
        var body = "[" + "0,".repeat((int) Exchange.MAX_TOKENS / 2 - 2) + "0]";
        assertThat(Exchange.json(body).size()).isEqualTo((int) Exchange.MAX_TOKENS / 2 - 1);
    }

    @Test
    void anAnswerWithTooManyTokensIsNotJson() {
        var body = "[" + "0,".repeat((int) Exchange.MAX_TOKENS) + "0]";
        assertThat(body.length()).isLessThan(Exchange.MAX_DOCUMENT_LENGTH);
        assertThat(Exchange.json(body).isMissingNode()).isTrue();
    }

    @Test
    void anAnswerLongerThanTheLimitIsNotJson() {
        var body = "{\"a\": \"" + "x".repeat(Exchange.MAX_DOCUMENT_LENGTH) + "\"}";
        assertThat(Exchange.json(body).isMissingNode()).isTrue();
    }

    @Test
    void anAnswerTooLargeToReadIsUnavailableAndSaysNothingOfTheAddress() {
        var request = new HttpGet.Request(URI.create("https://example.org/KEY123/estimate"), Map.of(),
                Duration.ofSeconds(1), ByteSize.ofKilobytes(256));
        HttpGet tooLarge = sent -> {
            throw new HttpGet.TooLarge(sent.maxBody());
        };

        var error = catchThrowableOfType(ProviderError.Unavailable.class,
                () -> Exchange.send(tooLarge, request, "Example"));

        assertThat(error).hasMessage("Example gave an answer larger than 262144 bytes");
    }
}

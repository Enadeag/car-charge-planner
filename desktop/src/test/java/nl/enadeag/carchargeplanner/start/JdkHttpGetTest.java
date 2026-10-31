package nl.enadeag.carchargeplanner.start;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nl.enadeag.carchargeplanner.engine.forecast.HttpGet;
import nl.enadeag.carchargeplanner.engine.forecast.HttpGetContractTest;
import java.io.IOException;
import java.net.ConnectException;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Java's HTTP client against the contract every {@link HttpGet} meets, and what it does beyond that. */
class JdkHttpGetTest extends HttpGetContractTest {

    private final JdkHttpGet http = new JdkHttpGet();

    @Override
    protected HttpGet http() {
        return http;
    }

    @AfterEach
    void closeTheClient() {
        http.close();
    }

    @Test
    void theUserAgentAlsoNamesTheHomePage() throws Exception {
        get("/refusal", Duration.ofSeconds(5));

        assertThat(receivedHeaders().getFirst("User-Agent")).contains("https://github.com/Enadeag/car-charge-planner");
    }

    @Test
    void anErrorIsRethrownAsItIs() {
        var error = new StackOverflowError("in the client's own thread");

        assertThatThrownBy(() -> JdkHttpGet.failure(error)).isSameAs(error);
    }

    @Test
    void anIOExceptionIsPassedOnAsItIs() {
        var refused = new ConnectException("refused");

        assertThat(JdkHttpGet.failure(refused)).isSameAs(refused);
    }

    @Test
    void anyOtherFailureBecomesAnIOException() {
        var bug = new IllegalStateException("a bug in the client");

        assertThat(JdkHttpGet.failure(bug)).isExactlyInstanceOf(IOException.class).hasCause(bug);
    }

    @Test
    void aTooLargeWrappedInAnotherFailureIsFoundInItsCauses() {
        var tooLarge = new HttpGet.TooLarge(SMALL_LIMIT);

        assertThat(JdkHttpGet.failure(new IOException("wrapped", new IllegalStateException(tooLarge))))
                .isSameAs(tooLarge);
    }
}

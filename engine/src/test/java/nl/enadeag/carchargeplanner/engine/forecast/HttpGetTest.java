package nl.enadeag.carchargeplanner.engine.forecast;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HttpGetTest {

    @Test
    void theUserAgentNamesTheAppAndItsHomePage() {
        assertThat(HttpGet.USER_AGENT).startsWith("car-charge-planner ")
                .contains("https://github.com/Enadeag/car-charge-planner");
    }
}

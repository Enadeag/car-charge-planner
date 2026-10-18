package io.github.enadeag.carchargeplanner.engine.forecast.providers;

import static io.github.enadeag.carchargeplanner.engine.forecast.ReplayHttp.Route.answer;

import io.github.enadeag.carchargeplanner.engine.forecast.HttpGet.Reply;
import io.github.enadeag.carchargeplanner.engine.forecast.ProviderCase;
import io.github.enadeag.carchargeplanner.engine.forecast.ProviderConformance;
import io.github.enadeag.carchargeplanner.engine.forecast.Recorded;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

class ExampleSolarTest extends ProviderConformance {

    private static final String RECORDED = Recorded.answer("example_solar.json");

    @Override
    protected ProviderCase providerCase() {
        return new ProviderCase(ExampleSolar::new,
                List.of(answer("/v1/solar", new Reply(200, RECORDED))),
                List.of(answer("", new Reply(503, "Service Unavailable"))),
                List.of(answer("", new Reply(200, "<html>maintenance</html>"))),
                List.of(answer("", new Reply(200, "{}"))),
                List.of(answer("", new Reply(429, "", Map.of("Retry-After", "120")))),
                Duration.ofSeconds(120),
                Map.of(),
                Map.of("api_key", "EXAMPLE-SECRET-KEY-123"),
                Instant.parse("2026-07-03T07:00:00Z"),
                text -> List.of(answer("", new Reply(400,
                        new JsonMapper().createObjectNode().put("error", text).toString()))));
    }
}

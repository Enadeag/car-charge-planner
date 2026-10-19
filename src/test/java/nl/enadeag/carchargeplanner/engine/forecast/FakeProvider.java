package nl.enadeag.carchargeplanner.engine.forecast;

import nl.enadeag.carchargeplanner.engine.config.ProviderId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A scripted forecast provider: each call gives the next answer of the script, and the last answer repeats. It keeps
 * what each call was given.
 */
public final class FakeProvider implements ForecastProvider {

    /** One answer: steps, or a thrown error. */
    @FunctionalInterface
    public interface Answer {
        List<ForecastStep> give() throws ProviderError;
    }

    public record Call(SystemDescription system, Map<String, String> settings, Instant now) {
    }

    public final List<Call> calls = new CopyOnWriteArrayList<>();
    private final ProviderInfo info;
    private volatile List<Answer> script;

    public FakeProvider(ProviderInfo info, Answer... script) {
        this.info = info;
        this.script = List.of(script);
    }

    /** A provider that asks for no settings, is refreshed after an hour and useful for six. */
    public FakeProvider(String id, String name, Answer... script) {
        this(info(id, name, List.of()), script);
    }

    public static ProviderInfo info(String id, String name, List<SettingSpec> settings) {
        return new ProviderInfo(new ProviderId(id), name, "A scripted provider for tests.", "https://example.test/terms",
                "Forecast by " + name, "Nothing: it is a fake.", Duration.ofHours(1), Duration.ofDays(2),
                Duration.ofHours(6), settings);
    }

    public static Answer steps(List<ForecastStep> steps) {
        return () -> steps;
    }

    public static Answer error(ProviderError error) {
        return () -> {
            throw error;
        };
    }

    public static Answer crash(RuntimeException bug) {
        return () -> {
            throw bug;
        };
    }

    /** Replaces the script; the calls so far are kept. */
    public void script(Answer... answers) {
        script = List.of(answers);
    }

    public int callCount() {
        return calls.size();
    }

    @Override
    public ProviderInfo info() {
        return info;
    }

    @Override
    public List<ForecastStep> fetch(SystemDescription system, Map<String, String> settings, Instant now)
            throws ProviderError {
        calls.add(new Call(system, Map.copyOf(settings), now));
        var current = script;
        return current.get(Math.min(calls.size(), current.size()) - 1).give();
    }
}

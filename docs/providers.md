# Forecast providers

A forecast provider tells the app how much power the user's solar system is expected to produce over the next
days. This guide is a stub: ticket P10 completes it (the interface in full, a skeleton provider, fixtures and
attribution notices, rate limits and terms, secrets, translations). What is here is what a contributor needs to
test a provider.

## Testing a provider: the conformance kit

Every provider is run through the same checks, against recorded answers and without touching the network.
The kit is `tests/provider_kit.py`; `tests/test_provider_kit.py` shows a complete small provider passing it.

A provider takes a **transport** (`forecast/transport.py`) when it is created and makes all its requests through
it: a `Request` (URL, headers) goes in, a `Reply` (status, body, headers) comes out. A server's refusal is a
`Reply` with that status; only "could not talk to the server at all" raises, as an `OSError`. In tests the
transport is a `ReplayTransport` that answers only what you recorded.

```python
class TestMyProvider(ProviderConformance):
    @pytest.fixture
    def case(self):
        return ProviderCase(
            make=lambda transport: MyProvider(transport),
            ok=[("api.example.com/forecast", Reply(200, RECORDED_ANSWER))],   # a real, recorded answer
            server_error=[("", Reply(503))],
            bad_body=[("", Reply(200, b"<html>"))],
            empty_body=[("", Reply(200, b"{}"))],
            rate_limited=[("", Reply(429, b"", {"Retry-After": "120"}))],     # when the provider has one
            retry_after=timedelta(seconds=120),
            secrets={"api_key": "SECRET-KEY-123"},                            # recognisable values, to find leaks
            now=datetime(2026, 7, 3, 7, 0, tzinfo=timezone.utc),              # when the answer was recorded
        )
```

What it checks:

| Check | What a provider must do |
| --- | --- |
| Metadata | A lower-case id, a name, description, attribution and a plain-words "what is sent", an `https` terms link, positive refresh interval and horizon, a useful age that is at least the refresh interval, unique setting keys with labels and hints. |
| A good answer | At least one step; steps carry a time zone, last a positive time, follow each other without overlap, have finite non-negative power, and stay within the declared horizon; it went through the transport and asked for nothing unrecorded. |
| Same answer, same steps | Deterministic; it leaves its inputs alone. |
| Rate limit | A refusal for too many requests raises `RateLimited` with the retry time the server gave (or `None`). |
| Server error, no network | A 5xx and a failed connection (`OSError`, timeout, reset) raise `Unavailable`. |
| Not as documented | A 200 with the wrong content raises `BadResponse`; an empty answer raises a `ProviderError` or gives valid steps; never a stray `KeyError` or `JSONDecodeError`. |
| Required settings | A missing required setting raises `NotConfigured` before anything is sent. |
| Secrets | A secret setting never appears in a log line or an error message. |

For tests of the code that *uses* providers (the forecast service, the Settings tab) there is a scripted
`FakeProvider` in `tests/fake_provider.py`: successes, errors, rate limits with retry times, and `Held` entries
that wait for an event so a test can have a fetch in flight.

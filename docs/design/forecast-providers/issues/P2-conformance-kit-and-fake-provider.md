# Conformance kit and fake provider

Status: done (not committed yet)
Type: task
Blocked by: P1

Spec section 8.

## Acceptance

- A reusable test suite that any provider can be run through: aware, ordered, non-overlapping steps; finite non-negative power; each documented error maps to the right `ProviderError`; complete metadata and settings; empty and malformed responses do not crash; never uses the network (a replay transport that fails the test on an unexpected request).
- A scripted fake provider (successes, failures, rate limits with retry times, delays) for the orchestration tests.
- The kit is documented in the provider guide stub and is used by P3 and P4.

## Comments

- `forecast/transport.py`: `Request`, `Reply`, `Transport`, and the real `urllib_transport` (an HTTP refusal is a `Reply`; only a failed connection raises `OSError`). Providers take a transport when created: that is what lets the kit replay recorded answers.
- `tests/provider_kit.py`: `ProviderCase`, `ReplayTransport` (fails on any request not recorded, and also keeps a list in case a provider swallowed the exception), the `check_*` functions and the `ProviderConformance` class to subclass. `tests/fake_provider.py`: `FakeProvider` with a script, `Held`, `steps_for`.
- `tests/test_provider_kit.py` runs a small real-looking provider through the kit and 9 broken variants (negative power, naive times, wrong error kind, crash on bad JSON, secret in an error, request before the settings check, a stray request, a swallowed stray request) that must each be caught, with the way each fails stated. Mutation-checked.
- Not in the kit, on purpose: "respects `min_refresh`" is the service's job and is tested in P6.
- `docs/providers.md` is the stub for the guide (P10 completes it).

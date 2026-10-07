"""The built-in forecast providers, found by id. Providers are added by pull request (see `docs/providers.md`)."""

from __future__ import annotations

from .provider import ForecastProvider


class ProviderRegistry:
    def __init__(self) -> None:
        self._providers: dict[str, ForecastProvider] = {}

    def register(self, provider: ForecastProvider) -> ForecastProvider:
        if not provider.id or provider.id != provider.id.strip().lower() or " " in provider.id:
            raise ValueError(f"a provider id is short, lower case and without spaces, not {provider.id!r}")
        if provider.id in self._providers:
            raise ValueError(f"a provider with the id {provider.id!r} is already registered")
        self._providers[provider.id] = provider
        return provider

    def unregister(self, provider_id: str) -> None:
        self._providers.pop(provider_id, None)

    def get(self, provider_id: str) -> ForecastProvider:
        try:
            return self._providers[provider_id]
        except KeyError:
            raise KeyError(f"unknown forecast provider {provider_id!r}") from None

    def ids(self) -> list[str]:
        return list(self._providers)

    def all(self) -> list[ForecastProvider]:
        return list(self._providers.values())

    def __contains__(self, provider_id: str) -> bool:
        return provider_id in self._providers


registry = ProviderRegistry()  # the built-in providers register themselves here (tickets P3 and P4)

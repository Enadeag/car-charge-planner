"""Which providers the config selects, and the settings they get: config.toml for the plain ones,
secrets.toml for the secret ones."""

from __future__ import annotations

from car_charge_planner.i18n import _
from car_charge_planner.planner.model import Config

from .provider import ForecastProvider, PlaceSearch
from .registry import ProviderRegistry
from .registry import registry as builtin
from .secret_store import Secrets


def provider_settings(cfg: Config, secrets: Secrets, provider: ForecastProvider) -> dict[str, str]:
    """What a provider's `fetch` is given: its declared settings only, secret ones from the secrets file."""
    plain = cfg.forecast.settings.get(provider.id, {})
    hidden = secrets.get(provider.id, {})
    values: dict[str, str] = {}
    for spec in provider.settings:
        value = (hidden if spec.secret else plain).get(spec.key, "")
        if value:
            values[spec.key] = value
    return values


def split_settings(provider: ForecastProvider, values: dict[str, str]) -> tuple[dict[str, str], dict[str, str]]:
    """(for config.toml, for secrets.toml): the secret settings go to the second, empty ones nowhere."""
    secret_keys = {s.key for s in provider.settings if s.secret}
    plain = {k: v for k, v in values.items() if v and k not in secret_keys}
    secret = {k: v for k, v in values.items() if v and k in secret_keys}
    return plain, secret


def place_search(cfg: Config, registry: ProviderRegistry = builtin) -> tuple[ForecastProvider, PlaceSearch] | None:
    """The primary provider and its place search, or None when it has none (or is not known): then the person
    types coordinates, and the page says why."""
    if cfg.forecast.primary not in registry:
        return None
    provider = registry.get(cfg.forecast.primary)
    return (provider, provider.search_places) if provider.search_places is not None else None


def check_selection(cfg: Config, secrets: Secrets, registry: ProviderRegistry = builtin) -> list[str]:
    """What is wrong with the selection, in words for the user: unknown providers, unknown settings, required
    settings that are missing. Empty when it is usable. (The config's own shape is checked by `validate`.)"""
    problems: list[str] = []
    chosen = [("primary", cfg.forecast.primary)] + ([("fallback", cfg.forecast.fallback)] if cfg.forecast.fallback else [])
    for role, provider_id in chosen:
        if provider_id not in registry:
            problems.append(_("The {role} forecast provider {id!r} is not known to this version of the app").format(role=role, id=provider_id))
            continue
        provider = registry.get(provider_id)
        declared = {s.key: s for s in provider.settings}
        for key in cfg.forecast.settings.get(provider_id, {}):
            if key not in declared:
                problems.append(_("{provider}: unknown setting {key!r}").format(provider=provider.name, key=key))
        values = provider_settings(cfg, secrets, provider)
        for spec in provider.settings:
            if not spec.optional and not values.get(spec.key):
                problems.append(_("{provider}: {setting} is required").format(provider=provider.name, setting=_(spec.label)))
    return problems

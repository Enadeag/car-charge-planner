"""Settings fields a forecast provider declares, built when they are needed (the provider is known at run time)."""

from __future__ import annotations

from car_charge_planner.forecast.provider import ForecastProvider
from car_charge_planner.i18n import N_

from .fields import FieldSpec


def provider_fields(provider: ForecastProvider) -> tuple[FieldSpec, ...]:
    """One field per declared setting. Keys are `forecast.<provider id>.<setting>`; a secret one is masked and kept
    out of the config file. Labels and hints are the provider's `N_` markers: the tab translates them when shown."""
    return tuple(
        FieldSpec(
            f"forecast.{provider.id}.{spec.key}",
            spec.label,
            spec.hint,
            kind="secret" if spec.secret else "text",
            flag="" if spec.optional else N_("Required"),
        )
        for spec in provider.settings
    )

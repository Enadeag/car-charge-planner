"""Everything the owner has answered so far. No Qt: the pages read and change it, and the wizard writes it once, at Finish."""

from __future__ import annotations

import os
from collections.abc import Mapping
from dataclasses import dataclass, field, replace

from car_charge_planner import units
from car_charge_planner.app_settings import AppSettings
from car_charge_planner.config.model import Config, Setup, require_location, typical_keys, validate
from car_charge_planner.units import METRIC

from .typical import with_typical


@dataclass
class SetupState:
    config: Config  # the draft: it starts as the current config, and is what Finish writes
    initial: Config  # how it started, to tell whether anything was done
    units: str = METRIC  # how distances and consumption are shown: an app setting, saved apart from the config
    initial_units: str = METRIC
    place_label: str = ""  # what the chosen place was called ("Utrecht, Utrecht, Netherlands")
    done: set[str] = field(default_factory=set)  # hub cards completed

    @classmethod
    def starting_from(
        cls, cfg: Config, environ: Mapping[str, str] | None = None, qt_locale_name: str | None = None, app: AppSettings | None = None
    ) -> SetupState:
        """Pre-filled from the current config and app settings. A config that is not set up yet starts as a first run:
        every value typical, and the units the region suggests."""
        draft = cfg
        system = (app or AppSettings()).units
        if not cfg.is_set_up:
            if not cfg.setup.typical:
                every = typical_keys(draft)
                draft = replace(with_typical(draft, *every), setup=Setup(every))  # all typical, and typical values
            system = units.suggest_system(os.environ if environ is None else environ, units.system_qt_locale_name() if qt_locale_name is None else qt_locale_name)
        return cls(config=draft, initial=draft, units=system, initial_units=system)

    def give_value(self, *keys: str) -> None:
        """The owner gave their own value for these: they are no longer typical."""
        self._typical(lambda have: have - set(keys))

    def use_typical(self, *keys: str) -> None:
        """"I don't know": the typical value stays (or becomes) the one in use, and is remembered as typical."""
        self._typical(lambda have: have | set(keys))

    def _typical(self, change) -> None:
        kept = change(set(self.config.setup.typical))
        self.config = replace(self.config, setup=Setup(tuple(key for key in typical_keys(self.config) if key in kept)))

    @property
    def touched(self) -> bool:
        return self.config != self.initial or self.units != self.initial_units or bool(self.done) or bool(self.place_label)

    @property
    def has_location(self) -> bool:
        return self.config.is_set_up

    def result(self) -> Config:
        """The config to write: checked as Settings checks it. Raises `ConfigError` with what is wrong."""
        return require_location(validate(self.config))

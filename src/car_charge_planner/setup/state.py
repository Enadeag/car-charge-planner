"""Everything the owner has answered so far. No Qt: the pages read and change it, and the wizard writes it once, at Finish."""

from __future__ import annotations

import os
from dataclasses import dataclass, field, replace
from typing import Mapping

from car_charge_planner import units
from car_charge_planner.planner.model import TYPICAL_KEYS, Config, Setup, require_location, validate

from .typical import with_typical


@dataclass
class SetupState:
    config: Config  # the draft: it starts as the current config, and is what Finish writes
    initial: Config  # how it started, to tell whether anything was done
    place_label: str = ""  # what the chosen place was called ("Utrecht, Utrecht, Netherlands")
    done: set[str] = field(default_factory=set)  # hub cards completed (S9)

    @classmethod
    def starting_from(cls, cfg: Config, environ: Mapping[str, str] | None = None, qt_locale_name: str | None = None) -> "SetupState":
        """Pre-filled from the current config. A config that is not set up yet starts as a first run: every value
        typical, and the units the region suggests (imperial for US, GB, LR and MM, else metric)."""
        draft = cfg
        if not cfg.is_set_up:
            if not cfg.setup.typical:
                draft = replace(with_typical(draft, *TYPICAL_KEYS), setup=Setup(TYPICAL_KEYS))  # all typical, and typical values
            system = units.suggest_system(os.environ if environ is None else environ, units.system_qt_locale_name() if qt_locale_name is None else qt_locale_name)
            draft = replace(draft, interface=replace(draft.interface, units=system))
        return cls(config=draft, initial=draft)

    # -- what the owner has given -------------------------------------------------------------------------

    def give_value(self, *keys: str) -> None:
        """The owner gave their own value for these: they are no longer typical."""
        self._typical(lambda have: have - set(keys))

    def use_typical(self, *keys: str) -> None:
        """"I don't know": the typical value stays (or becomes) the one in use, and is remembered as typical."""
        self._typical(lambda have: have | set(keys))

    def _typical(self, change) -> None:
        kept = change(set(self.config.setup.typical))
        self.config = replace(self.config, setup=Setup(tuple(key for key in TYPICAL_KEYS if key in kept)))

    # -- where it stands ----------------------------------------------------------------------------------

    @property
    def touched(self) -> bool:
        return self.config != self.initial or bool(self.done) or bool(self.place_label)

    @property
    def has_location(self) -> bool:
        return self.config.is_set_up

    def result(self) -> Config:
        """The config to write: checked as Settings checks it. Raises `ConfigError` with what is wrong."""
        return require_location(validate(self.config))

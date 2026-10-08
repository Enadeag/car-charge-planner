"""Owner-entered state: battery level and manual away periods (JSON, atomic writes)."""

from __future__ import annotations

import json
import logging
import os
from dataclasses import dataclass, field, replace
from datetime import UTC, datetime, timedelta
from pathlib import Path
from typing import Any

from car_charge_planner.planner.model import AwayPeriod, Window
from car_charge_planner.planner.schema import KEY, Migration, NewerVersionError, SchemaError, upgrade

log = logging.getLogger(__name__)

STATE_SCHEMA = 1  # the state.json format this version writes; raise it with a migrate_<n>_to_<n+1> below
STATE_MIGRATIONS: dict[int, Migration] = {}  # source version -> the step to the next one; empty until the format changes

DEFAULT_BATTERY_PCT = 50.0
KEEP_PAST_AWAY = timedelta(days=1)  # drop away periods that ended more than a day ago


@dataclass(frozen=True)
class BatteryReading:
    pct: float
    entered_at: datetime | None  # None: never entered, pct is a placeholder

    @property
    def assumed(self) -> bool:
        return self.entered_at is None


@dataclass(frozen=True)
class State:
    battery: BatteryReading = field(default_factory=lambda: BatteryReading(DEFAULT_BATTERY_PCT, None))
    away: tuple[AwayPeriod, ...] = ()
    notified: tuple[Window, ...] = ()  # windows already announced; the app sticks to them while they run
    seen: tuple[str, ...] = ()  # one-time hints already shown to the owner


def state_path() -> Path:
    base = Path(os.environ.get("XDG_STATE_HOME", Path.home() / ".local" / "state"))
    return base / "car-charge-planner" / "state.json"


def _instant(stamp: float) -> datetime:
    return datetime.fromtimestamp(stamp, UTC)


def _read_blob(path: Path) -> Any:
    """The file's data brought up to the current format. Raises OSError, ValueError or SchemaError."""
    blob: Any = json.loads(path.read_text())
    if isinstance(blob, dict):
        blob = upgrade(blob, STATE_SCHEMA, STATE_MIGRATIONS, path.name)
    return blob


def load_state(path: Path | None = None) -> State:
    """Missing or unreadable state gives the defaults; it never raises.

    A file from a newer version of the app also gives the defaults, and save_state leaves that file alone."""
    path = path or state_path()
    try:
        blob = _read_blob(path)
    except (OSError, ValueError) as err:  # SchemaError is a ValueError
        if isinstance(err, SchemaError):
            log.warning("%s", err)
        return State()
    state = State()
    try:
        state = replace(state, battery=BatteryReading(float(blob["battery_pct"]), _instant(blob["battery_entered_at"])))
    except (KeyError, TypeError, ValueError):
        pass
    away = []
    for item in blob.get("away", []) if isinstance(blob, dict) else []:
        try:
            period = AwayPeriod(_instant(item["start"]), _instant(item["end"]))
        except (KeyError, TypeError, ValueError):
            continue
        if period.end > period.start:
            away.append(period)
    notified = []
    for item in blob.get("notified", []) if isinstance(blob, dict) else []:
        try:
            notified.append(
                Window(
                    _instant(item["start"]),
                    _instant(item["end"]),
                    int(item["amps"]),
                    float(item["power_kw"]),
                    float(item["solar_kwh"]),
                    float(item["import_kwh"]),
                )
            )
        except (KeyError, TypeError, ValueError):
            continue  # also drops records from before windows were stored in full
    seen = tuple(x for x in blob.get("seen", []) if isinstance(x, str)) if isinstance(blob, dict) else ()
    return replace(state, away=tuple(sorted(away, key=lambda a: a.start)), notified=tuple(notified), seen=seen)


def _written_by_newer_version(path: Path) -> bool:
    try:
        _read_blob(path)
    except NewerVersionError as err:
        log.warning("%s Not saving over it.", err)
        return True
    except (OSError, ValueError):
        return False  # unreadable files are replaced, as before
    return False


def save_state(state: State, path: Path | None = None) -> None:
    """Write the state atomically. A file from a newer version of the app is kept as it is: nothing is written."""
    path = path or state_path()
    if _written_by_newer_version(path):
        return
    blob: dict = {
        KEY: STATE_SCHEMA,
        "away": [{"start": a.start.timestamp(), "end": a.end.timestamp()} for a in state.away],
        "notified": [
            {
                "start": n.start.timestamp(),
                "end": n.end.timestamp(),
                "amps": n.amps,
                "power_kw": n.power_kw,
                "solar_kwh": n.solar_kwh,
                "import_kwh": n.import_kwh,
            }
            for n in state.notified
        ],
    }
    if state.seen:
        blob["seen"] = list(state.seen)
    if state.battery.entered_at is not None:
        blob["battery_pct"] = state.battery.pct
        blob["battery_entered_at"] = state.battery.entered_at.timestamp()
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(".tmp")
    tmp.write_text(json.dumps(blob, indent=2))
    tmp.replace(path)


def load_battery(path: Path | None = None) -> BatteryReading:
    return load_state(path).battery


def set_battery(pct: float, now: datetime, path: Path | None = None) -> State:
    if not 0 <= pct <= 100:
        raise ValueError(f"battery must be between 0 and 100, got {pct}")
    state = replace(load_state(path), battery=BatteryReading(float(pct), now))
    save_state(state, path)
    return state


def add_away(period: AwayPeriod, now: datetime, path: Path | None = None) -> State:
    if period.end <= period.start:
        raise ValueError("away period must end after it starts")
    state = load_state(path)
    away = [a for a in state.away if a.end > now - KEEP_PAST_AWAY and a != period]
    state = replace(state, away=tuple(sorted([*away, period], key=lambda a: a.start)))
    save_state(state, path)
    return state


def remove_away(period: AwayPeriod, path: Path | None = None) -> State:
    state = load_state(path)
    state = replace(state, away=tuple(a for a in state.away if a != period))
    save_state(state, path)
    return state


def mark_notified(window: Window, now: datetime, path: Path | None = None) -> State:
    """Remember that a window was announced: no repeat after a restart, and the app keeps to it."""
    state = load_state(path)
    kept = [n for n in state.notified if n.end > now - KEEP_PAST_AWAY]
    state = replace(state, notified=tuple([*kept, window]))
    save_state(state, path)
    return state


def mark_seen(hint: str, path: Path | None = None) -> bool:
    """Remember that a one-time hint was shown. True the first time, False if it had been shown before."""
    state = load_state(path)
    if hint in state.seen:
        return False
    save_state(replace(state, seen=(*state.seen, hint)), path)
    return True

"""Charge windows and charge power (spec 4.3-4.4)."""

from __future__ import annotations

import math
from dataclasses import dataclass
from datetime import date, datetime, timedelta
from zoneinfo import ZoneInfo

from .model import AwayPeriod, Config, ForecastStep, Window, add_hours, elapsed_hours
from .surplus import amps_for, charge_power_kw, usable_kw

_EPS = 1e-9
_ROUND_TO = timedelta(minutes=5)


@dataclass(frozen=True)
class Segment:
    """A forecast step clipped to 'now', with its usable surplus."""

    start: datetime
    end: datetime
    usable_kw: float

    @property
    def hours(self) -> float:
        return elapsed_hours(self.start, self.end)


def build_runs(
    forecast: list[ForecastStep],
    cfg: Config,
    away: list[AwayPeriod],
    now: datetime,
) -> list[list[Segment]]:
    """Contiguous runs of steps where the car is home and solar can power it."""
    runs: list[list[Segment]] = []
    current: list[Segment] = []
    for step in sorted(forecast, key=lambda s: s.start):
        start = max(step.start, now)
        if step.end <= start:
            continue
        seg = Segment(start, step.end, usable_kw(step, cfg))
        at_home = not any(a.start < seg.end and seg.start < a.end for a in away)
        chargeable = amps_for(seg.usable_kw, cfg) >= cfg.charger.min_amps
        if at_home and chargeable:
            if current and current[-1].end != seg.start:
                runs.append(current)
                current = []
            current.append(seg)
        elif current:
            runs.append(current)
            current = []
    if current:
        runs.append(current)
    return runs


def _ceil_to_five_minutes(moment: datetime) -> datetime:
    midnight = moment.replace(hour=0, minute=0, second=0, microsecond=0)
    steps = math.ceil((moment - midnight) / _ROUND_TO - _EPS)
    return midnight + steps * _ROUND_TO


def _best_candidate(run: list[Segment], remaining: float, cfg: Config):
    """Best (amps, first, last) sub-interval of a run, or None."""
    best_key = None
    best = None
    min_window = timedelta(minutes=cfg.planning.min_window_minutes)
    tolerance = cfg.planning.import_tolerance_kwh
    for amps in range(cfg.charger.max_amps, cfg.charger.min_amps - 1, -1):
        power = charge_power_kw(amps, cfg)
        for i in range(len(run)):
            solar = imported = 0.0
            for j in range(i, len(run)):
                hours = run[j].hours
                solar += min(power, run[j].usable_kw) * hours
                imported += max(0.0, power - run[j].usable_kw) * hours
                if imported > tolerance + _EPS:
                    break
                if run[j].end - run[i].start < min_window:
                    continue
                key = (
                    round(min(solar, remaining), 6),
                    -round(imported, 6),
                    -run[i].start.timestamp(),
                    amps,
                )
                if best_key is None or key > best_key:
                    best_key, best = key, (amps, i, j)
    return best


def _make_window(run: list[Segment], amps: int, i: int, j: int, remaining: float, cfg: Config) -> Window:
    """Trim the chosen interval to the energy needed, but keep the minimum length."""
    power = charge_power_kw(amps, cfg)
    start = run[i].start
    end = run[j].end
    cumulative = 0.0
    trimmed_end = end
    for seg in run[i : j + 1]:
        delivered = min(power, seg.usable_kw) * seg.hours
        if cumulative + delivered >= remaining - _EPS:
            rate = min(power, seg.usable_kw)
            needed_hours = (remaining - cumulative) / rate if rate > 0 else seg.hours
            trimmed_end = _ceil_to_five_minutes(add_hours(seg.start, needed_hours))
            break
        cumulative += delivered
    trimmed_end = min(trimmed_end, end)
    minimum = timedelta(minutes=cfg.planning.min_window_minutes)
    end = min(max(trimmed_end, start + minimum), end)

    solar = imported = 0.0
    for seg in run[i : j + 1]:
        overlap_start, overlap_end = max(seg.start, start), min(seg.end, end)
        if overlap_end <= overlap_start:
            continue
        hours = elapsed_hours(overlap_start, overlap_end)
        solar += min(power, seg.usable_kw) * hours
        imported += max(0.0, power - seg.usable_kw) * hours
    return Window(start, end, amps, power, solar, imported)


def select_windows(runs: list[list[Segment]], need_grid_kwh: float, cfg: Config) -> list[Window]:
    """Pick the windows for one day that deliver the energy the car needs."""
    remaining = need_grid_kwh
    unused = list(runs)
    chosen: list[Window] = []
    while remaining > _EPS and unused:
        scored: list[tuple[float, float, list[Segment], Window]] = []
        for run in unused:
            candidate = _best_candidate(run, remaining, cfg)
            if candidate is not None:
                amps, i, j = candidate
                window = _make_window(run, amps, i, j, remaining, cfg)
                scored.append((window.solar_kwh, -window.start.timestamp(), run, window))
        if not scored:
            break
        scored.sort(key=lambda s: (s[0], s[1]), reverse=True)
        _, _, run, window = scored[0]
        chosen.append(window)
        remaining -= window.solar_kwh
        unused.remove(run)
    return sorted(chosen, key=lambda w: w.start)


def group_runs_by_day(runs: list[list[Segment]], tz: ZoneInfo) -> dict[date, list[list[Segment]]]:
    """A run belongs to the local date it starts on, even if it crosses midnight."""
    grouped: dict[date, list[list[Segment]]] = {}
    for run in runs:
        grouped.setdefault(run[0].start.astimezone(tz).date(), []).append(run)
    return grouped

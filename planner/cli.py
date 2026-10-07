"""Print the plan from a forecast file or a synthetic demo forecast.

    python -m planner.cli --demo summer --battery 45
    python -m planner.cli --live --battery 45
    python -m planner.cli --forecast forecast.json --battery 45 --config config.toml

Forecast file: JSON list of {"start": ISO8601, "end": ISO8601, "power_kw": expected solar power in kW}.
"""

from __future__ import annotations

import argparse
import json
from datetime import date, datetime, timedelta
from pathlib import Path
from zoneinfo import ZoneInfo

from .demo import bell_day
from .model import Config, ForecastStep, add_hours, load_config
from .advice import grid_advice
from .projection import plan

DEMOS = {
    "summer": [900, 900, 800, 900],
    "overcast": [150, 120, 200, 150],
    "mixed": [150, 900, 300, 800],
    "winter": [200, 150, 250, 100],
}


def demo_forecast(name: str, first_day: date, tz: ZoneInfo, cfg: Config | None = None) -> list[ForecastStep]:
    """A synthetic forecast: bell-shaped irradiance turned into power for the configured (or default) panels."""
    from forecast.open_meteo import power_kw

    panels = (cfg or Config()).panels
    steps = []
    for offset, peak in enumerate(DEMOS[name]):
        sunrise, sunset = (8, 16) if name == "winter" else (6, 18)
        for start, gti in bell_day(first_day + timedelta(days=offset), peak, tz, sunrise, sunset):
            steps.append(ForecastStep(start, add_hours(start, 1), power_kw(gti, panels.kwp, panels.system_losses)))
    return steps


def load_forecast(path: Path) -> list[ForecastStep]:
    rows = json.loads(path.read_text())
    if any("gti" in row and "power_kw" not in row for row in rows):
        raise SystemExit(f"{path}: this file uses the old format ('gti', W/m2). Give 'power_kw' (expected solar power in kW) instead.")
    return [ForecastStep(datetime.fromisoformat(r["start"]), datetime.fromisoformat(r["end"]), float(r["power_kw"])) for r in rows]


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--demo", choices=sorted(DEMOS))
    source.add_argument("--forecast", type=Path)
    source.add_argument("--live", action="store_true", help="Open-Meteo forecast (cached for an hour)")
    parser.add_argument("--refresh", action="store_true", help="with --live: ignore the cache")
    parser.add_argument("--battery", type=float, required=True, help="current battery %%")
    parser.add_argument("--config", type=Path)
    parser.add_argument("--now", help="ISO time, default: now")
    args = parser.parse_args(argv)

    cfg = load_config(args.config)
    tz = ZoneInfo(cfg.timezone)
    now = datetime.fromisoformat(args.now).astimezone(tz) if args.now else datetime.now(tz)
    if args.live:
        from forecast.open_meteo import ForecastUnavailable, get_forecast

        if not cfg.is_set_up:
            raise SystemExit("No location is set: give latitude and longitude in a config file (--config) or in Settings. --demo needs none.")
        try:
            result = get_forecast(cfg, now, force=args.refresh)
        except ForecastUnavailable as err:
            raise SystemExit(f"no forecast available: {err}")
        forecast = result.steps
        note = " (STALE: refresh failed)" if result.stale else ""
        print(f"Forecast fetched {result.age(now).total_seconds() / 60:.0f} min ago{note}. Weather data by Open-Meteo.com (CC BY 4.0).")
    else:
        forecast = demo_forecast(args.demo, now.date(), tz, cfg) if args.demo else load_forecast(args.forecast)

    days = plan(forecast, cfg, args.battery, now)
    for day in days:
        print(f"{day.date:%a %d %b}  battery {day.soc_start_pct:.0f}% -> {day.soc_after_charging_pct:.0f}% (end of day {day.soc_end_of_day_pct:.0f}%)")
        if not day.windows:
            print("    no good window")
        for w in day.windows:
            print(
                f"    {w.start:%H:%M}-{w.end:%H:%M}  {w.power_kw:.1f} kW ({w.amps} A)  "
                f"{w.solar_kwh:.1f} kWh solar, {w.import_kwh:.1f} kWh grid, {w.solar_share:.0%} solar"
            )

    advice = grid_advice(days, forecast, cfg, None, now)
    if advice:
        print(f"\nGRID CHARGE NEEDED by end of {advice.day:%d %b}: battery would end at {max(0, advice.soc_end_pct):.0f}% (minimum {cfg.car.minimum_pct:.0f}%), need {advice.energy_kwh:.1f} kWh")
        w = advice.window
        if w:
            print(f"    best: {w.start:%a %d %b %H:%M}-{w.end:%H:%M}  {w.power_kw:.1f} kW ({w.amps} A)  {w.solar_kwh:.1f} kWh solar, {w.import_kwh:.1f} kWh grid")
        else:
            print("    no free time at home to fit it")


if __name__ == "__main__":
    main()


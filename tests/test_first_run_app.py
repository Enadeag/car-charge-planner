"""The whole app, started as a program on a desktop without a tray (offscreen), with nothing set up.

Each test starts `car_charge_planner.main.main()` in a subprocess with its own folders and a fake forecast, so nothing touches the
network or the owner's files, and a small driver acts as the owner.
"""

import json
import os
import subprocess
import sys
import textwrap
from pathlib import Path

from app_process import isolated_user

REPO = Path(__file__).resolve().parent.parent

DRIVER = textwrap.dedent(
    """
    import json, sys
    sys.path.insert(0, {repo!r})
    sys.path.insert(0, {tests!r})
    import xdg_paths
    xdg_paths.apply()  # every variable is set: the app keeps its files where the test says, on every system
    sys.argv = ["car-charge-planner"] + {argv!r}
    from datetime import datetime, timedelta, timezone
    from PySide6.QtCore import QTimer
    from PySide6.QtWidgets import QApplication

    from car_charge_planner.forecast.check import Attempt
    from car_charge_planner.forecast.service import ForecastResult, ForecastService
    from car_charge_planner.planner.demo import bell_day
    from car_charge_planner.planner.model import ForecastStep
    from car_charge_planner.forecast.providers.open_meteo import power_kw
    import car_charge_planner.main as appmain
    import car_charge_planner.notifier as notifier_module
    import car_charge_planner.setup.wizard as wizard_module

    sent = []
    from car_charge_planner.tray_notifier import TrayNotifier
    for notifier_class in (notifier_module.Notifier, TrayNotifier):  # the one this system picks
        notifier_class.send = lambda self, title, body, expire_ms=20000: (sent.append(title), 1)[1]

    def fake_get(self, cfg, now, force=False):  # no network: three bell-shaped days
        from zoneinfo import ZoneInfo
        tz = ZoneInfo(cfg.timezone)
        steps = []
        for i in range(3):
            for start, irradiance in bell_day((now + timedelta(days=i)).date(), 900, tz):
                steps.append(ForecastStep(start, start + timedelta(hours=1), power_kw(irradiance, 4.8, 0.14)))
        return ForecastResult(steps, now.astimezone(timezone.utc), False, False, "open-meteo", "Open-Meteo", "Weather data: Open-Meteo.com (CC BY 4.0)")

    ForecastService.get = fake_get
    wizard_module.SetupWizard._real_check = lambda self, cfg: Attempt(True, 2, "OK")

    controllers = []
    original = appmain.Controller
    class Spy(original):
        def __init__(self, *a, **k):
            super().__init__(*a, **k)
            controllers.append(self)
            QTimer.singleShot(1500, look)  # the application exists now: act a moment after the app has started
    appmain.Controller = Spy

    shells = []
    original_create_shell = appmain.create_shell
    def spy_create_shell(*a, **k):
        shell, choice = original_create_shell(*a, **k)
        shells.append(shell)
        return shell, choice
    appmain.create_shell = spy_create_shell

    report = {{}}

    def look():
        c = controllers[0]
        wizard = shells[0].windows.setup_wizard
        report["wizard_open"] = bool(wizard is not None and wizard.isVisible())
        report["state_at_start"] = c.vm.state if c.vm else None
        report["notifications"] = list(sent)
        if {act!r} and wizard is not None:
            wizard.confirm_leave = lambda: True
            wizard.navigate("location")
            page = wizard.pages["location"]
            page.latitude.setValue(52.37)
            page.longitude.setValue(4.9)
            wizard.navigate("finish")
            for t in list(wizard.pages["finish"]._threads):
                t.join(5)
            report["finished"] = wizard.finish()
            QTimer.singleShot(1500, done)
        else:
            done()

    def done():
        c = controllers[0]
        report["set_up"] = c.cfg.is_set_up
        report["state_at_end"] = c.vm.state if c.vm else None
        report["window_visible"] = any(w.isVisible() and w.windowTitle() != "" and "Set-up" not in w.windowTitle() for w in QApplication.topLevelWidgets())
        report["config_written"] = open({config!r}, encoding="utf-8").read().count("[forecast]") == 1
        print("REPORT " + json.dumps(report), flush=True)
        QApplication.quit()

    sys.exit(appmain.main(sys.argv))
    """
)


def run_app(tmp_path, argv, act):
    config = tmp_path / "config" / "car-charge-planner" / "config.toml"
    config.parent.mkdir(parents=True)
    config.write_text('[interface]\nmode = "window"\n', encoding="utf-8")  # a window: this desktop has no tray (and nothing to wait for)
    for name in ("state", "cache", "runtime"):
        (tmp_path / name).mkdir()
    (tmp_path / "runtime").chmod(0o700)
    driver = tmp_path / "driver.py"
    driver.write_text(DRIVER.format(repo=str(REPO), tests=str(REPO / 'tests'), argv=argv, act=act, config=str(config)), encoding="utf-8")
    env = {
        **{k: v for k, v in os.environ.items() if k not in ("DBUS_SESSION_BUS_ADDRESS", "LANGUAGE", "LC_ALL")},
        "QT_QPA_PLATFORM": "offscreen",
        "XDG_CONFIG_HOME": str(tmp_path / "config"),
        "XDG_STATE_HOME": str(tmp_path / "state"),
        "XDG_CACHE_HOME": str(tmp_path / "cache"),
        "XDG_RUNTIME_DIR": str(tmp_path / "runtime"),
        "XDG_DATA_HOME": str(tmp_path / "data"),
        **isolated_user(tmp_path),
        "LANG": "nl_NL.UTF-8",
    }
    done = subprocess.run([sys.executable, str(driver)], env=env, capture_output=True, text=True, timeout=60, cwd=REPO, encoding="utf-8")
    lines = [line for line in done.stdout.splitlines() if line.startswith("REPORT ")]
    assert lines, f"the app did not report (exit {done.returncode}):\n{done.stderr[-1500:]}"
    return json.loads(lines[-1][len("REPORT "):])


def test_a_first_start_opens_the_wizard_and_finishing_it_leads_to_a_plan(tmp_path):
    report = run_app(tmp_path, [], act=True)
    assert report["wizard_open"] and report["state_at_start"] == "needs-setup"
    assert report["finished"] is True and report["set_up"] and report["state_at_end"] not in ("needs-setup", None)
    assert report["config_written"] and report["notifications"] == []


def test_a_start_at_login_does_not_open_the_wizard_and_offers_it_in_a_notification(tmp_path):
    report = run_app(tmp_path, ["--background"], act=False)
    assert report["wizard_open"] is False and report["set_up"] is False
    assert report["notifications"] == ["Set-up needed"]

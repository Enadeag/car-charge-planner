"""Start the whole app as a program and wait until it is running, instead of sleeping and hoping.

The app is started through a two-line launcher that runs the same `python -m car_charge_planner` entry point but announces
"READY" on stdout when the Qt event loop is about to start: everything before that (language, config, tray wait, shell,
first refresh) has then run without crashing.
"""

import os
import subprocess
import sys
import time
from pathlib import Path

PROJECT = Path(__file__).resolve().parent.parent

LAUNCHER = """
import runpy, sys
from PySide6.QtWidgets import QApplication

run = QApplication.exec
QApplication.exec = staticmethod(lambda *args: (print("READY", flush=True), run(*args))[1])
sys.argv[0] = "car_charge_planner"
runpy.run_module("car_charge_planner", run_name="__main__")
"""


def app_environment(home: Path, **extra) -> dict:
    """An environment in which the app keeps everything under `home` and shows no tray (offscreen)."""
    for name in ("config", "state", "cache", "run", "data"):
        (home / name).mkdir(mode=0o700, exist_ok=True)
    return {
        **os.environ,
        "QT_QPA_PLATFORM": "offscreen",
        "PYTHONUTF8": "1",  # the app's output is read as UTF-8; Windows would otherwise write it in its own code page
        "HOME": str(home),
        "XDG_CONFIG_HOME": str(home / "config"),
        "XDG_STATE_HOME": str(home / "state"),
        "XDG_CACHE_HOME": str(home / "cache"),
        "XDG_RUNTIME_DIR": str(home / "run"),
        "XDG_DATA_HOME": str(home / "data"),
        **extra,
    }


class RunningApp:
    def __init__(self, home: Path, *flags: str, env: dict | None = None):
        self.out_path, self.err_path = home / "app.out", home / "app.err"
        self.out, self.err = open(self.out_path, "w", encoding="utf-8"), open(self.err_path, "w", encoding="utf-8")
        self.process = subprocess.Popen(
            [sys.executable, "-c", LAUNCHER, *flags], cwd=PROJECT, env=env or app_environment(home), stdout=self.out, stderr=self.err, text=True,
            encoding="utf-8",
        )

    def wait_until_running(self, timeout: float = 60.0) -> None:
        """Return once the event loop is about to start; fail with the app's stderr if it exits first or never gets there."""
        deadline = time.monotonic() + timeout
        while "READY" not in self.out_path.read_text(encoding="utf-8"):
            assert self.process.poll() is None, f"the app exited with {self.process.returncode}: {self.err_path.read_text(encoding="utf-8")}"
            assert time.monotonic() < deadline, f"the app did not start within {timeout:.0f} s: {self.err_path.read_text(encoding="utf-8")}"
            time.sleep(0.05)

    def still_running_after(self, seconds: float = 0.5) -> bool:
        """The first events of the loop (timers, queued signals) have run without the app exiting."""
        try:
            self.process.wait(seconds)
        except subprocess.TimeoutExpired:
            return True
        return False

    def stop(self) -> str:
        """Terminate the app and return what it wrote to stderr."""
        self.process.terminate()
        try:
            self.process.wait(10)
        except subprocess.TimeoutExpired:
            self.process.kill()
            self.process.wait()
        self.out.close()
        self.err.close()
        return self.err_path.read_text(encoding="utf-8")

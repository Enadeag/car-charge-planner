"""The engine runs without Qt: it can be imported, and can plan, with PySide6 unavailable."""

import os
import subprocess
import sys

SCRIPT = """
import sys
sys.modules["PySide6"] = None
sys.modules["shiboken6"] = None
from car_charge_planner.engine import Engine
from car_charge_planner.planner.model import Config

class Silent:
    def send(self, title, body, expire_ms=20_000):
        return None

    def owns(self, notification_id):
        return False

engine = Engine(Config(), Silent())
engine.refresh()
assert engine.vm is not None and engine.vm.state == "needs-setup"
"""


def test_the_engine_imports_and_plans_without_pyside6(tmp_path):
    env = {**os.environ, "XDG_STATE_HOME": str(tmp_path), "XDG_CACHE_HOME": str(tmp_path)}
    result = subprocess.run([sys.executable, "-c", SCRIPT], capture_output=True, text=True, timeout=60, env=env)
    assert result.returncode == 0, result.stderr

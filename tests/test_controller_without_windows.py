"""The controller runs without any window: it can be imported with Qt Widgets unavailable."""

import subprocess
import sys

SCRIPT = """
import sys
sys.modules["PySide6.QtWidgets"] = None
import car_charge_planner.controller
assert not [m for m in sys.modules if m.startswith("car_charge_planner.") and m.split(".")[1] in {"settings", "setup", "about", "app_windows", "planview", "popup"}]
"""


def test_the_controller_imports_without_qt_widgets_or_any_window_module():
    result = subprocess.run([sys.executable, "-c", SCRIPT], capture_output=True, text=True, timeout=60)
    assert result.returncode == 0, result.stderr

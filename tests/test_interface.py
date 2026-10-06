"""The interface setting and the choice between the tray shell and the window shell."""

import os

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

from dataclasses import replace

import pytest
from PySide6.QtWidgets import QApplication

from app.controller import Controller
from app.main import NO_TRAY_HINT, announce_tray_fallback, create_shell, wait_for_tray
from app.settings.window import SettingsWindow
from app.shells import ShellChoice, choose_shell
from app.shells.tray import TrayShell
from app.shells.window import WindowShell
from planner.config_io import dump_config, write_config
from planner.model import INTERFACE_MODES, Config, ConfigError, Interface, load_config
from storage.state import load_state

from conftest import FakeNotifier


@pytest.fixture
def qapp():
    return QApplication.instance() or QApplication([])


def with_mode(mode):
    return replace(Config(), interface=Interface(mode))


# ---- the config key ----------------------------------------------------------------


def test_the_default_is_automatic():
    assert Config().interface.mode == "auto" and INTERFACE_MODES == ("auto", "tray", "window")


def test_the_mode_is_read_written_and_round_trips(tmp_path):
    for mode in INTERFACE_MODES:
        f = tmp_path / f"{mode}.toml"
        write_config(with_mode(mode), f)
        assert load_config(f).interface.mode == mode
    assert "[interface]" in dump_config(Config()) and 'mode = "auto"' in dump_config(Config())


def test_a_file_without_the_section_means_automatic(tmp_path):
    f = tmp_path / "c.toml"
    f.write_text("[panels]\nkwp = 5\n")  # an older config
    assert load_config(f).interface.mode == "auto"


def test_an_unknown_mode_is_a_readable_error(tmp_path):
    f = tmp_path / "c.toml"
    f.write_text('[interface]\nmode = "popup"\n')
    with pytest.raises(ConfigError) as err:
        load_config(f)
    assert "mode must be one of auto, tray, window" in str(err.value) and "popup" in str(err.value)


def test_an_unknown_key_in_the_section_is_rejected(tmp_path):
    f = tmp_path / "c.toml"
    f.write_text('[interface]\nstyle = "dark"\n')
    with pytest.raises(ConfigError) as err:
        load_config(f)
    assert "style" in str(err.value)


# ---- choosing the shell --------------------------------------------------------------


@pytest.mark.parametrize(
    "mode, tray, expected",
    [
        ("auto", True, ShellChoice("tray")),
        ("auto", False, ShellChoice("window")),  # simply a window: nothing was asked for that is missing
        ("tray", True, ShellChoice("tray")),
        ("tray", False, ShellChoice("window", fell_back=True)),  # asked for a tray, there is none: say so
        ("window", True, ShellChoice("window")),
        ("window", False, ShellChoice("window")),
    ],
)
def test_the_choice_matrix(mode, tray, expected):
    assert choose_shell(mode, tray) == expected


# ---- waiting for the tray at login -----------------------------------------------------


class FakeTime:
    def __init__(self, ready_after_probes=None):
        self.now, self.probes, self.sleeps, self.ready_after = 0.0, 0, [], ready_after_probes

    def clock(self):
        return self.now

    def sleep(self, ms):
        self.sleeps.append(ms)
        self.now += ms / 1000

    def probe(self):
        self.probes += 1
        return self.ready_after is not None and self.probes >= self.ready_after


def test_a_tray_that_is_there_at_once_costs_no_waiting():
    t = FakeTime(ready_after_probes=1)
    assert wait_for_tray(5, t.probe, t.sleep, t.clock) is True and t.sleeps == []


def test_a_tray_that_appears_a_moment_later_is_found():
    t = FakeTime(ready_after_probes=4)  # the panel finished starting while we waited
    assert wait_for_tray(5, t.probe, t.sleep, t.clock) is True and len(t.sleeps) == 3


def test_no_tray_at_all_gives_up_after_the_timeout():
    t = FakeTime()
    assert wait_for_tray(2, t.probe, t.sleep, t.clock) is False
    assert 2 <= t.now <= 2.5 and t.probes >= 8


def test_background_starts_wait_longer_than_interactive_ones():
    from app.main import TRAY_WAIT_BACKGROUND_S, TRAY_WAIT_INTERACTIVE_S

    assert TRAY_WAIT_BACKGROUND_S > TRAY_WAIT_INTERACTIVE_S >= 0


# ---- making the shell ------------------------------------------------------------------


def controller_with(mode, tmp_path, monkeypatch):
    monkeypatch.setenv("XDG_STATE_HOME", str(tmp_path))
    return Controller(with_mode(mode), tmp_path / "config.toml", FakeNotifier())


@pytest.mark.parametrize(
    "mode, tray, shell_class, fell_back",
    [
        ("auto", True, TrayShell, False),
        ("auto", False, WindowShell, False),
        ("tray", False, WindowShell, True),
        ("window", True, WindowShell, False),
    ],
)
def test_the_right_shell_is_created(mode, tray, shell_class, fell_back, tmp_path, monkeypatch, qapp):
    shell, choice = create_shell(controller_with(mode, tmp_path, monkeypatch), tray)
    assert isinstance(shell, shell_class) and choice.fell_back is fell_back


def test_the_missing_tray_is_announced_once_ever(tmp_path, monkeypatch, qapp):
    controller = controller_with("tray", tmp_path, monkeypatch)
    choice = choose_shell("tray", False)
    assert announce_tray_fallback(controller, choice) is True
    title, body = controller.notifier.sent[0]
    assert "tray" in title.lower() and "Interface" in body
    assert NO_TRAY_HINT in load_state().seen
    assert announce_tray_fallback(controller, choice) is False  # not at every start
    assert len(controller.notifier.sent) == 1


def test_nothing_is_announced_when_nothing_fell_back(tmp_path, monkeypatch, qapp):
    controller = controller_with("auto", tmp_path, monkeypatch)
    assert announce_tray_fallback(controller, choose_shell("auto", False)) is False
    assert announce_tray_fallback(controller, choose_shell("tray", True)) is False
    assert controller.notifier.sent == []


# ---- changing it while the app runs ------------------------------------------------------


def test_changing_the_interface_says_it_applies_at_the_next_start(tmp_path, monkeypatch, qapp):
    controller = controller_with("auto", tmp_path, monkeypatch)
    monkeypatch.setattr(controller, "rebuild", lambda: None)
    monkeypatch.setattr(controller, "refresh", lambda force=False: None)
    controller.apply_config(with_mode("window"))
    assert len(controller.notifier.sent) == 1 and "next time" in controller.notifier.sent[0][1]
    controller.apply_config(with_mode("window"))  # unchanged: silent
    controller.apply_config(replace(with_mode("window"), planning=replace(Config().planning, margin=0.5)))
    assert len(controller.notifier.sent) == 1


# ---- the Interface tab in Settings --------------------------------------------------------


class Harness:
    def __init__(self, tmp_path, cfg=None):
        self.file = tmp_path / "config.toml"
        self.window = SettingsWindow(cfg or Config(), self.file, lambda c: None)
        self.window.confirm_discard = lambda: True
        self.window.show()
        self.tab = next(t for t in self.window.all_tabs if t.title == "Interface")
        self.box = self.tab.inputs["interface.mode"]


@pytest.fixture
def h(tmp_path, qapp):
    return Harness(tmp_path)


def test_the_tab_offers_the_three_modes_with_readable_labels(h):
    values = [h.box.itemData(i) for i in range(h.box.count())]
    labels = [h.box.itemText(i) for i in range(h.box.count())]
    assert values == ["auto", "tray", "window"]
    assert labels[0].startswith("Automatic") and "Tray" in labels[1] and "window" in labels[2].lower()
    assert h.box.currentData() == "auto"


def test_the_hint_says_when_a_change_applies(h):
    from PySide6.QtWidgets import QLabel

    hints = [l.text() for l in h.tab.findChildren(QLabel)]
    assert any("next time the app starts" in t for t in hints) and any("AppIndicator" in t for t in hints)


def test_choosing_a_mode_and_saving_writes_it(h):
    h.box.setCurrentIndex(h.box.findData("window"))
    h.window.save_button.click()
    assert load_config(h.file).interface.mode == "window"


def test_the_tab_shows_the_stored_mode(tmp_path, qapp):
    assert Harness(tmp_path, with_mode("tray")).box.currentData() == "tray"


def test_an_untouched_choice_is_not_a_change_and_an_edit_is(h):
    assert not h.window.is_dirty() and h.window.cancel_button.text() == "Close"
    h.box.setCurrentIndex(h.box.findData("tray"))
    assert h.window.is_dirty() and h.window.cancel_button.text() == "Cancel"
    h.box.setCurrentIndex(h.box.findData("auto"))
    assert not h.window.is_dirty() and h.window.cancel_button.text() == "Close"


def test_reset_tab_goes_back_to_automatic_without_saving(tmp_path, qapp):
    h = Harness(tmp_path, with_mode("window"))
    h.window.tabs.setCurrentWidget(h.tab)
    h.window.reset_button.click()
    assert h.box.currentData() == "auto" and not h.file.exists()


# ---- the whole app, on a desktop without a tray -------------------------------------------


def _env(tmp_path, **extra):
    for name in ("config", "state", "cache", "run"):
        (tmp_path / name).mkdir(mode=0o700, exist_ok=True)
    return {
        **os.environ,
        "QT_QPA_PLATFORM": "offscreen",  # an offscreen desktop has no system tray
        "HOME": str(tmp_path),
        "XDG_CONFIG_HOME": str(tmp_path / "config"),
        "XDG_STATE_HOME": str(tmp_path / "state"),
        "XDG_CACHE_HOME": str(tmp_path / "cache"),
        "XDG_RUNTIME_DIR": str(tmp_path / "run"),
        "XDG_DATA_HOME": str(tmp_path / "data"),
        **extra,
    }


def test_without_a_tray_the_app_starts_as_a_window_instead_of_exiting(tmp_path):
    import subprocess
    import sys
    import time
    from pathlib import Path

    project = Path(__file__).resolve().parent.parent
    env = _env(tmp_path)
    first = subprocess.Popen([sys.executable, "-m", "app"], cwd=project, env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    try:
        time.sleep(4)  # a second to look for a tray, then it carries on as a window
        assert first.poll() is None, first.stderr.read()  # still running: it did not exit with "no system tray"
        second = subprocess.run([sys.executable, "-m", "app"], cwd=project, env=env, capture_output=True, text=True, timeout=30)
        assert second.returncode == 0 and "already running" in second.stderr
        assert first.poll() is None  # and the first one is still there
    finally:
        first.terminate()
        try:
            out, err = first.communicate(timeout=10)
        except subprocess.TimeoutExpired:
            first.kill()
            out, err = first.communicate()
    assert "Traceback" not in err and "No system tray" not in err


def test_the_autostart_flag_starts_the_whole_app_without_a_tray_too(tmp_path):
    """What the autostart entry runs: `python -m app --background`, here on a desktop with no tray."""
    import subprocess
    import sys
    import time
    from pathlib import Path

    project = Path(__file__).resolve().parent.parent
    process = subprocess.Popen(
        [sys.executable, "-m", "app", "--background"], cwd=project, env=_env(tmp_path), stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True
    )
    try:
        time.sleep(8)  # up to five seconds looking for a tray at login, then the hidden window
        assert process.poll() is None, process.stderr.read()
    finally:
        process.terminate()
        try:
            out, err = process.communicate(timeout=10)
        except subprocess.TimeoutExpired:
            process.kill()
            out, err = process.communicate()
    assert "Traceback" not in err and "unknown command" not in err.lower()

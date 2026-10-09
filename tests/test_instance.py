import os
import stat
import subprocess
import sys
import textwrap
from pathlib import Path

import pytest
from PySide6.QtTest import QTest
from PySide6.QtWidgets import QApplication

from car_charge_planner.instance import InstanceServer, Outcome, claim_instance, request_show, socket_path

PROJECT = Path(__file__).resolve().parent.parent


@pytest.fixture
def qapp():
    return QApplication.instance() or QApplication([])


@pytest.fixture
def path(tmp_path):
    return str(tmp_path / "instance.sock")


def settle(ms=150):
    QTest.qWait(ms)


def test_with_no_server_a_request_finds_nobody(qapp, path):
    assert request_show(path, timeout_ms=200) is False


def test_a_request_reaches_the_running_instance_once(qapp, path):
    server = InstanceServer(path)
    assert server.listen()
    seen = []
    server.show_requested.connect(lambda: seen.append(1))
    assert request_show(path) is True
    settle()
    assert seen == [1]
    server.close()


def test_every_request_is_delivered(qapp, path):
    server = InstanceServer(path)
    server.listen()
    seen = []
    server.show_requested.connect(lambda: seen.append(1))
    for _ in range(3):
        assert request_show(path)
    settle(250)
    assert len(seen) == 3
    server.close()


def test_other_messages_are_ignored(qapp, path):
    from PySide6.QtNetwork import QLocalSocket

    server = InstanceServer(path)
    server.listen()
    seen = []
    server.show_requested.connect(lambda: seen.append(1))
    socket = QLocalSocket()
    socket.connectToServer(path)
    assert socket.waitForConnected(500)
    socket.write(b"quit\nshutdown now\n")
    socket.flush()
    socket.waitForBytesWritten(500)
    socket.disconnectFromServer()
    settle()
    assert seen == []
    server.close()


def test_the_first_claim_becomes_the_instance_the_second_signals_it(qapp, path):
    first_outcome, first = claim_instance(path)
    assert first_outcome is Outcome.PRIMARY and first is not None
    seen = []
    first.show_requested.connect(lambda: seen.append(1))
    second_outcome, second = claim_instance(path)
    assert second_outcome is Outcome.SIGNALLED and second is None
    settle()
    assert seen == [1]
    first.close()


def test_after_the_instance_ends_the_next_start_becomes_the_instance(qapp, path):
    outcome, first = claim_instance(path)
    first.close()
    outcome, again = claim_instance(path)
    assert outcome is Outcome.PRIMARY
    again.close()


def test_a_stale_socket_file_from_a_crash_is_replaced(qapp, path):
    Path(path).write_text("left behind by a crashed instance")  # nobody listens on it
    outcome, server = claim_instance(path)
    assert outcome is Outcome.PRIMARY
    assert request_show(path)
    server.close()


def test_a_live_instances_socket_is_never_removed(qapp, path):
    outcome, first = claim_instance(path)
    for _ in range(3):
        again, _server = claim_instance(path)
        assert again is Outcome.SIGNALLED
    assert Path(path).exists() and request_show(path)
    first.close()


def test_when_no_socket_can_be_made_the_app_carries_on(qapp, tmp_path):
    outcome, server = claim_instance(str(tmp_path / "missing-directory" / "x.sock"))
    assert outcome is Outcome.UNAVAILABLE and server is None


def test_the_socket_file_is_private_to_the_user(qapp, path):
    outcome, server = claim_instance(path)
    mode = stat.S_IMODE(os.stat(path).st_mode)
    assert mode & 0o077 == 0, oct(mode)
    server.close()


def test_a_second_server_cannot_take_over_a_path_a_live_server_holds(qapp, path):
    """Qt's UserAccessOption lets listen() succeed here and replaces the live socket: never use it."""
    live = InstanceServer(path)
    assert live.listen()
    intruder = InstanceServer(path)
    assert intruder.listen() is False
    assert request_show(path)
    live.close()


def test_the_path_lives_in_the_runtime_directory(monkeypatch):
    monkeypatch.setenv("XDG_RUNTIME_DIR", "/run/user/1234")
    assert socket_path() == "/run/user/1234/car-charge-planner.sock"


@pytest.mark.linux
@pytest.mark.macos
def test_without_a_runtime_directory_it_uses_a_private_directory_of_its_own(monkeypatch, tmp_path):
    monkeypatch.delenv("XDG_RUNTIME_DIR", raising=False)
    monkeypatch.setattr("tempfile.gettempdir", lambda: str(tmp_path))
    path = Path(socket_path())
    assert path.parent == tmp_path / f"car-charge-planner-{os.getuid()}" and path.name == "instance.sock"
    assert stat.S_IMODE(path.parent.stat().st_mode) == 0o700


@pytest.mark.linux
@pytest.mark.macos
def test_a_directory_that_is_open_to_others_is_not_trusted(monkeypatch, tmp_path, qapp):
    monkeypatch.delenv("XDG_RUNTIME_DIR", raising=False)
    monkeypatch.setattr("tempfile.gettempdir", lambda: str(tmp_path))
    directory = tmp_path / f"car-charge-planner-{os.getuid()}"
    directory.mkdir(mode=0o755)
    directory.chmod(0o755)
    with pytest.raises(OSError):
        socket_path()
    outcome, server = claim_instance()  # and the app then carries on without the protection
    assert outcome is Outcome.UNAVAILABLE and server is None


def test_a_second_launch_of_the_real_app_hands_over_and_exits(tmp_path):
    """Process A is the running instance; `car-charge-planner` is the second start."""
    runtime = tmp_path / "run"
    runtime.mkdir(mode=0o700)
    env = {**os.environ, "XDG_RUNTIME_DIR": str(runtime), "QT_QPA_PLATFORM": "offscreen", "HOME": str(tmp_path)}
    first = subprocess.Popen(
        [
            sys.executable,
            "-c",
            textwrap.dedent(
                """
                import sys
                from PySide6.QtCore import QTimer
                from PySide6.QtWidgets import QApplication
                from car_charge_planner.instance import Outcome, claim_instance
                app = QApplication([])
                outcome, server = claim_instance()
                print(outcome.value, flush=True)
                server.show_requested.connect(lambda: (print("SHOW", flush=True), app.quit()))
                QTimer.singleShot(8000, app.quit)
                app.exec()
                """
            ),
        ],
        cwd=PROJECT,
        env=env,
        stdout=subprocess.PIPE,
        text=True,
    )
    try:
        assert first.stdout.readline().strip() == "primary"
        second = subprocess.run([sys.executable, "-m", "car_charge_planner"], cwd=PROJECT, env=env, capture_output=True, text=True, timeout=30)
        assert second.returncode == 0 and "already running" in second.stderr
        assert first.stdout.readline().strip() == "SHOW"
    finally:
        first.kill()
        first.wait()


def test_two_copies_starting_at_once_the_loser_signals_instead_of_removing_the_socket(qapp, path, monkeypatch):
    import car_charge_planner.instance as instance

    winner = InstanceServer(path)
    assert winner.listen()
    seen = []
    winner.show_requested.connect(lambda: seen.append(1))

    real = instance.request_show
    calls = []

    def first_look_comes_too_early(p, timeout_ms=500):
        calls.append(1)
        return False if len(calls) == 1 else real(p, timeout_ms)

    monkeypatch.setattr(instance, "request_show", first_look_comes_too_early)
    outcome, server = claim_instance(path)
    assert outcome is Outcome.SIGNALLED and server is None
    assert len(calls) == 2  # it looked again before deciding the socket was stale
    assert Path(path).exists()
    settle()
    assert seen == [1]
    winner.close()

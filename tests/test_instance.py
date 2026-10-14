import os
import shutil
import stat
import subprocess
import sys
import tempfile
import textwrap
import uuid
from pathlib import Path

import pytest
from PySide6.QtTest import QTest

from another_thread import from_another_thread
from app_process import RunningApp, app_environment, isolated_user
from car_charge_planner.instance import InstanceServer, Outcome, Reply, claim_instance, request_quit, request_show, socket_path

PROJECT = Path(__file__).resolve().parent.parent


@pytest.fixture
def path(socket_name):
    return socket_name


def settle(ms=150):
    QTest.qWait(ms)


def test_with_no_server_a_request_finds_nobody(qapp, path):
    assert request_show(path, timeout_ms=200) is Reply.NOBODY


def test_a_request_reaches_the_running_instance_once(qapp, path):
    server = InstanceServer(path)
    assert server.listen()
    seen = []
    server.show_requested.connect(lambda: seen.append(1))
    assert from_another_thread(request_show, path) is Reply.ANSWERED
    settle()
    assert seen == [1]
    server.close()


def test_a_request_to_a_server_that_never_answers_is_reported_as_silent(qapp, path):
    from PySide6.QtNetwork import QLocalServer

    mute, held = QLocalServer(), []
    mute.newConnection.connect(lambda: held.append(mute.nextPendingConnection()))  # accepts, never answers
    assert mute.listen(path)
    assert from_another_thread(request_show, path, 500, 200) is Reply.SILENT
    settle()
    for connection in held:
        connection.close()
    mute.close()


def test_a_second_start_that_gets_no_answer_does_not_become_or_replace_the_instance(qapp, path):
    from PySide6.QtNetwork import QLocalServer

    mute, held = QLocalServer(), []
    mute.newConnection.connect(lambda: held.append(mute.nextPendingConnection()))  # accepts, never answers
    assert mute.listen(path)
    assert from_another_thread(claim_instance, path) == (Outcome.NO_ANSWER, None)
    settle()
    for connection in held:
        connection.close()
    mute.close()


def test_a_quit_request_is_answered_and_asks_the_instance_to_quit_without_showing(qapp, path):
    server = InstanceServer(path)
    assert server.listen()
    seen = []
    server.show_requested.connect(lambda: seen.append("show"))
    server.quit_requested.connect(lambda: seen.append("quit"))
    assert from_another_thread(request_quit, path) is Reply.ANSWERED
    settle()
    assert seen == ["quit"]
    server.close()


def test_with_no_server_a_quit_request_finds_nobody(qapp, path):
    assert request_quit(path, timeout_ms=200) is Reply.NOBODY


def test_every_request_is_delivered(qapp, path):
    server = InstanceServer(path)
    server.listen()
    seen = []
    server.show_requested.connect(lambda: seen.append(1))
    for _ in range(3):
        assert from_another_thread(request_show, path) is Reply.ANSWERED
    settle(250)
    assert len(seen) == 3
    server.close()


def test_other_messages_are_ignored(qapp, path):
    from PySide6.QtNetwork import QLocalSocket

    server = InstanceServer(path)
    server.listen()
    seen = []
    server.show_requested.connect(lambda: seen.append(1))
    server.quit_requested.connect(lambda: seen.append(2))
    socket = QLocalSocket()
    socket.connectToServer(path)
    assert socket.waitForConnected(500)
    socket.write(b"restart\nshutdown now\n")
    socket.flush()
    socket.waitForBytesWritten(500)
    settle()
    assert seen == [] and socket.bytesAvailable() == 0
    socket.disconnectFromServer()
    settle()
    server.close()


def test_the_first_claim_becomes_the_instance_the_second_signals_it(qapp, path):
    first_outcome, first = claim_instance(path)
    assert first_outcome is Outcome.PRIMARY and first is not None
    seen = []
    first.show_requested.connect(lambda: seen.append(1))
    second_outcome, second = from_another_thread(claim_instance, path)
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


@pytest.mark.linux
@pytest.mark.macos
def test_a_stale_socket_file_from_a_crash_is_replaced(qapp, path):
    Path(path).write_text("left behind by a crashed instance", encoding="utf-8")  # nobody listens on it
    outcome, server = claim_instance(path)
    assert outcome is Outcome.PRIMARY
    assert from_another_thread(request_show, path) is Reply.ANSWERED
    settle()
    server.close()


def test_a_live_instances_socket_is_never_removed(qapp, path):
    outcome, first = claim_instance(path)
    for _ in range(3):
        again, _server = from_another_thread(claim_instance, path)
        assert again is Outcome.SIGNALLED
    assert from_another_thread(request_show, path) is Reply.ANSWERED
    settle()
    first.close()


@pytest.mark.linux
@pytest.mark.macos
def test_when_no_socket_can_be_made_the_app_carries_on(qapp, tmp_path):
    outcome, server = claim_instance(str(tmp_path / "missing-directory" / "x.sock"))
    assert outcome is Outcome.UNAVAILABLE and server is None


@pytest.mark.linux
@pytest.mark.macos
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
    assert from_another_thread(request_show, path) is Reply.ANSWERED
    settle()
    live.close()


@pytest.mark.linux
@pytest.mark.macos
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


@pytest.mark.windows
def test_on_windows_the_name_is_a_pipe_name_per_user(monkeypatch):
    monkeypatch.setattr("getpass.getuser", lambda: "alice")
    alice = socket_path()
    monkeypatch.setattr("getpass.getuser", lambda: "bob")
    assert alice == "car-charge-planner-alice"
    assert socket_path() == "car-charge-planner-bob"


@pytest.mark.windows
def test_on_windows_a_second_start_signals_the_first_by_the_per_user_name(qapp, monkeypatch):
    user = f"test-{uuid.uuid4().hex}"
    monkeypatch.setattr("getpass.getuser", lambda: user)
    outcome, first = claim_instance()
    assert outcome is Outcome.PRIMARY and first is not None
    seen = []
    first.show_requested.connect(lambda: seen.append(1))
    assert from_another_thread(claim_instance) == (Outcome.SIGNALLED, None)
    settle()
    assert seen == [1]
    first.close()


@pytest.mark.macos
def test_on_macos_a_second_start_signals_the_first_in_the_private_directory(qapp, monkeypatch):
    directory = tempfile.mkdtemp(prefix="ccp")  # short: a socket path is limited to about 100 characters
    monkeypatch.setattr("tempfile.gettempdir", lambda: directory)
    monkeypatch.delenv("XDG_RUNTIME_DIR", raising=False)
    try:
        outcome, first = claim_instance()
        assert outcome is Outcome.PRIMARY and first is not None
        seen = []
        first.show_requested.connect(lambda: seen.append(1))
        assert from_another_thread(claim_instance) == (Outcome.SIGNALLED, None)
        settle()
        assert seen == [1]
        first.close()
    finally:
        shutil.rmtree(directory, ignore_errors=True)


def test_a_second_launch_of_the_real_app_hands_over_and_exits(tmp_path):
    """Process A is the running instance; `car-charge-planner` is the second start."""
    runtime = tmp_path / "run"
    runtime.mkdir(mode=0o700)
    env = {**os.environ, "XDG_RUNTIME_DIR": str(runtime), "QT_QPA_PLATFORM": "offscreen", "HOME": str(tmp_path), **isolated_user(tmp_path)}
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
        encoding="utf-8",
    )
    try:
        assert first.stdout.readline().strip() == "primary"
        second = subprocess.run([sys.executable, "-m", "car_charge_planner"], cwd=PROJECT, env=env, capture_output=True, text=True, timeout=30, encoding="utf-8")
        assert second.returncode == 0 and "already running" in second.stderr
        assert first.stdout.readline().strip() == "SHOW"
    finally:
        first.kill()
        first.wait()


def test_a_second_launch_of_the_real_app_says_when_the_running_one_does_not_respond(tmp_path):
    runtime = tmp_path / "run"
    runtime.mkdir(mode=0o700)
    env = {**os.environ, "XDG_RUNTIME_DIR": str(runtime), "QT_QPA_PLATFORM": "offscreen", "HOME": str(tmp_path), **isolated_user(tmp_path)}
    first = subprocess.Popen(
        [
            sys.executable,
            "-c",
            textwrap.dedent(
                """
                from PySide6.QtCore import QCoreApplication, QTimer
                from PySide6.QtNetwork import QLocalServer
                from car_charge_planner.instance import socket_path
                app = QCoreApplication([])
                server = QLocalServer()
                assert server.listen(socket_path())  # takes the socket but never answers
                print("listening", flush=True)
                QTimer.singleShot(15000, app.quit)
                app.exec()
                """
            ),
        ],
        cwd=PROJECT,
        env=env,
        stdout=subprocess.PIPE,
        text=True,
        encoding="utf-8",
    )
    try:
        assert first.stdout.readline().strip() == "listening"
        second = subprocess.run([sys.executable, "-m", "car_charge_planner"], cwd=PROJECT, env=env, capture_output=True, text=True, timeout=30, encoding="utf-8")
        assert second.returncode == 1 and "did not respond" in second.stderr
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

    def first_look_comes_too_early(p, *args):
        calls.append(1)
        return Reply.NOBODY if len(calls) == 1 else real(p, *args)

    monkeypatch.setattr(instance, "request_show", first_look_comes_too_early)
    outcome, server = from_another_thread(claim_instance, path)
    assert outcome is Outcome.SIGNALLED and server is None
    assert len(calls) == 2  # it looked again before deciding the socket was stale
    settle()
    assert seen == [1]
    winner.close()


def test_the_quit_command_of_the_real_app_ends_the_running_app_cleanly(tmp_path):
    app = RunningApp(tmp_path, "--background")
    try:
        app.wait_until_running()
        done = subprocess.run([sys.executable, "-m", "car_charge_planner", "quit"], cwd=PROJECT, env=app_environment(tmp_path), capture_output=True, text=True, timeout=30, encoding="utf-8")
        assert done.returncode == 0 and "confirmed" in done.stdout
        assert app.process.wait(30) == 0
    finally:
        app.stop()


@pytest.fixture
def quit_command(monkeypatch, path, capsys):
    monkeypatch.setattr("car_charge_planner.instance.socket_path", lambda: path)

    def run():
        from car_charge_planner.__main__ import main

        code = from_another_thread(main, ["quit"])
        captured = capsys.readouterr()
        return code, captured.out, captured.err

    return run


def test_the_quit_command_reports_a_confirmed_quit(qapp, path, quit_command):
    server = InstanceServer(path)
    assert server.listen()
    seen = []
    server.quit_requested.connect(lambda: seen.append(1))
    code, out, _err = quit_command()
    settle()
    assert (code, seen) == (0, [1]) and "confirmed" in out
    server.close()


def test_the_quit_command_says_when_nobody_runs(qapp, quit_command):
    code, out, _err = quit_command()
    assert code == 0 and "not running" in out


def test_the_quit_command_fails_when_the_running_app_does_not_respond(qapp, path, quit_command, monkeypatch):
    from PySide6.QtNetwork import QLocalServer

    monkeypatch.setattr("car_charge_planner.instance.request_quit", lambda target: request_quit(target, 500, 200))  # not the full wait
    mute, held = QLocalServer(), []
    mute.newConnection.connect(lambda: held.append(mute.nextPendingConnection()))  # accepts, never answers
    assert mute.listen(path)
    code, _out, err = quit_command()
    assert code == 1 and "did not respond" in err
    settle()
    for connection in held:
        connection.close()
    mute.close()

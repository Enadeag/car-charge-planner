"""One running copy of the app. A second start asks the first to show itself and exits.

The first instance listens on a private local socket (a named pipe on Windows): unlike a lock file, a socket also lets
the second start say "show yourself", and the `quit` command say "quit".
"""

from __future__ import annotations

import getpass
import hashlib
import logging
import os
import stat
import sys
import tempfile
from enum import Enum
from pathlib import Path

from PySide6.QtCore import QElapsedTimer, QObject, QThread, Signal
from PySide6.QtNetwork import QLocalServer, QLocalSocket
from shiboken6 import isValid

from .identity import DESKTOP_ENTRY

log = logging.getLogger(__name__)

SHOW = b"show"
QUIT = b"quit"
OK = b"ok"
CONNECT_TIMEOUT_MS = 500
WAIT_SLICE_MS = 10
ANSWER_TIMEOUT_MS = 2000  # a first instance that is busy planning may take a moment to get back to its event loop
RACE_PAUSE_MS = 100  # another instance may be starting at this very moment


class Outcome(Enum):
    PRIMARY = "primary"  # we are the running instance
    SIGNALLED = "signalled"  # another instance runs; it was asked to show itself
    NO_ANSWER = "no_answer"  # another instance holds the socket but did not answer the request
    UNAVAILABLE = "unavailable"  # no socket could be made; carry on without single-instance protection


class Reply(Enum):
    NOBODY = "nobody"  # nothing listens on the socket
    ANSWERED = "answered"
    SILENT = "silent"  # connected, but no "ok" within the timeout


ERROR_ALREADY_EXISTS = 183


def socket_path() -> str:
    """Private to the user. Windows: a pipe name per user. Elsewhere: the runtime directory (mode 0700) when there
    is one, else a private directory of our own under the temp directory."""
    if sys.platform == "win32":
        return f"{DESKTOP_ENTRY}-{getpass.getuser()}"
    runtime = os.environ.get("XDG_RUNTIME_DIR")
    if runtime:
        return str(Path(runtime) / f"{DESKTOP_ENTRY}.sock")
    directory = Path(tempfile.gettempdir()) / f"{DESKTOP_ENTRY}-{os.getuid()}"
    directory.mkdir(mode=0o700, exist_ok=True)
    info = directory.stat()
    if info.st_uid != os.getuid() or stat.S_IMODE(info.st_mode) & 0o077:
        raise OSError(f"{directory} is not a private directory of this user")  # someone else made it, or it is open
    return str(directory / "instance.sock")


def request_show(path: str, timeout_ms: int = CONNECT_TIMEOUT_MS, answer_timeout_ms: int = ANSWER_TIMEOUT_MS) -> Reply:
    """Ask a running instance to show itself and wait for its "ok"."""
    return _request(SHOW, path, timeout_ms, answer_timeout_ms)


def request_quit(path: str, timeout_ms: int = CONNECT_TIMEOUT_MS, answer_timeout_ms: int = ANSWER_TIMEOUT_MS) -> Reply:
    """Ask a running instance to quit and wait for its "ok", which it sends before it quits."""
    return _request(QUIT, path, timeout_ms, answer_timeout_ms)


def _request(message: bytes, path: str, timeout_ms: int, answer_timeout_ms: int) -> Reply:
    """Waiting for the answer also keeps a Windows pipe client from disconnecting before the server has read its message."""
    socket = QLocalSocket()
    socket.connectToServer(path)
    if not socket.waitForConnected(timeout_ms):
        return Reply.NOBODY
    socket.write(message + b"\n")
    socket.flush()
    socket.waitForBytesWritten(timeout_ms)
    reply = Reply.SILENT
    clock = QElapsedTimer()
    clock.start()
    while True:
        if socket.canReadLine():
            if bytes(socket.readLine()).strip() == OK:  # pyright: ignore[reportArgumentType]  # a QByteArray is bytes-like
                reply = Reply.ANSWERED
            break
        remaining = answer_timeout_ms - clock.elapsed()
        if remaining <= 0:
            break
        socket.waitForReadyRead(min(remaining, WAIT_SLICE_MS))  # in slices: a long blocking call holds the interpreter lock
    socket.disconnectFromServer()
    if socket.state() != QLocalSocket.LocalSocketState.UnconnectedState:
        socket.waitForDisconnected(timeout_ms)
    return reply


def _claim_name(name: str) -> int | None:
    """Windows only: take a named mutex for this server name; None when a live process already holds it."""
    import ctypes
    from ctypes import wintypes

    kernel32 = ctypes.WinDLL("kernel32", use_last_error=True)  # pyright: ignore[reportAttributeAccessIssue]
    kernel32.CreateMutexW.argtypes = [wintypes.LPVOID, wintypes.BOOL, wintypes.LPCWSTR]
    kernel32.CreateMutexW.restype = wintypes.HANDLE
    kernel32.CloseHandle.argtypes = [wintypes.HANDLE]
    handle = kernel32.CreateMutexW(None, False, "Local\\" + hashlib.sha256(name.encode()).hexdigest()[:32])
    if not handle:
        return None
    if ctypes.get_last_error() == ERROR_ALREADY_EXISTS:  # pyright: ignore[reportAttributeAccessIssue]  # Windows only
        kernel32.CloseHandle(handle)
        return None
    return handle


def _release_name(handle: int) -> None:
    import ctypes
    from ctypes import wintypes

    kernel32 = ctypes.WinDLL("kernel32")  # pyright: ignore[reportAttributeAccessIssue]
    kernel32.CloseHandle.argtypes = [wintypes.HANDLE]
    kernel32.CloseHandle(handle)


class InstanceServer(QObject):
    show_requested = Signal()
    quit_requested = Signal()

    def __init__(self, path: str) -> None:
        super().__init__()
        self.path = path
        self.server = QLocalServer(self)
        self._mutex: int | None = None
        # Not setSocketOptions(UserAccessOption): with it Qt's listen() succeeds on a path that a live
        # server already holds, replacing its socket file. A plain listen() fails atomically instead,
        # which is what the start-up race needs. Privacy comes from the directory and the chmod below.
        self.server.newConnection.connect(self._on_connection)

    def listen(self) -> bool:
        if sys.platform == "win32":
            # Qt lets a second server listen on a pipe name that a live server holds, so the mutex is what keeps
            # a live instance from being taken over.
            self._mutex = _claim_name(self.path)
            if self._mutex is None:
                return False
            if not self.server.listen(self.path):
                self.close()
                return False
            return True
        if not self.server.listen(self.path):
            return False
        try:
            os.chmod(self.path, 0o600)
        except OSError:
            pass  # the directory is private anyway
        return True

    def error(self) -> str:
        return self.server.errorString()

    def close(self) -> None:
        self.server.close()
        if self._mutex is not None:
            _release_name(self._mutex)
            self._mutex = None

    def _on_connection(self) -> None:
        while self.server.hasPendingConnections():
            socket = self.server.nextPendingConnection()
            socket.readyRead.connect(lambda s=socket: self._read(s))
            socket.disconnected.connect(lambda s=socket: (self._read(s), s.deleteLater() if isValid(s) else None))
            self._read(socket)  # the data may have arrived with the connection

    def _read(self, socket: QLocalSocket) -> None:
        while isValid(socket) and socket.canReadLine():  # closing the server deletes its sockets while signals are queued
            line = bytes(socket.readLine())  # pyright: ignore[reportArgumentType]  # the stub does not know a QByteArray is bytes-like
            message = line.strip()
            if message not in (SHOW, QUIT):
                continue
            if message == SHOW:
                self.show_requested.emit()
            if socket.state() == QLocalSocket.LocalSocketState.ConnectedState:
                socket.write(OK + b"\n")
                socket.flush()
            if message == QUIT:
                self.quit_requested.emit()  # after the answer: quitting may close the server


def _handed_over(reply: Reply) -> Outcome:
    return Outcome.SIGNALLED if reply is Reply.ANSWERED else Outcome.NO_ANSWER


def claim_instance(path: str | None = None) -> tuple[Outcome, InstanceServer | None]:
    """Become the running instance, or tell the one that runs to show itself.

    A socket file left by a crashed instance is detected (nobody answers on it) and replaced. A live
    instance's socket is never removed: if listening fails, ask once more before treating it as stale,
    because another copy may have started a moment ago.
    """
    try:
        path = path or socket_path()
    except OSError as err:
        log.warning("No private place for the single-instance socket: %s", err)
        return Outcome.UNAVAILABLE, None
    reply = request_show(path)
    if reply is not Reply.NOBODY:
        return _handed_over(reply), None
    server = InstanceServer(path)
    if server.listen():
        return Outcome.PRIMARY, server
    QThread.msleep(RACE_PAUSE_MS)
    reply = request_show(path)
    if reply is not Reply.NOBODY:
        return _handed_over(reply), None
    QLocalServer.removeServer(path)  # a stale socket file
    if server.listen():
        return Outcome.PRIMARY, server
    log.warning("Could not listen on %s: %s", path, server.error())
    return Outcome.UNAVAILABLE, None

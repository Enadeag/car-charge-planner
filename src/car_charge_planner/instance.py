"""One running copy of the app. A second start asks the first to show itself and exits.

The first instance listens on a private local socket (a named pipe on Windows): unlike a lock file, a socket also lets
the second start say "show yourself".
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

from PySide6.QtCore import QObject, QThread, Signal
from PySide6.QtNetwork import QLocalServer, QLocalSocket

from .identity import DESKTOP_ENTRY

log = logging.getLogger(__name__)

SHOW = b"show"
CONNECT_TIMEOUT_MS = 500
RACE_PAUSE_MS = 100  # another instance may be starting at this very moment


class Outcome(Enum):
    PRIMARY = "primary"  # we are the running instance
    SIGNALLED = "signalled"  # another instance runs; it was asked to show itself
    UNAVAILABLE = "unavailable"  # no socket could be made; carry on without single-instance protection


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


def request_show(path: str, timeout_ms: int = CONNECT_TIMEOUT_MS) -> bool:
    """Ask a running instance to show itself. False when nobody answers on the socket."""
    socket = QLocalSocket()
    socket.connectToServer(path)
    if not socket.waitForConnected(timeout_ms):
        return False
    socket.write(SHOW + b"\n")
    socket.flush()
    socket.waitForBytesWritten(timeout_ms)
    socket.disconnectFromServer()
    if socket.state() != QLocalSocket.LocalSocketState.UnconnectedState:
        socket.waitForDisconnected(timeout_ms)
    return True


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
            socket.disconnected.connect(lambda s=socket: (self._read(s), s.deleteLater()))
            self._read(socket)  # the data may have arrived with the connection

    def _read(self, socket: QLocalSocket) -> None:
        while socket.canReadLine():
            line = bytes(socket.readLine())  # pyright: ignore[reportArgumentType]  # the stub does not know a QByteArray is bytes-like
            if line.strip() == SHOW:
                self.show_requested.emit()


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
    if request_show(path):
        return Outcome.SIGNALLED, None
    server = InstanceServer(path)
    if server.listen():
        return Outcome.PRIMARY, server
    QThread.msleep(RACE_PAUSE_MS)
    if request_show(path):
        return Outcome.SIGNALLED, None
    QLocalServer.removeServer(path)  # a stale socket file
    if server.listen():
        return Outcome.PRIMARY, server
    log.warning("Could not listen on %s: %s", path, server.error())
    return Outcome.UNAVAILABLE, None

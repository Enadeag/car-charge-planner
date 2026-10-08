"""Keep Python's garbage collector on the main thread.

Qt objects must be destroyed on the thread that made them. Python's cycle collector runs on whichever thread
happens to allocate memory when it triggers, and the app has background threads (the forecast fetch, the
notification listener). If one of those triggers a collection, a leftover Qt object (a widget or a timer in a
reference cycle) can be destroyed on the wrong thread: Qt prints "Timers cannot be stopped from another
thread" and the process may crash.

So automatic collection is switched off and the main thread collects on a timer and at exit.
"""

from __future__ import annotations

import gc

from PySide6.QtCore import QCoreApplication, QTimer

INTERVAL_MS = 30_000


def install(app: QCoreApplication, interval_ms: int = INTERVAL_MS) -> QTimer:
    """Disable automatic collection and collect from the main thread's event loop instead."""
    gc.disable()
    timer = QTimer(app)
    timer.timeout.connect(gc.collect)
    timer.start(interval_ms)
    app.aboutToQuit.connect(gc.collect)
    return timer

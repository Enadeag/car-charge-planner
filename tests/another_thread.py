from concurrent.futures import ThreadPoolExecutor

from PySide6.QtTest import QTest


def from_another_thread(function, *args):
    """Run a blocking call (another start of the app) on a thread while this thread's event loop serves the instance.

    On Windows a pipe client that disconnects before the server has read from it loses its message, and the server
    reads only when its thread runs events: a second start in the same thread would never be heard.
    """
    with ThreadPoolExecutor(1) as pool:
        future = pool.submit(function, *args)
        while not future.done():
            QTest.qWait(10)
        return future.result()

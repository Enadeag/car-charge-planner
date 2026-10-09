"""How a provider talks to the network, as a function it is given: tests and the conformance kit replace it.

A provider never opens a connection itself. It builds a `Request`, hands it to the `Transport` it was created
with and reads the `Reply`. A server's refusal (a 429, a 503) is a `Reply` with that status; only "could not
talk to the server at all" raises, as an `OSError` (which `URLError` and timeouts are).
"""

from __future__ import annotations

import json
import urllib.error
import urllib.request
from collections.abc import Callable
from dataclasses import dataclass, field
from typing import Any

PROJECT_URL = "https://github.com/Enadeag/car-charge-planner"
USER_AGENT = f"car-charge-planner (+{PROJECT_URL})"
TIMEOUT_S = 15


@dataclass(frozen=True)
class Request:
    url: str
    headers: dict[str, str] = field(default_factory=dict[str, str])
    timeout_s: float = TIMEOUT_S  # a search box wants a short one


@dataclass(frozen=True)
class Reply:
    status: int
    body: bytes = b""
    headers: dict[str, str] = field(default_factory=dict[str, str])

    @property
    def ok(self) -> bool:
        return 200 <= self.status < 300

    def header(self, name: str) -> str | None:
        """Headers are case-insensitive."""
        wanted = name.lower()
        return next((value for key, value in self.headers.items() if key.lower() == wanted), None)

    def json(self) -> Any:
        return json.loads(self.body.decode("utf-8"))


Transport = Callable[[Request], Reply]


def urllib_transport(request: Request) -> Reply:
    headers = {"User-Agent": USER_AGENT, **request.headers}
    try:
        with urllib.request.urlopen(urllib.request.Request(request.url, headers=headers), timeout=request.timeout_s) as response:
            return Reply(response.status, response.read(), dict(response.headers))
    except urllib.error.HTTPError as err:  # the server answered, with a refusal
        return Reply(err.code, err.read() if err.fp else b"", dict(err.headers or {}))

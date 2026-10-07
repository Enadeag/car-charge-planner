"""What a page of the wizard is, and the order they come in."""

from __future__ import annotations

from PySide6.QtCore import Signal
from PySide6.QtWidgets import QWidget


class Page(QWidget):
    """One page. It reads the answers when it is shown (`enter`) and writes its own back when it is left (`leave`)."""

    id = ""  # what "Change" links and the wizard call it by
    in_sequence = True  # False: a page reached from another (the hub's cards), and left with Back
    changed = Signal()  # what the page holds changed: Next may become possible or not

    def __init__(self, wizard) -> None:
        super().__init__()
        self.wizard = wizard

    @property
    def state(self):
        return self.wizard.state

    @property
    def title(self) -> str:  # pragma: no cover - every page names itself
        return ""

    def enter(self) -> None:
        """Shown: show what the answers say."""

    def leave(self) -> None:
        """Left forwards or back: keep what was entered."""

    def can_continue(self) -> bool:
        return True

    def next_label(self) -> str | None:
        """A different text for the Next button, or None for the usual one."""
        return None

"""What a page of the wizard is, and the order they come in."""

from __future__ import annotations

from PySide6.QtCore import Qt, Signal
from PySide6.QtWidgets import QToolButton, QWidget


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


# A button of a row of choices (a compass direction, a roof tilt): the one that is down must be plain to see in any theme.
CHOICE_BUTTON_STYLE = (
    "QPushButton { border: 1px solid palette(mid); border-radius: 3px; padding: 4px 8px; }"
    "QPushButton:checked { background-color: palette(highlight); color: palette(highlighted-text); border-color: palette(highlight); }"
)


def fold(text: str) -> QToolButton:
    """A small button that opens and closes a part of a page: an arrow and a name."""
    button = QToolButton()
    button.setText(text)
    button.setCheckable(True)
    button.setAutoRaise(True)
    button.setToolButtonStyle(Qt.ToolButtonStyle.ToolButtonTextBesideIcon)
    button.setArrowType(Qt.ArrowType.RightArrow)
    button.toggled.connect(lambda open_: button.setArrowType(Qt.ArrowType.DownArrow if open_ else Qt.ArrowType.RightArrow))
    return button

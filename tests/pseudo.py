"""A pseudo-language for the offscreen UI tests (reused by every page that is added later).

`install(tmp_path)` builds a catalogue from the committed `.pot` in which every text is wrapped and lengthened
(`Charge now` -> `[!! Charge now~~~ !!]`) and makes it the active language. Anything the user can read that is
not wrapped was never translated; anything that no longer fits shows where a long language would break the layout.
"""

from __future__ import annotations

import html
import math
import re
from pathlib import Path

from babel.messages.catalog import Catalog
from babel.messages.mofile import write_mo
from babel.messages.pofile import read_po
from PySide6.QtWidgets import (
    QAbstractButton,
    QAbstractSpinBox,
    QComboBox,
    QLabel,
    QTabBar,
    QWidget,
)

from app import i18n
from app.i18n import DOMAIN

LANGUAGE = "en"  # plural rules of English; the directory name is all gettext looks at
GROWTH = 0.3
KEEP = {("decimal separator", "."): ",", ("list separator", ", "): ", "}  # these are glue, not words

MARK = "[!!"


def wrap(text: str) -> str:
    return f"{MARK} {text}{'~' * math.ceil(len(text) * GROWTH)} !!]"


def build(localedir: Path, pot: Path = i18n.LOCALE_DIR / f"{DOMAIN}.pot") -> Path:
    with open(pot, "rb") as handle:
        template = read_po(handle)
    catalogue = Catalog(locale=LANGUAGE, domain=DOMAIN)
    for message in template:
        if not message.id:
            continue
        context = message.context
        if isinstance(message.id, tuple):  # plural: one wrapped text per form
            forms = tuple(wrap(m) for m in message.id)
            catalogue.add(message.id, forms, context=context)
        elif (context, message.id) in KEEP:
            catalogue.add(message.id, KEEP[(context, message.id)], context=context)
        else:
            catalogue.add(message.id, wrap(message.id), context=context)
    target = localedir / LANGUAGE / "LC_MESSAGES" / f"{DOMAIN}.mo"
    target.parent.mkdir(parents=True, exist_ok=True)
    with open(target, "wb") as handle:
        write_mo(handle, catalogue)
    return target


def install(tmp_path: Path) -> None:
    build(tmp_path)
    assert i18n.init([LANGUAGE], tmp_path) == LANGUAGE


# ---- reading what is on screen ----------------------------------------------------------------------------------

_TAGS = re.compile(r"<[^>]+>")
# what is not a word to translate: numbers, times, units and symbols, which the app shows as they are
_NOT_TEXT = re.compile(r"^(?:[0-9.,:%/–\-·→•×+()\s]|kWp|kWh|kW|km|[AV]|min|h)*$")


def plain(text: str) -> str:
    return html.unescape(_TAGS.sub("", text)).strip()


def needs_translation(text: str) -> bool:
    return bool(text) and not _NOT_TEXT.match(text) and MARK not in text


def shown(root: QWidget) -> list[QWidget]:
    return [root, *(w for w in root.findChildren(QWidget) if w.isVisibleTo(root))]


def visible_texts(root: QWidget) -> list[tuple[str, str]]:
    """(what, text) for every text of a shown page: labels, buttons, tabs, tooltips, choices, suffixes."""
    found: list[tuple[str, str]] = []
    for w in shown(root):
        name = f"{type(w).__name__}:{w.objectName() or ''}"
        if isinstance(w, QLabel):
            found.append((name, plain(w.text())))
        elif isinstance(w, QAbstractButton):
            found.append((name, plain(w.text())))
            menu = getattr(w, "menu", lambda: None)()
            if menu is not None:
                found += [(name + " menu", plain(a.text())) for a in menu.actions()]
                found += [(name + " menu tip", plain(a.toolTip())) for a in menu.actions()]
        elif isinstance(w, QTabBar):
            found += [(name + " tab", plain(w.tabText(i))) for i in range(w.count())]
        elif isinstance(w, QAbstractSpinBox):
            found.append((name + " suffix", plain(getattr(w, "suffix", lambda: "")())))
        elif isinstance(w, QComboBox) and not w.isEditable():  # an editable one is a list of place names
            found += [(name + " item", plain(w.itemText(i))) for i in range(w.count())]
        if w.toolTip():
            found.append((name + " tooltip", plain(w.toolTip())))
    return [(what, text) for what, text in found if text]


def proper_nouns() -> set[str]:
    """What stays as it is in every language: the forecast providers' names and the credits they ask for."""
    from forecast.registry import registry

    return {text for provider in registry.all() for text in (provider.name, provider.attribution)}


def untranslated(root: QWidget) -> list[str]:
    names = sorted(proper_nouns(), key=len, reverse=True)
    found = []
    for what, text in visible_texts(root):
        for name in names:
            text = text.replace(name, "")
        if needs_translation(text.strip(" \u00b7")):
            found.append(f"{what}: {text!r}")
    return found


def clipped(root: QWidget) -> list[str]:
    """Texts that do not fit the room they were given. The window must be shown and laid out."""
    problems: list[str] = []
    for w in shown(root):
        if isinstance(w, QLabel) and w.text() and w.pixmap().isNull():
            text = plain(w.text())
            if not text:
                continue
            if w.wordWrap():
                if w.heightForWidth(w.width()) > w.height() + 1:
                    problems.append(f"label too short: {text!r}")
            elif w.fontMetrics().horizontalAdvance(text) > w.width() + 1:
                problems.append(f"label too narrow: {text!r} ({w.width()}px)")
        elif isinstance(w, QAbstractButton) and w.text():
            text = plain(w.text())
            if w.fontMetrics().horizontalAdvance(text) > w.width():
                problems.append(f"button too narrow: {text!r} ({w.width()}px)")
    return problems

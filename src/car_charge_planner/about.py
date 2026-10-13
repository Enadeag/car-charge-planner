"""The About dialog: version, author attribution, licence and where the source is."""

from __future__ import annotations

from importlib.metadata import PackageNotFoundError, version

from PySide6.QtCore import Qt
from PySide6.QtWidgets import QDialog, QDialogButtonBox, QLabel, QVBoxLayout

from .i18n import _
from .identity import APP_NAME

SOURCE_URL = "https://github.com/Enadeag/car-charge-planner"
LICENCE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"
AUTHOR = "Kelvin Vroegop"
ATTRIBUTION = f"{APP_NAME}, originally by {AUTHOR}"  # the wording ADDITIONAL-TERMS.md requires: never translated


def app_version() -> str:
    try:
        return version("car-charge-planner")
    except PackageNotFoundError:  # run from a source tree that was never installed
        return "?"


class AboutDialog(QDialog):
    def __init__(self) -> None:
        super().__init__(None)
        self.setWindowTitle(_("About {app}").format(app=APP_NAME))
        box = QVBoxLayout(self)
        lines = [
            f"<b>{APP_NAME}</b> {_('version {version}').format(version=app_version())}",
            ATTRIBUTION,
            f"Copyright © 2026 {AUTHOR}",
            _('Licensed under the <a href="{url}">GNU General Public License v3.0 or later</a>.').format(url=LICENCE_URL),
            _('<a href="{url}">Source code</a>').format(url=SOURCE_URL),
        ]
        self.text = QLabel("<br>".join(lines))
        self.text.setTextFormat(Qt.TextFormat.RichText)
        self.text.setOpenExternalLinks(True)
        self.text.setWordWrap(True)
        box.addWidget(self.text)
        buttons = QDialogButtonBox(QDialogButtonBox.StandardButton.Close)
        buttons.rejected.connect(self.reject)
        box.addWidget(buttons)

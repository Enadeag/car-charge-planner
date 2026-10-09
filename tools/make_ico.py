"""Draw `assets/car-charge-planner.svg` at every size Windows asks for and write `assets/car-charge-planner.ico`.

Run it after the SVG changes: `python tools/make_ico.py`. Needs PySide6 (QtSvg); the result is committed so that a
build needs nothing but Python. Qt's own ICO writer saves one size only, hence the container is written here.
"""

from __future__ import annotations

import struct
import sys
from pathlib import Path

ASSETS = Path(__file__).resolve().parent.parent / "src" / "car_charge_planner" / "assets"
SIZES = (16, 20, 24, 32, 40, 48, 64, 256)  # taskbar, title bar, Start menu and explorer views at 100 to 200 % scale


def render_png(svg: Path, size: int) -> bytes:
    from PySide6.QtCore import QBuffer, QByteArray, QIODevice, Qt
    from PySide6.QtGui import QGuiApplication, QImage, QPainter
    from PySide6.QtSvg import QSvgRenderer

    QGuiApplication.instance() or QGuiApplication(sys.argv[:1])
    image = QImage(size, size, QImage.Format.Format_ARGB32)
    image.fill(Qt.GlobalColor.transparent)
    painter = QPainter(image)
    painter.setRenderHint(QPainter.RenderHint.Antialiasing)
    QSvgRenderer(str(svg)).render(painter)
    painter.end()
    data = QByteArray()
    buffer = QBuffer(data)
    buffer.open(QIODevice.OpenModeFlag.WriteOnly)
    image.save(buffer, "PNG")
    return bytes(data.data())


def build_ico(pngs: dict[int, bytes]) -> bytes:
    """An ICO container of PNG images (Windows has read these since Vista). A size of 256 is written as 0."""
    header = struct.pack("<HHH", 0, 1, len(pngs))
    offset = len(header) + 16 * len(pngs)
    entries, images = b"", b""
    for size, png in pngs.items():
        entries += struct.pack("<BBBBHHII", size % 256, size % 256, 0, 0, 1, 32, len(png), offset + len(images))
        images += png
    return header + entries + images


def main() -> None:
    svg = ASSETS / "car-charge-planner.svg"
    (ASSETS / "car-charge-planner.ico").write_bytes(build_ico({size: render_png(svg, size) for size in SIZES}))


if __name__ == "__main__":
    main()

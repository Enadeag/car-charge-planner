"""The Windows icon holds the SVG icon at every size Windows picks from."""

import os

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

import struct
import sys
from pathlib import Path

from PySide6.QtGui import QImage

from car_charge_planner.platform.windows import ICON

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "tools"))
import make_ico  # noqa: E402


def images_in(ico: bytes) -> dict[int, bytes]:
    count = struct.unpack_from("<H", ico, 4)[0]
    found = {}
    for i in range(count):
        width, _h, _c, _r, _planes, _bits, length, offset = struct.unpack_from("<BBBBHHII", ico, 6 + 16 * i)
        found[width or 256] = ico[offset : offset + length]
    return found


def test_the_icon_has_every_size_and_shows_the_disc_and_the_bolt(qapp):
    found = images_in(ICON.read_bytes())
    assert sorted(found) == [16, 20, 24, 32, 40, 48, 64, 256]
    for size, png in found.items():
        image = QImage.fromData(png)
        assert (image.width(), image.height()) == (size, size)
        assert image.pixelColor(0, 0).alpha() == 0  # transparent outside the disc
        assert image.pixelColor(int(size * 0.2), size // 2).name() == "#3b82c4"  # the disc, left of the bolt
        assert image.pixelColor(size // 2 - size // 20, size // 2).name() == "#ffffff"  # the bolt


def test_the_committed_icon_is_the_current_svg(qapp):
    svg = ICON.with_suffix(".svg")
    for size, png in images_in(ICON.read_bytes()).items():
        committed = QImage.fromData(png).convertToFormat(QImage.Format.Format_ARGB32)
        fresh = QImage.fromData(make_ico.render_png(svg, size)).convertToFormat(QImage.Format.Format_ARGB32)
        # a renderer may differ a little in the edge pixels between systems; a changed drawing differs far more
        wrong = sum(
            max(abs(a - b) for a, b in zip(committed.pixelColor(x, y).getRgb(), fresh.pixelColor(x, y).getRgb(), strict=True)) > 16
            for x in range(size) for y in range(size)
        )
        assert wrong < size * 2, f"the SVG changed: run tools/make_ico.py ({size}px differs)"

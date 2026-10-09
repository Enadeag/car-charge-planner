"""Where the tray popup goes for a taskbar or menu bar on each screen edge. Geometries are logical pixels, as Qt reports them."""

import pytest
from PySide6.QtCore import QPoint, QRect, QSize

from car_charge_planner.placement import keep_inside, panel_edge, popup_position

POPUP = QSize(440, 600)

PLACEMENTS = {
    # name: (usable area of the screen, tray icon, expected top left of the popup)
    "windows, taskbar at the bottom, 100 %": (QRect(0, 0, 1920, 1040), QRect(1800, 1048, 24, 24), QPoint(1472, 432)),
    "windows, taskbar at the bottom, 150 % (1280x720 logical)": (QRect(0, 0, 1280, 680), QRect(1200, 688, 16, 16), QPoint(832, 72)),
    "windows, taskbar at the bottom, 200 % (3840x2160 physical, 1920x1080 logical)": (QRect(0, 0, 1920, 1032), QRect(1700, 1040, 16, 16), QPoint(1472, 424)),
    "windows, taskbar at the top": (QRect(0, 40, 1920, 1040), QRect(1800, 8, 24, 24), QPoint(1472, 48)),
    "windows, taskbar on the left": (QRect(48, 0, 1872, 1080), QRect(12, 1030, 24, 24), QPoint(56, 472)),
    "windows, taskbar on the right": (QRect(0, 0, 1872, 1080), QRect(1884, 1030, 24, 24), QPoint(1424, 472)),
    "windows, auto-hidden taskbar at the bottom": (QRect(0, 0, 1920, 1080), QRect(900, 1056, 24, 24), QPoint(691, 472)),
    "windows, taskbar at the bottom of a second screen": (QRect(1920, 0, 1920, 1040), QRect(3700, 1048, 24, 24), QPoint(3392, 432)),
    "macos, menu bar at the top": (QRect(0, 24, 1440, 876), QRect(1200, 0, 24, 24), QPoint(991, 32)),
    "macos, icon near the left edge of the menu bar": (QRect(0, 24, 1440, 876), QRect(2, 0, 24, 24), QPoint(8, 32)),
    "an icon in the middle of the taskbar: centred on it": (QRect(0, 0, 1920, 1040), QRect(900, 1048, 24, 24), QPoint(691, 432)),
}


@pytest.mark.parametrize("area, anchor, expected", PLACEMENTS.values(), ids=PLACEMENTS)
def test_the_popup_sits_against_the_panel_with_the_icon(area, anchor, expected):
    assert popup_position(anchor, POPUP, area) == expected


def test_a_popup_taller_than_the_usable_area_keeps_its_top_on_screen():
    assert popup_position(QRect(900, 1048, 24, 24), QSize(440, 2000), QRect(0, 0, 1920, 1040)) == QPoint(691, 8)


@pytest.mark.parametrize("area, anchor, expected", PLACEMENTS.values(), ids=PLACEMENTS)
def test_the_popup_never_leaves_the_usable_area(area, anchor, expected):
    position = popup_position(anchor, POPUP, area)
    assert area.contains(QRect(position, POPUP))


@pytest.mark.parametrize(
    "anchor, edge",
    [
        (QRect(1800, 1048, 24, 24), "bottom"),
        (QRect(1800, 8, 24, 24), "top"),
        (QRect(12, 1030, 24, 24), "left"),  # the bottom of a left taskbar is nearer the bottom edge than the left one
        (QRect(1884, 1030, 24, 24), "right"),
    ],
)
def test_the_panel_edge_is_where_the_icon_lies_outside_the_usable_area(anchor, edge):
    area = {"bottom": QRect(0, 0, 1920, 1040), "top": QRect(0, 40, 1920, 1040), "left": QRect(48, 0, 1872, 1080), "right": QRect(0, 0, 1872, 1080)}
    assert panel_edge(anchor, area[edge]) == edge


def test_a_position_remembered_on_another_screen_is_pulled_back_onto_this_one():
    assert keep_inside(QPoint(3000, -50), POPUP, QRect(0, 0, 1920, 1080)) == QPoint(1480, 0)

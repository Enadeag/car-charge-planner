import importlib
import logging
import os
import sys

import pytest

from car_charge_planner.notifier import (
    APP_NAME,
    DESKTOP_ENTRY,
    FALLBACK_ICON,
    ICON_NAME,
    NOTIFY_SIGNATURE,
    JeepneyBus,
    Notifier,
    NotifyError,
    default_icon,
)


class FakeBus:
    def __init__(self, fail=None):
        self.calls = []
        self.fail = fail

    def notify(self, *args):
        self.calls.append(args)
        if self.fail:
            raise self.fail
        return 40 + len(self.calls)


def test_the_call_carries_the_app_name_icon_text_action_hint_and_expiry():
    bus = FakeBus()
    Notifier(bus, icon="my-icon").send("Charge window 10:00-15:00", "Set the current to 7 A", expire_ms=15000)
    app_name, replaces_id, icon, summary, body, actions, hints, expire = bus.calls[0]
    assert (app_name, replaces_id, icon) == (APP_NAME, 0, "my-icon")
    assert summary == "Charge window 10:00-15:00" and body == "Set the current to 7 A"
    assert actions == ["default", "Open"]  # a click on the notification
    assert hints == {"desktop-entry": ("s", DESKTOP_ENTRY)}  # ties it to the launcher entry
    assert expire == 15000


def test_send_returns_the_id_and_remembers_it():
    notifier = Notifier(FakeBus(), icon="x")
    assert notifier.send("a", "b") == 41 and notifier.send("c", "d") == 42
    assert notifier.sent_ids == [41, 42]


def test_only_the_latest_ids_are_kept():
    notifier = Notifier(FakeBus(), icon="x")
    for _ in range(80):
        notifier.send("a", "b")
    assert len(notifier.sent_ids) == 50 and notifier.sent_ids[-1] == 120


@pytest.mark.parametrize("error", [OSError("no session bus"), NotifyError("ServiceUnknown"), ImportError("no jeepney"), TimeoutError()])
def test_a_failure_never_reaches_the_app(error):
    notifier = Notifier(FakeBus(fail=error), icon="x")
    assert notifier.send("a", "b") is None
    assert notifier.sent_ids == []


def test_a_missing_service_is_logged_once_not_every_minute(caplog):
    notifier = Notifier(FakeBus(fail=OSError("down")), icon="x")
    with caplog.at_level(logging.WARNING):
        for _ in range(5):
            notifier.send("a", "b")
    assert len([r for r in caplog.records if "Could not show a notification" in r.message]) == 1


@pytest.mark.linux
def test_the_message_is_built_with_the_exact_types_the_protocol_needs():
    pytest.importorskip("jeepney")
    message = JeepneyBus.build_message(APP_NAME, 0, "icon", "title", "body", ["default", "Open"], {"desktop-entry": ("s", "x")}, 5000)
    assert message.header.fields
    assert message.body[0] == APP_NAME and message.body[1] == 0
    assert NOTIFY_SIGNATURE == "susssasa{sv}i"  # u for the id: QtDBus cannot send this from Python
    from jeepney.low_level import HeaderFields

    assert message.header.fields[HeaderFields.signature] == NOTIFY_SIGNATURE
    assert message.header.fields[HeaderFields.member] == "Notify"
    assert message.header.fields[HeaderFields.destination] == "org.freedesktop.Notifications"


@pytest.mark.linux
def test_an_error_reply_from_the_service_becomes_a_notify_error(monkeypatch):
    pytest.importorskip("jeepney")
    from types import SimpleNamespace

    from jeepney import MessageType

    reply = SimpleNamespace(header=SimpleNamespace(message_type=MessageType.error), body=("boom",))

    class Conn:
        def __enter__(self):
            return self

        def __exit__(self, *a):
            return False

        def send_and_get_reply(self, message, timeout):
            return reply

    monkeypatch.setattr("jeepney.io.blocking.open_dbus_connection", lambda bus: Conn())
    with pytest.raises(NotifyError):
        JeepneyBus().notify(APP_NAME, 0, "i", "t", "b", [], {}, 1000)


@pytest.mark.linux
def test_a_success_reply_gives_the_notification_id(monkeypatch):
    pytest.importorskip("jeepney")
    from types import SimpleNamespace

    from jeepney import MessageType

    reply = SimpleNamespace(header=SimpleNamespace(message_type=MessageType.method_return), body=(77,))

    class Conn:
        def __enter__(self):
            return self

        def __exit__(self, *a):
            return False

        def send_and_get_reply(self, message, timeout):
            return reply

    monkeypatch.setattr("jeepney.io.blocking.open_dbus_connection", lambda bus: Conn())
    assert JeepneyBus().notify(APP_NAME, 0, "i", "t", "b", [], {}, 1000) == 77


def test_the_icon_is_the_apps_own_once_installed_and_a_stock_one_before(tmp_path, monkeypatch):
    monkeypatch.setenv("XDG_DATA_HOME", str(tmp_path))
    assert default_icon() == FALLBACK_ICON
    icons = tmp_path / "icons" / "hicolor" / "scalable" / "apps"
    icons.mkdir(parents=True)
    (icons / f"{ICON_NAME}.svg").write_text("<svg/>", encoding="utf-8")
    assert default_icon() == ICON_NAME


def test_the_tray_app_announces_through_the_notifier(cfg, tmp_path, monkeypatch):
    from datetime import date

    from PySide6.QtWidgets import QApplication

    from car_charge_planner.engine import run_here
    from car_charge_planner.forecast.service import ForecastResult
    from conftest import FakeClock, FakeNotifier, FixedForecast, TrayApp, at, forecast_from

    os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")
    QApplication.instance() or QApplication([])
    monkeypatch.setenv("XDG_STATE_HOME", str(tmp_path))
    fake = FakeNotifier()
    day = date(2026, 7, 3)
    forecast = FixedForecast(ForecastResult(forecast_from([(day, 900)]), at(day, 6), True, False))
    app = TrayApp(
        cfg, tmp_path / "config.toml", notifier=fake, forecasts=forecast, clock=FakeClock(at(day, 8, 50)), run_in_background=run_here
    )
    app.shell.start()
    assert len(fake.sent) == 1 and fake.sent[0][0].startswith("Charge window 09:00")


@pytest.mark.parametrize("name", ["notifier", "notification_actions", "controller", "main"])
def test_the_app_imports_without_jeepney(monkeypatch, name):
    package = importlib.import_module("car_charge_planner")
    importlib.import_module(f"car_charge_planner.{name}")  # not imported yet when this test runs first or alone
    monkeypatch.setitem(sys.modules, "jeepney", None)  # an import of it now fails, as on a system where it is not installed
    monkeypatch.delitem(sys.modules, f"car_charge_planner.{name}")
    monkeypatch.setattr(package, name, getattr(package, name))  # the fresh import replaces this attribute; put the module the other tests use back
    importlib.import_module(f"car_charge_planner.{name}")

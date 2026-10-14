"""The Settings window, the set-up wizard and the About dialog: which are open, and what finishing them does."""

from __future__ import annotations

from collections.abc import Callable

from car_charge_planner.planner.model import Config
from car_charge_planner.storage.state import mark_seen

from .about import AboutDialog
from .controller import Controller
from .i18n import _
from .settings.window import SettingsWindow
from .setup.wizard import SetupWizard

SETUP_OFFER = "set-up-offered"  # the one-time notification offering set-up to a hidden start


class AppWindows:
    """Owned by a shell; `show_plan` is what finishing the set-up shows."""

    def __init__(self, controller: Controller, show_plan: Callable[[], None]) -> None:
        self.controller = controller
        self.show_plan = show_plan
        self.settings_window: SettingsWindow | None = None
        self.setup_wizard: SetupWizard | None = None
        self.about_dialog: AboutDialog | None = None

    def open_settings(self, tab: str | None = None) -> None:
        """One Settings window at a time: opening it again brings the open one forward."""
        controller = self.controller
        if self.settings_window is None or not self.settings_window.isVisible():
            self.settings_window = SettingsWindow(
                controller.engine.cfg, controller.config_file, controller.apply_config, on_setup=self.open_setup
            )
        if tab:
            self.settings_window.show_tab(tab)
        self.settings_window.show_centred()

    def open_about(self) -> None:
        if self.about_dialog is None:
            self.about_dialog = AboutDialog()
        self.about_dialog.show()
        self.about_dialog.raise_()
        self.about_dialog.activateWindow()

    def open_setup(self) -> None:
        """One set-up wizard at a time, started from the current config."""
        controller = self.controller
        if self.setup_wizard is None or not self.setup_wizard.isVisible():
            self.setup_wizard = SetupWizard(controller.engine.cfg, controller.config_file, self._setup_finished)
        self.setup_wizard.show()
        self.setup_wizard.raise_()
        self.setup_wizard.activateWindow()

    def _setup_finished(self, cfg: Config) -> None:
        self.controller.apply_config(cfg)
        self.show_plan()

    def offer_setup(self, background: bool) -> None:
        """At a start that is not set up: open the wizard, or, when started hidden (at login), only offer it."""
        if self.controller.engine.cfg.is_set_up:
            return
        if not background:
            self.open_setup()
        elif mark_seen(SETUP_OFFER):  # once: a notification at every login would nag
            self.controller.notifier.send(
                _("Set-up needed"), _("Click here to describe your solar panels and car. It takes a few minutes.")
            )

    def notification_clicked(self) -> None:
        """A click on a notification opens the wizard while not set up (the offer asked for it), else the plan."""
        if self.controller.engine.cfg.is_set_up:
            self.show_plan()
        else:
            self.open_setup()

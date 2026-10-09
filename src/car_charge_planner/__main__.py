"""The command line: `car-charge-planner` (or `python -m car_charge_planner`) with an optional command."""

import sys

from .i18n import _, init

COMMANDS = "run | test-notification | install-launcher | uninstall-launcher | install-autostart | uninstall-autostart"


def main(argv: list[str] | None = None) -> int:
    """Run a command and return the exit code. With no command (or only flags such as --background) the app runs."""
    arguments = sys.argv[1:] if argv is None else argv
    init()  # these commands run without Qt, so choose the language here
    command = arguments[0] if arguments and not arguments[0].startswith("-") else "run"

    if command == "install-autostart":
        from .identity import launch_command
        from .platform import start_at_login

        print(_("Autostart entry written to {path}").format(path=start_at_login().enable(launch_command())))
    elif command == "uninstall-autostart":
        from .platform import start_at_login

        print(_("Autostart entry removed") if start_at_login().disable() else _("No autostart entry found"))
    elif command == "install-launcher":
        from .identity import launch_command
        from .platform import menu_entry

        entry = menu_entry()
        if not entry.own_entry:
            print(_("Nothing to install: on this system the application is its own menu entry"))
        else:
            print(_("Launcher entry written to:") + "\n" + "\n".join(f"  {item}" for item in entry.install(launch_command())))
    elif command == "uninstall-launcher":
        from .platform import menu_entry

        entry = menu_entry()
        removed = entry.uninstall()
        if not entry.own_entry:
            print(_("Nothing to remove: on this system the application is its own menu entry"))
        else:
            print(_("Removed:") + "\n" + "\n".join(f"  {path}" for path in removed) if removed else _("No launcher entry found"))
    elif command == "test-notification":
        from .selftest import run

        return run()
    elif command == "run":
        from .main import main as run_app

        return run_app()
    else:
        print(_("Unknown command {command!r}. Use: {commands}").format(command=command, commands=COMMANDS), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())

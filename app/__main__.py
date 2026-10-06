import sys
from pathlib import Path

from .i18n import _, init

init()  # these commands run without Qt, so choose the language here

command = sys.argv[1] if len(sys.argv) > 1 and not sys.argv[1].startswith("-") else "run"  # flags such as --background mean "run"

if command == "install-autostart":
    from .autostart import install

    print(_("Autostart entry written to {path}").format(path=install(Path(sys.executable), Path.cwd())))
elif command == "uninstall-autostart":
    from .autostart import uninstall

    print(_("Autostart entry removed") if uninstall() else _("No autostart entry found"))
elif command == "install-launcher":
    from .launcher import install

    entry, icon = install(Path(sys.executable), Path.cwd())
    print(_("Launcher entry written to {entry}\nIcon written to {icon}").format(entry=entry, icon=icon))
elif command == "uninstall-launcher":
    from .launcher import uninstall

    removed = uninstall()
    print(_("Removed:") + "\n" + "\n".join(f"  {path}" for path in removed) if removed else _("No launcher entry found"))
elif command == "test-notification":
    from .selftest import run

    sys.exit(run())
elif command == "run":
    from .main import main

    sys.exit(main())
else:
    sys.exit(
        _(
            "Unknown command {command!r}. "
            "Use: run | test-notification | install-launcher | uninstall-launcher | install-autostart | uninstall-autostart"
        ).format(command=command)
    )

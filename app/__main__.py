import sys
from pathlib import Path

command = sys.argv[1] if len(sys.argv) > 1 else "run"

if command == "install-autostart":
    from .autostart import install

    print(f"Autostart entry written to {install(Path(sys.executable), Path.cwd())}")
elif command == "uninstall-autostart":
    from .autostart import uninstall

    print("Autostart entry removed" if uninstall() else "No autostart entry found")
elif command == "install-launcher":
    from .launcher import install

    entry, icon = install(Path(sys.executable), Path.cwd())
    print(f"Launcher entry written to {entry}\nIcon written to {icon}")
elif command == "uninstall-launcher":
    from .launcher import uninstall

    removed = uninstall()
    print("Removed:\n" + "\n".join(f"  {path}" for path in removed) if removed else "No launcher entry found")
elif command == "run":
    from .tray import main

    sys.exit(main())
else:
    sys.exit(
        f"Unknown command {command!r}. Use: run | install-launcher | uninstall-launcher | install-autostart | uninstall-autostart"
    )

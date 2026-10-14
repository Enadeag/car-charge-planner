"""The engine is the only writer of the stored data, beside the desktop app writing its own settings (app.toml) from its
wiring: no client module reads or writes a data file or the keychain.

The check reads the clients' source rather than running them against a filesystem that refuses writes: it covers every
branch, also those no test reaches (an error path, a one-time hint), and Qt's own files do not get in the way.
"""

import ast
from pathlib import Path

import pytest

PACKAGE = Path(__file__).resolve().parent.parent / "src" / "car_charge_planner"
CLIENTS = sorted(
    [
        *PACKAGE.glob("settings/*.py"),
        *PACKAGE.glob("setup/*.py"),
        *PACKAGE.glob("shells/*.py"),
        *PACKAGE.glob("quick/*.py"),
        *(PACKAGE / name for name in ("popup.py", "planview.py", "app_windows.py", "controller.py")),
    ]
)

# modules that exist to find, read or write the stored data
FORBIDDEN_MODULES = {
    "car_charge_planner.config_file",
    "car_charge_planner.paths",
    "car_charge_planner.config.io",
    "car_charge_planner.engine.store",
    "json",
    "keyring",
    "pickle",
    "shelve",
    "shutil",
    "sqlite3",
    "tempfile",
    "tomllib",
}
# modules that also hold plain data types: only those may be imported from them
ONLY_TYPES = {
    "car_charge_planner.storage.state": {"BatteryReading", "State"},
    "car_charge_planner.forecast.secret_store": {"Secrets"},
}
FORBIDDEN_NAMES = {
    "car_charge_planner.config.model": {"load_config", "read_toml", "stored_schema"},
    "car_charge_planner.engine": {"FileStore"},  # a client asks the engine; it does not open the store itself
    # the app's own settings are stored by the desktop app's wiring (main.py); a client saves them through DesktopSettings
    "car_charge_planner.app_settings": {"AppSettingsFile", "adopt_interface", "load_app_settings", "write_app_settings", "app_settings_path"},
}
FILE_METHODS = {"open", "read_text", "read_bytes", "write_text", "write_bytes", "touch", "mkdir", "unlink", "rmdir"}


def module_name(path: Path) -> str:
    parts = path.relative_to(PACKAGE.parent).with_suffix("").parts
    return ".".join(parts[:-1] if parts[-1] == "__init__" else parts)


def imported(node: ast.ImportFrom, path: Path) -> str:
    if not node.level:
        return node.module or ""
    package = module_name(path).split(".")
    if path.name != "__init__.py":
        package = package[:-1]
    base = package[: len(package) - (node.level - 1)]
    return ".".join([*base, node.module] if node.module else base)


def problems(path: Path) -> list[str]:
    found = []
    for node in ast.walk(ast.parse(path.read_text(encoding="utf-8"))):
        where = f"{path.relative_to(PACKAGE).as_posix()}:{getattr(node, 'lineno', '?')}"
        if isinstance(node, ast.Import):
            found += [f"{where} imports {alias.name}" for alias in node.names if alias.name.split(".")[0] in FORBIDDEN_MODULES or alias.name in FORBIDDEN_MODULES]
        elif isinstance(node, ast.ImportFrom):
            module = imported(node, path)
            names = {alias.name for alias in node.names}
            if module in FORBIDDEN_MODULES or module.split(".")[0] in FORBIDDEN_MODULES:
                found.append(f"{where} imports from {module}")
            elif module in ONLY_TYPES and names - ONLY_TYPES[module]:
                found.append(f"{where} imports {sorted(names - ONLY_TYPES[module])} from {module}")
            elif names & FORBIDDEN_NAMES.get(module, set()):
                found.append(f"{where} imports {sorted(names & FORBIDDEN_NAMES[module])} from {module}")
        elif isinstance(node, ast.Call):
            function = node.func
            if isinstance(function, ast.Name) and function.id == "open":
                found.append(f"{where} opens a file")
            elif isinstance(function, ast.Attribute) and function.attr in FILE_METHODS:
                found.append(f"{where} calls .{function.attr}()")
            elif isinstance(function, ast.Attribute) and isinstance(function.value, ast.Name) and function.value.id == "os":
                found.append(f"{where} calls os.{function.attr}()")
    return found


def test_the_scan_covers_the_clients():
    names = {path.relative_to(PACKAGE).as_posix() for path in CLIENTS}
    assert {"settings/window.py", "settings/forecast_tab.py", "setup/wizard.py", "shells/window.py", "controller.py", "quick/bridge.py"} <= names
    assert all(path.exists() for path in CLIENTS)


@pytest.mark.parametrize("path", CLIENTS, ids=lambda path: path.relative_to(PACKAGE).as_posix())
def test_a_client_touches_no_stored_data(path):
    assert problems(path) == []


QML_FILES = sorted(PACKAGE.glob("quick/**/*.qml"))
# QML's own ways to reach files, storage or the network: the QML side shows what the bridge gives it, nothing more
QML_FORBIDDEN = ("import QtCore", "import Qt.labs.settings", "import QtQuick.LocalStorage", "import Qt.labs.folderlistmodel",
                 "import QtQuick.Dialogs", "XMLHttpRequest", "Qt.openUrlExternally", "LocalStorage.", "FileDialog", "FolderDialog")


def test_the_scan_covers_the_qml_files():
    assert {"Dashboard.qml", "Theme.qml"} <= {path.name for path in QML_FILES}


@pytest.mark.parametrize("path", QML_FILES, ids=lambda path: path.name)
def test_a_qml_file_touches_no_stored_data(path):
    text = path.read_text(encoding="utf-8")
    assert [word for word in QML_FORBIDDEN if word in text] == []

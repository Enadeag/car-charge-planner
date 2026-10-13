# PyInstaller spec for the Windows build: one folder (onedir) holding two programs that share everything else.
# Build it with tools/build_windows.py, which compiles the translations first and wraps the folder in the installer.

import sys
import tomllib
from pathlib import Path

from PyInstaller.utils.win32.versioninfo import (
    FixedFileInfo, StringFileInfo, StringStruct, StringTable, VarFileInfo, VarStruct, VSVersionInfo,
)

ROOT = Path(SPECPATH).resolve().parent.parent
SOURCE = ROOT / "src"
PACKAGE = SOURCE / "car_charge_planner"
sys.path.insert(0, str(SOURCE))
from car_charge_planner.identity import APP_NAME, COMMAND, COMMENT, CONSOLE_SUFFIX

VERSION = tomllib.loads((ROOT / "pyproject.toml").read_text(encoding="utf-8"))["project"]["version"]
ICON = PACKAGE / "assets" / "car-charge-planner.ico"

datas = [(str(PACKAGE / "assets"), "car_charge_planner/assets")]
datas += [(str(mo), str(mo.parent.relative_to(SOURCE))) for mo in PACKAGE.glob("locale/*/LC_MESSAGES/*.mo")]

a = Analysis(
    [str(Path(SPECPATH) / "launch.py")],
    pathex=[str(SOURCE)],
    datas=datas,
    excludes=[
        "babel",  # only the translation tools use it, at build time
        "car_charge_planner.i18n_tools",
        "setuptools", "pkg_resources", "_distutils_hack",  # build tools some hooks pull in; nothing imports them at run time
        "jeepney", "secretstorage", "cryptography",  # D-Bus and the Secret Service: Linux only
        "tkinter", "unittest", "pydoc", "doctest", "pdb", "lib2to3", "sqlite3", "xmlrpc",
    ],
    noarchive=False,
)

# Qt ships files the app never loads; leaving them out saves about 25 MB. opengl32sw.dll is Mesa's software OpenGL, a
# fallback for OpenGL content (the app draws widgets only); the other plug-ins serve image formats and platforms it does not use.
UNUSED = (
    "opengl32sw.dll",
    "qdirect2d.dll", "qminimal.dll",
    "qgif.dll", "qjpeg.dll", "qtiff.dll", "qwebp.dll", "qwbmp.dll", "qicns.dll", "qtga.dll", "qpdf.dll",
    "qopensslbackend.dll", "qcertonlybackend.dll",  # QtNetwork's TLS plug-ins: the app's network use is a local socket
)


def wanted(entry) -> bool:
    name = Path(entry[0]).name.lower()
    if name in UNUSED:
        return False
    if "translations" in Path(entry[0]).parts and not name.startswith("qtbase_"):
        return False  # only Qt's base texts (dialog buttons) are installed as translations by the app
    return True


a.binaries = [entry for entry in a.binaries if wanted(entry)]
a.datas = [entry for entry in a.datas if wanted(entry)]

version_tuple = tuple(int(part) for part in (VERSION.split(".") + ["0"] * 4)[:4])
version_info = VSVersionInfo(
    ffi=FixedFileInfo(filevers=version_tuple, prodvers=version_tuple),
    kids=[
        StringFileInfo([StringTable("040904B0", [
            StringStruct("FileDescription", APP_NAME),  # the name Windows shows for the program (Task Manager, notifications)
            StringStruct("ProductName", APP_NAME),
            StringStruct("FileVersion", VERSION),
            StringStruct("ProductVersion", VERSION),
            StringStruct("Comments", COMMENT),
            StringStruct("LegalCopyright", "GPL-3.0-or-later"),
            StringStruct("OriginalFilename", f"{COMMAND}.exe"),
        ])]),
        VarFileInfo([VarStruct("Translation", [0x0409, 1200])]),
    ],
)

pyz = PYZ(a.pure)


def program(name: str, console: bool):
    return EXE(
        pyz, a.scripts, [], exclude_binaries=True, name=name, console=console, icon=str(ICON), version=version_info,
        upx=False,  # UPX-packed programs are flagged by antivirus far more often
    )


# The windowed program is what the Start menu and the login entry start: it opens no console window. Windows does not
# connect a windowed program's output to the terminal it was started from, so the commands that print
# (test-notification, install-autostart, ...) come with a console twin.
COLLECT(
    program(COMMAND, console=False),
    program(COMMAND + CONSOLE_SUFFIX, console=True),
    a.binaries,
    a.datas,
    name=COMMAND,
    upx=False,
)

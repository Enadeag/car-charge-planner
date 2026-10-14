"""Build the Windows installer: `python tools/build_windows.py` on Windows, in an environment with the app, PyInstaller and Babel.

Writes the program folder to `dist/car-charge-planner/` and `dist/car-charge-planner-<version>-setup.exe` with its
SHA-256 checksum beside it. Needs Inno Setup 6 (ISCC.exe on the PATH, in its usual folders, or named by `ISCC`).
"""

from __future__ import annotations

import argparse
import hashlib
import os
import shutil
import subprocess
import sys
import tomllib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "src"))
from car_charge_planner.identity import APP_NAME, APP_USER_MODEL_ID, COMMAND, COMMENT, CONSOLE_SUFFIX  # noqa: E402

PACKAGE = ROOT / "src" / "car_charge_planner"
DIST = ROOT / "dist"
WORK = ROOT / "build" / "windows"
SPEC = ROOT / "installers" / "windows" / "car-charge-planner.spec"
SCRIPT = ROOT / "installers" / "windows" / "car-charge-planner.iss"


def project() -> dict:
    return tomllib.loads((ROOT / "pyproject.toml").read_text(encoding="utf-8"))["project"]


def run(*command: str | Path) -> None:
    print("+", subprocess.list2cmdline([str(part) for part in command]), flush=True)
    subprocess.run([str(part) for part in command], cwd=ROOT, check=True)


def iscc() -> Path:
    env = os.environ
    folders = [Path(env["LOCALAPPDATA"], "Programs")] if "LOCALAPPDATA" in env else []  # where winget installs it for one user
    folders += [Path(env[name]) for name in ("ProgramFiles(x86)", "ProgramFiles") if name in env]
    candidates = [env.get("ISCC"), shutil.which("ISCC"), *(str(folder / "Inno Setup 6" / "ISCC.exe") for folder in folders)]
    for candidate in candidates:
        if candidate and Path(candidate).is_file():
            return Path(candidate)
    raise SystemExit("Inno Setup 6 was not found: install it, or set ISCC to the path of ISCC.exe")


def checksum(file: Path) -> Path:
    """`<hash> *<name>`, the form `sha256sum -c` reads; PowerShell's Get-FileHash shows the same hash in capitals."""
    digest = hashlib.sha256(file.read_bytes()).hexdigest()
    target = file.with_name(file.name + ".sha256")
    target.write_text(f"{digest} *{file.name}\n", encoding="utf-8")
    return target


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bundle-only", action="store_true", help="build the program folder, not the installer")
    args = parser.parse_args(argv)
    version, homepage = project()["version"], project()["urls"]["Homepage"]

    if any(PACKAGE.glob("locale/*/LC_MESSAGES/*.po")):
        run(sys.executable, "-m", "car_charge_planner.i18n_tools", "compile")
    run(sys.executable, "-m", "PyInstaller", "--noconfirm", "--clean", "--distpath", DIST, "--workpath", WORK / "pyinstaller", SPEC)
    bundle = DIST / COMMAND
    print(f"Program folder: {bundle}")
    if args.bundle_only:
        return 0

    notices = WORK / "THIRD-PARTY-NOTICES.txt"
    run(sys.executable, ROOT / "tools" / "third_party_notices.py", notices)
    defines = {
        "AppVersion": version,
        "AppName": APP_NAME,
        "AppUserModelID": APP_USER_MODEL_ID,
        "ExeName": f"{COMMAND}.exe",
        "CliExeName": f"{COMMAND}{CONSOLE_SUFFIX}.exe",
        "Comment": COMMENT,
        "Homepage": homepage,
        "BundleDir": bundle,
        "NoticesFile": notices,
        "LicenseFile": ROOT / "LICENSE",
        "NoticeFile": ROOT / "NOTICE",
        "TermsFile": ROOT / "ADDITIONAL-TERMS.md",
        "IconFile": PACKAGE / "assets" / "car-charge-planner.ico",
        "OutputDir": DIST,
    }
    run(iscc(), "/Q", *(f"/D{name}={value}" for name, value in defines.items()), SCRIPT)
    installer = DIST / f"car-charge-planner-{version}-setup.exe"
    print(f"Installer: {installer}\nChecksum: {checksum(installer).read_text(encoding='utf-8').strip()}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

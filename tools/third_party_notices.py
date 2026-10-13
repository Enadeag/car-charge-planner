"""Write `THIRD-PARTY-NOTICES.txt` for a packaged build from the metadata of the packages installed in this environment.

Run it with the interpreter the build bundles: `python tools/third_party_notices.py <output file>`. It lists every
package the app needs on this system (its requirements, followed recursively), Python itself and the PyInstaller
program that starts the bundle, each with its version, licence, address and licence text.
"""

from __future__ import annotations

import platform
import sys
import sysconfig
from importlib.metadata import Distribution, PackageNotFoundError, distribution
from pathlib import Path

from packaging.markers import default_environment
from packaging.requirements import Requirement
from packaging.utils import canonicalize_name

ROOT = Path(__file__).resolve().parent.parent
APP = "car-charge-planner"
LGPL_TEXT = ROOT / "installers" / "licenses" / "LGPL-3.0-only.txt"  # the SPDX text: the LGPL followed by the GPL it builds on
QT_PACKAGES = {"pyside6-essentials", "shiboken6"}
STARTER = "pyinstaller"  # only its small start-up program (the bootloader) is in the bundle, none of its Python code


def requirements(root: str = APP, environment: dict[str, str] | None = None) -> list[Distribution]:
    """The installed distributions `root` needs at run time on this system (extras left out), sorted by name."""
    env = {**default_environment(), "extra": "", **(environment or {})}
    found: dict[str, Distribution] = {}
    pending = [root]
    while pending:
        for line in _distribution(pending.pop()).requires or []:
            requirement = Requirement(line)
            key = canonicalize_name(requirement.name)
            if key not in found and (requirement.marker is None or requirement.marker.evaluate(env)):
                found[key] = _distribution(requirement.name)
                pending.append(requirement.name)
    return sorted(found.values(), key=lambda d: canonicalize_name(d.metadata["Name"]))


def _distribution(name: str) -> Distribution:
    try:
        return distribution(name)
    except PackageNotFoundError:
        raise SystemExit(f"{name} is required but not installed: the notices would be incomplete") from None


def licence(dist: Distribution) -> str:
    meta = dist.metadata
    expression, text = meta.get("License-Expression"), (meta.get("License") or "").strip()
    if expression:
        return expression
    if text and "\n" not in text:
        return text
    classifiers = [c.rsplit(" :: ", 1)[-1] for c in meta.get_all("Classifier") or [] if c.startswith("License ::")]
    return " / ".join(classifiers) or "see the licence text"


def address(dist: Distribution) -> str:
    meta = dist.metadata
    urls = dict(entry.split(", ", 1) for entry in meta.get_all("Project-URL") or [] if ", " in entry)
    lowered = {key.lower(): value for key, value in urls.items()}
    for key in ("source", "repository", "source code", "homepage"):
        if key in lowered:
            return lowered[key]
    return meta.get("Home-page") or next(iter(urls.values()), "")


def licence_texts(dist: Distribution) -> list[str]:
    files = [f for f in dist.files or [] if any(word in f.name.upper() for word in ("LICEN", "COPYING", "NOTICE"))]
    return [Path(str(dist.locate_file(f))).read_text(encoding="utf-8", errors="replace").strip() for f in sorted(files, key=str)]


def python_licence() -> str:
    """Python's licence, which also carries the notices of what it bundles (OpenSSL, libffi, zlib, ...)."""
    for folder in (Path(sys.base_prefix), Path(sysconfig.get_path("stdlib"))):
        if (folder / "LICENSE.txt").is_file():
            return (folder / "LICENSE.txt").read_text(encoding="utf-8", errors="replace").strip()
    raise SystemExit("Python's LICENSE.txt was not found: the notices would be incomplete")


def qt_version() -> str:
    from PySide6.QtCore import qVersion

    return qVersion()


def entry(title: str, version: str, licence_name: str, url: str, body: list[str]) -> str:
    rule = "=" * 78
    return "\n".join([rule, f"{title} {version}", f"Licence: {licence_name}", f"Address: {url}", rule, "", *body, ""])


def qt_entry(dists: list[Distribution]) -> str:
    qt = qt_version()
    short = ".".join(qt.split(".")[:2])
    names = ", ".join(f"{d.metadata['Name']} {d.version}" for d in dists)
    body = [
        f"{names} and the Qt {qt} libraries they contain are used under the GNU Lesser General Public License,",
        "version 3 (LGPL-3.0-only); the full text follows.",
        "",
        "They are separate files in the program's folder (_internal\\PySide6 and _internal\\shiboken6): you may replace",
        "them with your own build of the same version. Their source code:",
        "  PySide6 and Shiboken: https://code.qt.io/cgit/pyside/pyside-setup.git/",
        f"  Qt {qt}: https://download.qt.io/official_releases/qt/{short}/{qt}/",
        "",
        LGPL_TEXT.read_text(encoding="utf-8").strip(),
    ]
    return entry("Qt for Python (PySide6, Shiboken) and Qt", qt, "LGPL-3.0-only", "https://www.qt.io/qt-for-python", body)


def notices(dists: list[Distribution], python_text: str, starter: Distribution | None) -> str:
    qt = [d for d in dists if canonicalize_name(d.metadata["Name"]) in QT_PACKAGES]
    others = [d for d in dists if d not in qt]
    parts = [
        f"Third-party software in {APP}",
        "",
        "The program is under the GNU General Public License, version 3 or later (LICENSE.txt), with the additional terms in ADDITIONAL-TERMS.txt. It bundles the software below, each under its own licence.",
        "",
        "Summary:",
        *(f"  {d.metadata['Name']} {d.version}: LGPL-3.0-only (offered under {licence(d)})" for d in qt),
        *(f"  {d.metadata['Name']} {d.version}: {licence(d)}" for d in others),
        f"  Python {platform.python_version()}: PSF-2.0",
        *([f"  PyInstaller bootloader {starter.version}: GPL-2.0-or-later with the bootloader exception"] if starter else []),
        "",
    ]
    if qt:
        parts.append(qt_entry(qt))
    for dist in others:
        parts.append(entry(dist.metadata["Name"], dist.version, licence(dist), address(dist), licence_texts(dist) or ["(no licence file in the package)"]))
    parts.append(entry("Python", platform.python_version(), "PSF-2.0", "https://www.python.org", [python_text]))
    if starter:
        body = [
            "The program files (car-charge-planner.exe, car-charge-planner-cli.exe) are PyInstaller's bootloader, whose",
            "licence allows it to start programs under any licence:",
            "",
            *licence_texts(starter),
        ]
        parts.append(entry("PyInstaller bootloader", starter.version, "GPL-2.0-or-later WITH Bootloader-exception", address(starter), body))
    return "\n".join(parts)


def main(argv: list[str] | None = None) -> int:
    args = sys.argv[1:] if argv is None else argv
    if len(args) != 1:
        print(__doc__, file=sys.stderr)
        return 2
    try:
        starter = distribution(STARTER)
    except PackageNotFoundError:
        starter = None  # not a PyInstaller build
    Path(args[0]).write_text(notices(requirements(), python_licence(), starter), encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

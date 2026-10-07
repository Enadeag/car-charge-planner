"""Developer commands for translations (needs Babel, a development dependency).

    python -m app.i18n_tools extract          # code -> app/locale/car-charge-planner.pot
    python -m app.i18n_tools init nl          # a new language, from the template
    python -m app.i18n_tools update           # the template -> every language's .po
    python -m app.i18n_tools compile          # .po -> .mo (what the app loads)

Run from the project root. `extract` is deterministic (a fixed date in the header, no line numbers), so the
committed template changes only when a text changes.
"""

from __future__ import annotations

import sys
from datetime import datetime, timezone
from pathlib import Path

from .i18n import DOMAIN, LOCALE_DIR

ROOT = Path(__file__).resolve().parent.parent
PACKAGE_DIR = Path(__file__).resolve().parent
POT = LOCALE_DIR / f"{DOMAIN}.pot"
FIXED_DATE = datetime(2026, 1, 1, tzinfo=timezone.utc)  # a template that changes only when the texts do
COMMENT_TAG = "i18n:"  # `# i18n: {n} is a number` before a text becomes a note for the translator

ISSUES_URL = "https://github.com/Enadeag/car-charge-planner/issues"  # where translators send problems with a text
HEADER_COMMENT = (
    f"# Translations template for {DOMAIN}.\n"
    "# Copyright (C) 2026 Kelvin Vroegop\n"
    f"# This file is distributed under the same license as the {DOMAIN} project (MIT).\n"
)

KEYWORDS = {
    "_": None,
    "ngettext": (1, 2),
    "pgettext": ((1, "c"), 2),
    "N_": None,
    "NC_": ((1, "c"), 2),
}


def _babel():
    from importlib.util import find_spec

    if find_spec("babel") is None:
        raise SystemExit("Babel is needed for this command: pip install babel")


# every package whose texts reach the user: the app itself and the libraries it shows messages from
SOURCE_DIRS = tuple(d for d in (ROOT / name for name in ("app", "planner", "forecast", "storage")) if d.is_dir())


def extract(package_dir: Path | None = None, pot: Path = POT, prefix: str = "app") -> int:
    """Write the template from every marked text. Returns how many messages.

    Without `package_dir` that is every directory in `SOURCE_DIRS`, each file named after its folder;
    with one, just that folder, its files named under `prefix` (what the tests use).
    """
    _babel()
    from babel.messages.catalog import Catalog
    from babel.messages.extract import extract_from_dir
    from babel.messages.pofile import write_po

    catalog = Catalog(
        project=DOMAIN,
        version="source",  # not the release number: that would change the template at every release
        header_comment=HEADER_COMMENT,
        msgid_bugs_address=ISSUES_URL,
        creation_date=FIXED_DATE,
        last_translator="",
        language_team="",
        charset="utf-8",
    )
    sources = [(package_dir, prefix)] if package_dir else [(d, d.name) for d in SOURCE_DIRS]
    for directory, name in sources:
        found = extract_from_dir(
            str(directory),
            method_map=[("**.py", "python")],
            options_map={"**.py": {}},
            keywords=KEYWORDS,
            comment_tags=(COMMENT_TAG,),
            strip_comment_tags=True,
        )
        for filename, lineno, message, comments, context in found:
            catalog.add(message, None, [(f"{name}/{filename}", lineno)], auto_comments=comments, context=context)
    pot.parent.mkdir(parents=True, exist_ok=True)
    with open(pot, "wb") as handle:
        write_po(handle, catalog, width=0, sort_by_file=True, include_lineno=False, include_previous=False)
    return len(catalog) if len(catalog) else 0


def _cli(*arguments: str) -> None:
    _babel()
    from babel.messages.frontend import CommandLineInterface

    status = CommandLineInterface().run(["pybabel", *arguments])
    if status:
        raise SystemExit(status)


def init(language: str, pot: Path = POT, locale_dir: Path = LOCALE_DIR) -> None:
    _cli("init", "-i", str(pot), "-d", str(locale_dir), "-D", DOMAIN, "-l", language)


def update(pot: Path = POT, locale_dir: Path = LOCALE_DIR) -> None:
    _cli("update", "-i", str(pot), "-d", str(locale_dir), "-D", DOMAIN)


def compile_catalogues(locale_dir: Path = LOCALE_DIR) -> None:
    _cli("compile", "-d", str(locale_dir), "-D", DOMAIN, "--statistics")


def main(argv: list[str] | None = None) -> int:
    args = sys.argv[1:] if argv is None else argv
    command = args[0] if args else ""
    if command == "extract":
        print(f"{extract()} messages written to {POT.relative_to(ROOT)}")
    elif command == "init" and len(args) == 2:
        init(args[1])
    elif command == "update":
        update()
    elif command == "compile":
        compile_catalogues()
    else:
        print(__doc__, file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

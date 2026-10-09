"""Translations (gettext): English is the source language, and a language is one catalogue file under `locale/`.

`_` translates now; `N_` and `NC_` (with a context) mark a module-level text to translate when shown, with `_` or
`translate`. The catalogue is looked up at call time, so a text made before `init()` is translated when shown.
"""

from __future__ import annotations

import gettext
import os
from collections.abc import Mapping, Sequence
from pathlib import Path

DOMAIN = "car-charge-planner"
LOCALE_DIR = Path(__file__).parent / "locale"


class _Active:
    translations: gettext.NullTranslations = gettext.NullTranslations()
    language: str = ""  # the catalogue in use; empty means untranslated English


def _(message: str) -> str:
    # gettext("") returns the catalogue's header, never what a caller means by it
    return _Active.translations.gettext(message) if message else message


def ngettext(singular: str, plural: str, n: int) -> str:
    return _Active.translations.ngettext(singular, plural, n)


def pgettext(context: str, message: str) -> str:
    return _Active.translations.pgettext(context, message)


def number(value: float, decimals: int = 0) -> str:
    """A number for use inside a text: fixed decimals, with the language's own decimal separator.

    Qt's input boxes follow the system locale by themselves; texts we build do not, so the separator is a translatable text.
    """
    text = f"{value:.{decimals}f}"
    separator = pgettext("decimal separator", ".")
    return text if separator == "." else text.replace(".", separator)


def N_(message: str) -> str:
    """Mark a text for extraction; translate it later, when it is shown."""
    return message


def NC_(context: str, message: str) -> tuple[str, str]:
    """`N_` with a context; translate with `translate`."""
    return (context, message)


def translate(marker: str | tuple[str, str]) -> str:
    if isinstance(marker, tuple):
        return pgettext(*marker)
    return _(marker)


def _clean(code: str) -> str | None:
    """'nl_NL.UTF-8@euro' or 'nl-NL' -> 'nl_NL'; the plain C locale means "no language" -> None."""
    code = code.strip().split("@")[0].split(".")[0].replace("-", "_")
    return None if code.lower() in ("", "c", "posix") else code


def _with_parents(code: str) -> list[str]:
    """'zh_Hant_TW' -> ['zh_Hant_TW', 'zh_Hant', 'zh']: a regional catalogue falls back to the general one."""
    parts = code.split("_")
    return ["_".join(parts[:i]) for i in range(len(parts), 0, -1)]


def _add(found: list[str], code: str | None) -> None:
    for candidate in _with_parents(code) if code else []:
        if candidate not in found:
            found.append(candidate)


def resolve_languages(environ: Mapping[str, str], qt_languages: Sequence[str] = ()) -> list[str]:
    """The languages to try, best first, from the environment the way gettext tools do it.

    The first of LC_ALL, LC_MESSAGES and LANG that is set gives the locale; the plain C locale means English
    whatever else is set. Otherwise the LANGUAGE preference list comes first, then the locale. With none set, Qt's system language.
    """
    effective = next((environ[k] for k in ("LC_ALL", "LC_MESSAGES", "LANG") if environ.get(k)), None)
    found: list[str] = []
    if effective is not None and _clean(effective) is None:
        return found  # C or POSIX: English on purpose
    for part in (environ.get("LANGUAGE") or "").split(":"):
        _add(found, _clean(part))
    if effective is not None:
        _add(found, _clean(effective))
    elif not found:
        # Qt's list is already ordered most specific first: keep that order, then add the parents
        given = [c for c in (_clean(code) for code in qt_languages) if c]
        found.extend(dict.fromkeys(given))
        for code in given:
            _add(found, code)
    return found


def system_qt_languages() -> list[str]:
    from PySide6.QtCore import QLocale

    return list(QLocale.system().uiLanguages())


def init(
    languages: Sequence[str] | None = None,
    localedir: Path | str | None = None,
    environ: Mapping[str, str] | None = None,
    qt_languages: Sequence[str] | None = None,
) -> str:
    """Choose the catalogue. Returns the language in use ('' for untranslated English). Never raises.

    `languages` forces a choice (a user setting, a test); otherwise it comes from the environment.
    """
    if languages is None:
        env = os.environ if environ is None else environ
        wanted = resolve_languages(env, system_qt_languages() if qt_languages is None else qt_languages)
    else:
        wanted = []
        for code in languages:
            _add(wanted, _clean(code))
    translations = gettext.NullTranslations()
    language = ""
    try:
        path = gettext.find(DOMAIN, str(localedir or LOCALE_DIR), languages=wanted)
        if path:
            with open(path, "rb") as handle:
                translations = gettext.GNUTranslations(handle)
            language = Path(path).parent.parent.name
    except (OSError, ValueError):  # a damaged catalogue must not stop the app: English is always there
        translations, language = gettext.NullTranslations(), ""
    _Active.translations, _Active.language = translations, language
    return language


def available_languages(localedir: Path | str | None = None) -> list[str]:
    """The languages with a compiled catalogue, sorted (English is the source and needs none)."""
    root = Path(localedir or LOCALE_DIR)
    if not root.is_dir():
        return []
    return sorted(d.name for d in root.iterdir() if (d / "LC_MESSAGES" / f"{DOMAIN}.mo").is_file())


def language_name(code: str) -> str:
    """A language's own name for itself ("Nederlands"), so a user can find theirs whatever language is on screen."""
    from PySide6.QtCore import QLocale

    name = "English" if code == "en" else QLocale(code).nativeLanguageName()  # Qt says "American English" for "en"
    return (name[:1].upper() + name[1:]) if name else code


def current_language() -> str:
    return _Active.language


def install_qt_translations(app, language: str | None = None) -> list:
    """Qt's own texts (the buttons of standard dialogs) from the translations that ship with PySide6.

    Returns the installed translators (keep them alive). Nothing is installed for English or for a
    language Qt has no file for.
    """
    from PySide6.QtCore import QLibraryInfo, QLocale, QTranslator

    code = _Active.language if language is None else language
    if not code:
        return []
    directory = QLibraryInfo.path(QLibraryInfo.LibraryPath.TranslationsPath)
    installed = []
    for catalogue in ("qtbase",):
        translator = QTranslator(app)
        if translator.load(QLocale(code), catalogue, "_", directory) and app.installTranslator(translator):
            installed.append(translator)
    return installed


def setup(app, languages: Sequence[str] | None = None, localedir: Path | str | None = None) -> str:
    """Called once by `main()` before any widget text exists: the catalogue, then Qt's own texts."""
    language = init(languages, localedir)
    for translator in getattr(app, "_qt_translators", []):
        app.removeTranslator(translator)  # a language change replaces Qt's texts, it does not stack on the old ones
    app._qt_translators = install_qt_translations(app)  # kept alive for the life of the app
    return language

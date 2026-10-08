# i18n foundation

Status: resolved (the package data and build inclusion of the .mo files wait for the packaging ticket: see below)
Type: task
Blocked by: none

Spec section 6. Everything that follows is written against this.

## Acceptance

- `app/i18n.py`: `_`, `ngettext`, `pgettext`, `N_` (lazy marker); language chosen from `LANGUAGE`, `LC_ALL`, `LC_MESSAGES`, `LANG`, then Qt's system locale; falls back to untranslated English when nothing matches; catalogues under the package's `locale/` directory, domain `car-charge-planner`.
- Applied once at start, before any widget text is created; lazily marked texts (`N_`) translate when displayed, so a language change never needs a re-import.
- Qt's own translations (`qtbase_<language>.qm` from the PySide6 translations directory) loaded alongside, so standard dialog buttons follow the language.
- Babel as a development dependency; `babel.cfg`; a dev command (documented) that extracts to a committed `locale/car-charge-planner.pot`, updates `.po` files and compiles `.mo`.
- The `.mo` files are included in the package data and in the build.
- Tests: language resolution from each environment variable, fallback, `ngettext` plurals, `pgettext` context, lazy markers translate late, a test catalogue is picked up, Qt translator loaded for a language that has one.

## Comments

Implemented: `app/i18n.py` (`_`, `ngettext`, `pgettext`, `N_`, `NC_`, `translate`, `resolve_languages`, `init`, `install_qt_translations`, `setup`), `app/i18n_tools.py` (`extract`, `init`, `update`, `compile`; run as `python -m app.i18n_tools ...`), `babel.cfg`, the committed template `app/locale/car-charge-planner.pot` (empty until ticket I2), `main()` calls `i18n.setup(app)` before any widget text exists, Babel as a development dependency (`pip install -e .[dev]` once packaging exists; for now `pip install babel`), a README section. 43 tests in `tests/test_i18n.py`; 474 in the suite.

Design points worth knowing:
- **Language resolution follows the gettext tools**: `LC_ALL`, `LC_MESSAGES`, `LANG` say which locale is in use (first one set wins); the plain C or POSIX locale means English whatever `LANGUAGE` says; otherwise `LANGUAGE` (a colon list) comes first, then the locale; with none set, Qt's own list of system languages (most specific first). A regional request (`pt_BR`) falls back to the general catalogue (`pt`), and a more specific catalogue wins.
- **Texts are looked up at call time**, so a text marked with `N_()` at import and translated with `_()` or `translate()` when shown follows a language chosen later, and the language can change while the app runs.
- `_("")` returns `""`: gettext would return the catalogue header (translator names, plural rule).
- A missing or damaged catalogue never stops the app; it stays English.
- **Qt's own texts follow the language the app really has a catalogue for.** A Dutch user with the English-only app gets English standard buttons too, not a mixture. `setup()` removes the translators it installed earlier before installing new ones: a first version only forgot them, so after German the German buttons stayed. Found by a live language-switching run; the unit test of that run had hidden it by removing the translators by hand. Now covered by two tests that fail without the fix.
- **The template is deterministic.** Babel's command line stamps the current time into the header (the `SOURCE_DATE_EPOCH` variable is not honoured for extraction), so `extract` uses Babel's API with a fixed date, no line numbers (only the file), word-wrap off and a header without placeholder authors or versions. A test regenerates the template and compares it with the committed file, so a forgotten `extract` fails the suite (this is also what ticket I3's last check asks for).
- Texts that need a note for translators take one from a comment: `# i18n: {name} is the place the user chose`. Brace placeholders are flagged `python-brace-format` automatically.

Deferred on purpose: **including the compiled `.mo` files in the package and the build.** There is no build backend yet and the package is about to be renamed (open-source ticket 04). The catalogues live inside the package directory (`app/locale/`), so the later packaging step only has to list them as package data. Until a language ships there is no `.mo` to include. Whether compiled `.mo` files are committed or built by the release process is for ticket I4 to decide (a clone with a translation needs one of them).

Not verified here: a real desktop session in another language (this machine runs English) and how Qt's translations look in the real dialogs beyond the standard buttons.

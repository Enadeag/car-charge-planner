# Contributing

Thank you for helping. Small fixes can go straight to a pull request; for anything bigger, open an issue first so we can agree on the approach. Be kind: this project follows the [Code of Conduct](CODE_OF_CONDUCT.md).

## Licence of contributions

A contribution is accepted under the project's licence: the GNU General Public License, version 3 or later, with the additional term in [ADDITIONAL-TERMS.md](ADDITIONAL-TERMS.md). You keep your copyright. Do not add a licence header to the files you write: [NOTICE](NOTICE) covers them all.

## Set up

You need Python 3.12 or newer. From a clone:

```bash
python -m venv .venv
.venv/bin/pip install -e ".[dev]"
```

Run the app with `.venv/bin/car-charge-planner`.

## Tests and lint

```bash
QT_QPA_PLATFORM=offscreen .venv/bin/pytest -q    # about two minutes; no window appears and nothing touches the network
.venv/bin/ruff check .
```

`ruff check .` also checks import order and a few common mistakes (bugbear, pyupgrade). Types are checked with [pyright](https://github.com/microsoft/pyright), which CI runs too and which must report no errors:

```bash
PATH="$PWD/.venv/bin:$PATH" pyright    # or activate the venv first; pyright then finds PySide6
```

The code is checked in basic mode, and `planner/` and `forecast/` (no Qt) in strict mode, so give new code there full type hints. The tests are not type-checked. Where the Qt stubs are wrong, a `# pyright: ignore[rule]` with a short reason is fine.

A change comes with a test that fails without it. New or changed texts need a refreshed template (see below).

## Comments and docstrings

Write one only when it says something the name, signature and types cannot.

- Do comment the why: a decision, a constraint, a trade-off, an edge case, a unit or format the types don't show.
- Always comment a workaround for Qt, D-Bus, TOML, time-zone changes or another library, saying what breaks and why.
- Public contracts (the provider protocol, the conformance kit) are documented fully but briefly, in one place.
- A module docstring is one sentence about the module's role. Longer explanations go in `docs/`.
- Leave out docstrings on trivial properties, private helpers, signals and overrides whose names say it all.
- No spec section or ticket numbers, no history ("used to", "as before"): git keeps that. No section banners: split the file instead.
- Delete a comment that restates the next line.

## What a test should do

- Test behaviour someone depends on: a plan, a saved file, a request sent, a message shown in the right language. Assert the effect, not that a mock was called or that a widget exists.
- Every test must be able to fail when the behaviour breaks. Prefer exact expected values and recorded responses over "does not crash" or "is not None".
- Don't pin exact UI wording, widget geometry, widget parentage or private attributes. Go through the public method, the signal or the saved result. The pseudo-language test already covers untranslated and clipped texts on every page.
- One behaviour, one test. The same path with other inputs is a `parametrize`. A rule shared by all providers belongs in the conformance kit, not in each provider's tests.
- Checks on the repository itself (docs, README, packaging, template) only when they are cheap and catch a real mistake.
- A test that starts the app or uses real timers waits for a condition, never a fixed `sleep`.
- Don't test test helpers, fixtures or constants. In tests too, the test name is the documentation: comment only what is surprising.

## Code layout

Everything is in `src/car_charge_planner/`:

| Folder or file | What it holds |
| --- | --- |
| `planner/` | The planning itself: surplus, windows, projection, advice. No Qt. |
| `forecast/` | Forecast providers (Open-Meteo, Forecast.Solar), the service that picks and caches, place search, key storage. How to add a provider: `docs/providers.md`. |
| `storage/`, `config_file.py` | The state file and the TOML configuration. |
| `controller.py` | Ties it together: fetches, plans, announces, opens the windows. |
| `shells/` | The tray shell and the window shell (the two ways the app shows itself). |
| `planview.py`, `popup.py`, `view_model.py` | The plan page and its Qt-free view model. |
| `settings/`, `setup/` | The Settings window and the first-run set-up wizard. |
| `locale/` | Translations. |

`CONTEXT.md` explains the words the project uses (window, charge power, away and so on).

## Reporting how it runs on your desktop

The app is developed on KDE Plasma under Wayland. If you try it on another desktop, please open a **Does it work on my desktop?** issue: it takes five minutes and tells us what works where.

## Adding a language

Every text the app shows is translatable, and a language is one file. You need the project set up as in the
README, plus Babel (`pip install babel`). Run the commands from the project root.

1. **Start the file.** Use the language code, `nl` for Dutch, `pt_BR` for Brazilian Portuguese:

   ```bash
   .venv/bin/python -m car_charge_planner.i18n_tools init nl
   ```

   This creates `src/car_charge_planner/locale/nl/LC_MESSAGES/car-charge-planner.po`, a copy of the template with every text
   waiting for a translation.

2. **Translate.** Open the `.po` file in a translation editor (Poedit, Lokalize, Gtranslator) or a text editor.
   Things to know:
   - `{name}` placeholders (`{pct}`, `{end}`, `{n}`) are filled in by the app. Keep them exactly as written, but
     put them wherever your language wants them: word order is yours.
   - Texts with a number have one form per plural your language has (`{n} day` / `{n} days`).
   - A text with a context line (`msgctxt`) is the same English word in a different role. `weekday, short` is
     "Mon", `month, short` is "Jan"; write them the way your language abbreviates them.
   - `decimal separator` is the one place to write `,` if your language writes 1,6 instead of 1.6.
   - `list separator` is what goes between items of a list (`, `).
   - A text you leave empty stays English.

3. **Compile and try it.**

   ```bash
   .venv/bin/python -m car_charge_planner.i18n_tools compile
   LANGUAGE=nl .venv/bin/car-charge-planner
   ```

   The app follows `LANGUAGE`, then `LC_ALL`, `LC_MESSAGES` and `LANG`. Once at least one translation is installed,
   Settings, on the Interface tab, shows a Language choice; it applies the next time the app starts.

4. **Check it.** The tests find the usual mistakes (a text that is still English, a label that no longer fits):

   ```bash
   .venv/bin/pytest -q tests/test_i18n.py tests/test_i18n_texts.py tests/test_pseudo_language.py tests/test_language_setting.py
   ```

   Look at the Settings tabs and the popup in your language: long words are the usual trouble.

5. **Send it.** Commit the `.po` file of your language and open a pull request. The compiled `.mo` files are not committed: they are built from the `.po` files when the app is packaged, and you make them yourself (step 3) to try a language from a clone.

### When the English texts change

`python -m car_charge_planner.i18n_tools extract` rewrites the template from the code, and `python -m car_charge_planner.i18n_tools update` merges
it into every language's `.po`; new and changed texts show up untranslated (or marked fuzzy: check those). A test
fails when the committed template is behind the code, so a text added to the code comes with a refreshed template.

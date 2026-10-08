# Translator documentation and the language setting

Status: done (not committed yet)
Type: task
Blocked by: I2

## Acceptance

- `CONTRIBUTING.md` section "Adding a language": init the `.po`, translate, compile, run the app with `LANGUAGE=xx`, run the pseudo-language and catalogue tests.
- A `language` setting in `[interface]` (`auto` by default, or a language code), honoured by `app/i18n.py`; the Settings window shows the language choice **only when more than one catalogue is installed**.
- A test with a second, test-only catalogue shows the picker and switching works after a restart; with one catalogue the picker is absent.

## Comments

- `[interface] language` (`auto`, or a code such as `nl`, `pt_BR`), validated; `app/main.py` applies it after the config is read (`auto` keeps the system choice). `en` forces the source text on a system that speaks another language.
- The picker is in Settings > Interface, listing Automatic, English and every installed catalogue, each in its own language (`i18n.language_name`). It is absent when no translation is installed, and a stored language is kept untouched then. A change sends the same "applies the next start" notification as the interface mode.
- Reading `.mo` files: "installed" means a compiled `car-charge-planner.mo` exists under `app/locale/<code>/LC_MESSAGES/`.
- `CONTRIBUTING.md` is new and holds only "Adding a language"; ticket 06 (community files) writes the rest. The steps were run for real (init, compile, `LANGUAGE=nl`).
- Decided: compiled `.mo` files are not committed (git-ignored); the `.po` files are the source. Ticket 04 (packaging) must compile them at build time (`python -m app.i18n_tools compile`) and include them in the package.

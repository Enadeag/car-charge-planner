# Pseudo-language checks

Status: done (not committed yet)
Type: task
Blocked by: I2

Spec section 6, "Checks".

## Acceptance

- A test helper builds a pseudo catalogue from the `.pot` (each string wrapped, for example `[!! Charge now !!]`, lengthened about 30%).
- Every page and window (popup, plan window, Settings tabs, wizard pages once they exist) is rendered offscreen with it; the test fails on a visible text that is not wrapped (excluding values such as numbers, place names and times), and on any clipped label.
- A test that regenerating the `.pot` gives the committed file (so new strings are not forgotten).
- The helper is reusable by the wizard tickets.

## Comments

- `tests/pseudo.py` builds the pseudo catalogue from the committed `.pot` (`[!! text~~~ !!]`, 30% longer; plural forms wrapped too; the decimal and list separators kept as glue) and reads every shown text (labels, buttons, tabs, menus, tooltips, choices, suffixes). Reusable: a wizard page only needs a test that builds the page after `pseudo.install(tmp_path)` and asserts `untranslated(page) == []` and `clipped(page) == []`.
- `tests/test_pseudo_language.py` renders the popup (both pages, six situations) and every Settings tab. Mutation-checked: un-translating one button fails five of the tests.
- The `.pot` up-to-date test already existed from I1 (`test_the_committed_template_is_up_to_date_with_the_code`).
- Limit, honestly: windows are shown at the size their content asks for, so a growing text makes the window grow rather than clip; the clip check catches fixed-size spots and word-wrapped labels that get too little height. Growing the text 4x still passes today. Settings windows need about 590 px height with 30% longer texts, which a very small screen would cap.

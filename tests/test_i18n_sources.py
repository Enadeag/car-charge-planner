"""The source code keeps to the translation rules. Read as text, so no Qt and no catalogue is needed."""

import ast
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PACKAGE = ROOT / "src" / "car_charge_planner"
SOURCES = sorted(PACKAGE.rglob("*.py"))

LOOKUPS = {"_", "ngettext", "pgettext", "N_", "NC_"}
# calls that put a text in front of the user: a plain English string given to one of these is untranslated
SHOWN_BY = {
    "QLabel", "QPushButton", "setText", "setToolTip", "setWindowTitle", "addAction", "addTab", "setTabText",
    "addItem", "addItems", "setSuffix", "setPlaceholderText", "setTitle", "addMenu", "QAction",
}
# placeholders that are not words, and the spots that are deliberately English
ALLOWED_PLAIN = {"×"}


def _calls(path):
    return [n for n in ast.walk(ast.parse(path.read_text(encoding="utf-8"))) if isinstance(n, ast.Call)]


def _name(call):
    f = call.func
    return f.id if isinstance(f, ast.Name) else f.attr if isinstance(f, ast.Attribute) else ""


def _has_letters(value: str) -> bool:
    return any(c.isalpha() for c in value)


def test_translation_lookups_take_literal_text():
    """Extraction reads the source: a text built at run time would never reach the template."""
    bad = []
    for path in SOURCES:
        if path.name == "i18n.py":
            continue
        for call in _calls(path):
            if _name(call) in LOOKUPS and isinstance(call.func, ast.Name):
                for arg in call.args[: 2 if _name(call) in ("ngettext", "pgettext", "NC_") else 1]:
                    # a name, attribute or index is a text marked with N_ elsewhere; building one here is the mistake
                    if isinstance(arg, (ast.JoinedStr, ast.BinOp, ast.Call)):
                        bad.append(f"{path.relative_to(ROOT)}:{call.lineno}")
    assert not bad, bad


def test_no_untranslated_text_is_handed_to_a_widget():
    bad = []
    for path in SOURCES:
        for call in _calls(path):
            if _name(call) in SHOWN_BY:
                for arg in call.args:
                    texts = [arg] if not isinstance(arg, (ast.List, ast.Tuple)) else list(arg.elts)
                    for t in texts:
                        if isinstance(t, ast.Constant) and isinstance(t.value, str) and _has_letters(t.value) and t.value not in ALLOWED_PLAIN:
                            bad.append(f"{path.relative_to(ROOT)}:{call.lineno} {t.value!r}")
                        if isinstance(t, ast.JoinedStr) and not any(
                            isinstance(n, ast.Call) and _name(n) == "_" for n in ast.walk(t)
                        ):
                            bad.append(f"{path.relative_to(ROOT)}:{call.lineno} f-string without a translated part")
    assert not bad, bad


def test_no_sentence_is_built_by_joining_translated_pieces():
    """`_("a") + x + _("b")` fixes the word order; a template with named placeholders does not."""
    bad = []
    for path in SOURCES:
        for node in ast.walk(ast.parse(path.read_text(encoding="utf-8"))):
            if isinstance(node, ast.BinOp) and isinstance(node.op, ast.Add):
                sides = [node.left, node.right]
                if sum(isinstance(s, ast.Call) and _name(s) == "_" for s in sides) == 2:
                    bad.append(f"{path.relative_to(ROOT)}:{node.lineno}")
    assert not bad, bad

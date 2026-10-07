"""The README's "Data and privacy" section says what each provider sends, in step with the providers (ticket P10)."""

import re
from pathlib import Path

import pytest

from forecast.registry import registry

README = (Path(__file__).resolve().parent.parent / "README.md").read_text()


def section() -> str:
    start = README.index("## Data and privacy")
    end = README.index("\n## ", start + 1)
    return README[start:end]


@pytest.mark.parametrize("provider", registry.all(), ids=lambda p: p.id)
def test_each_provider_is_described_with_what_it_sends_its_credit_and_its_terms(provider):
    text = " ".join(section().split())  # the same text however the table wraps
    assert provider.name in text
    assert " ".join(provider.sends.split()) in text, f"README does not say what {provider.name} is sent"
    assert provider.attribution in text, f"README does not show {provider.name}'s credit"
    assert provider.terms_url.removeprefix("https://") in text, f"README does not link {provider.name}'s terms"


def test_the_section_says_what_matters():
    text = section()
    for phrase in ("no telemetry", "Commercial use", "secrets.toml", "Local only", "Third-party software"):
        assert phrase.lower() in text.lower(), phrase


def test_the_third_party_licences_are_the_installed_ones():
    from importlib.metadata import metadata

    assert "LGPL-3.0" in (metadata("PySide6-Essentials").get("License") or "")
    assert (metadata("jeepney").get("License-Expression") or metadata("jeepney").get("License")) == "MIT"
    assert re.search(r"jeepney.*\| MIT \|", section())


def test_the_attribution_section_credits_both_providers():
    tail = README[README.index("## Attribution"):]
    assert "Open-Meteo.com" in tail and "Forecast.Solar" in tail

"""The README's "Data and privacy" section says what each provider sends, in step with the providers."""

import re
from pathlib import Path

import pytest

from car_charge_planner.forecast.registry import registry

README = (Path(__file__).resolve().parent.parent / "README.md").read_text(encoding="utf-8")


def section() -> str:
    start = README.index("## Data and privacy")
    end = README.index("\n## ", start + 1)
    return README[start:end]


@pytest.mark.parametrize("provider", registry.all(), ids=lambda p: p.id)
def test_each_provider_is_described_with_what_it_sends_its_credit_and_its_terms(provider):
    text = " ".join(section().split())
    assert provider.name in text
    assert " ".join(provider.sends.split()) in text, f"README does not say what {provider.name} is sent"
    assert provider.attribution in text, f"README does not show {provider.name}'s credit"
    assert provider.terms_url.removeprefix("https://") in text, f"README does not link {provider.name}'s terms"


@pytest.mark.parametrize("provider", [p for p in registry.all() if p.search_places is not None], ids=lambda p: p.id)
def test_a_providers_place_search_is_in_the_privacy_section_with_its_credit(provider):
    text = " ".join(section().split())
    assert "place you type in the search box" in text and provider.name in text
    credit = getattr(provider, "place_attribution", "")
    if credit:  # "Place data: GeoNames, via Open-Meteo.com": both names must be in the README
        assert "GeoNames" in README and provider.name in README


def test_the_readme_quotes_what_the_first_page_of_the_wizard_says():
    """The privacy text of the Welcome page and the README say the same words (for the default provider)."""
    from car_charge_planner.setup.welcome import privacy_text

    quoted = " ".join(section().split())
    assert " ".join(privacy_text(registry.get("open-meteo")).split()) in quoted


def test_every_set_up_picture_the_readme_shows_exists_in_both_themes():
    from car_charge_planner.setup.wizard import PAGES

    root = Path(__file__).resolve().parent.parent
    pictures = re.findall(r"docs/images/(setup-[a-z]+-(?:light|dark)\.png)", README)
    assert pictures and len(pictures) == len(set(pictures))
    wanted = {f"setup-{page.id}-{theme}.png" for page in PAGES for theme in ("light", "dark")}
    assert set(pictures) == wanted, "the README shows a picture for every page of the wizard, in both themes"
    assert all((root / "docs" / "images" / name).is_file() for name in wanted)

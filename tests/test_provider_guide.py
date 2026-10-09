"""The guide's skeleton provider is run through the conformance kit, so the guide cannot go stale."""

import json
import re
from datetime import UTC, datetime, timedelta
from pathlib import Path

import pytest

import provider_kit as kit
from car_charge_planner.forecast.transport import Reply
from provider_kit import ProviderCase

GUIDE = (Path(__file__).resolve().parent.parent / "docs" / "providers.md").read_text(encoding="utf-8")


def block(language: str) -> str:
    found = re.search(rf"```{language}[ a-z]*\n(.*?)```", GUIDE, re.S)
    assert found, f"the guide has no ```{language} block"
    return found.group(1)


@pytest.fixture(scope="module")
def skeleton():
    namespace: dict = {}
    exec(compile(block("python skeleton"), "docs/providers.md", "exec"), namespace)
    return namespace["Example"]


@pytest.fixture
def case(skeleton):
    recorded = block("json recorded").encode()
    return ProviderCase(
        make=skeleton,
        ok=[("api.example.com", Reply(200, recorded))],
        server_error=[("", Reply(503))],
        bad_body=[("", Reply(200, b"<html>"))],
        empty_body=[("", Reply(200, b"{}"))],
        rate_limited=[("", Reply(429, b"", {"Retry-After": "120"}))],
        retry_after=timedelta(seconds=120),
        secrets={"api_key": "SECRET-KEY-123"},
        now=datetime(2026, 7, 3, 7, 0, tzinfo=UTC),
    )


def test_the_recorded_answer_in_the_guide_is_json():
    assert json.loads(block("json recorded"))["steps"]


@pytest.mark.parametrize(
    "check",
    [
        kit.check_metadata, kit.check_good_answer, kit.check_deterministic, kit.check_inputs_untouched,
        kit.check_rate_limit, kit.check_server_error, kit.check_bad_body, kit.check_empty_body,
        kit.check_secrets_stay_secret,
    ],
    ids=lambda c: c.__name__,
)
def test_the_skeleton_passes_the_kit(case, check):
    check(case)


@pytest.mark.parametrize("failure", [OSError("unreachable"), TimeoutError("slow")])
def test_the_skeleton_survives_a_dead_network(case, failure):
    kit.check_no_network(case, failure)


def test_the_guide_names_every_check_the_kit_has():
    for word in ("Metadata", "A good answer", "Rate limit", "Server error", "Not as documented", "Required settings", "Secrets"):
        assert word in GUIDE


def test_every_error_kind_is_in_the_guides_table():
    for kind in ("Unavailable", "RateLimited", "BadResponse", "NotConfigured"):
        assert f"`{kind}" in GUIDE

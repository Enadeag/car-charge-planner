# Provider guide and data and privacy documentation

Status: done (not committed yet)
Type: task
Blocked by: P3, P4, P6

## Acceptance

- `docs/providers.md`: the interface, a skeleton provider, how to run the conformance kit, recorded fixtures and attribution notices, rate limits and terms, secrets, translations; Solcast listed as a wanted contribution with its constraints (account, registered site, key, 10 calls a day, personal use only, 30-minute steps).
- README "Data and privacy" (open-source ticket 05) built from the providers' own declared descriptions: what each sends, to whom, its terms and attribution; the free tiers' limits; that commercial use needs the provider's own licence.
- A backlog draft for the Solcast provider and for "entry-point plug-ins" (open-source ticket 08).

## Comments

- `docs/providers.md` is now the full guide: the contract (types, steps, losses, the four error kinds and what the app does with each), the transport, a **complete skeleton provider**, how to add one (module, registry, checking the live documentation, recording an answer for a neutral place, fixture notice, the conformance kit, the README), rate limits and terms, secrets, translations, credit, Solcast's constraints, and "plug-ins: not yet".
- `tests/test_provider_guide.py` runs the guide's own skeleton and its recorded answer through the conformance kit, so the guide cannot go stale.
- README **Data and privacy** (what is sent to whom, per provider, in the providers' own words; free plans; commercial use; credit; nothing else leaves; local-only; what is kept; third-party software with licences read from the installed packages) and an updated Attribution. `tests/test_readme_privacy.py` fails when a provider's `sends`, credit or terms link is not in that section.
- Terms and limits were read on 2026-10-07 (Open-Meteo: free API non-commercial, under 10,000 calls a day, CC BY 4.0, paid plan = own server address and key; Forecast.Solar: public plan, 12 calls an hour per address, this and the next day, no statement about commercial use). P11 re-verifies.
- Backlog drafts for open-source ticket 08: the Solcast and entry-point-plugin backlog drafts.

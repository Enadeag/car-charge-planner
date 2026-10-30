# Secrets: decisions

Decisions about how the app keeps and removes the owner's secrets (API keys, links) that the code alone does not explain. Newest last; each names the date and the pull request that settled it.

- **2026-10-30, #282: leftovers of the withdrawn calendar trips go at start-up, as far as the keychain allows.** The app removes `calendar/ical-link` and `geoapify/api-key` from the keychain and from `secrets.json` at each start (`CalendarLeftovers`). A copy in the keychain may stay until a start at which the keychain is usable and holds it: while the keychain is locked or unavailable, while `secrets.json` lacks the names but the keychain has them, or after the engine has postponed asking for the keychain. There is no retry within a session. A keychain that stays locked is the owner's to unlock; the app never asks for it unprompted.
- **2026-10-30, #282: removing a secret never asks to unlock the keychain.** Checked on a KDE desktop with KWallet through the Secret Service: no prompt. GNOME Keyring is not checked yet; it belongs to the GNOME check of #52.
- **2026-10-30, #282: the start-up clean-up is not described in the user docs.** Only builds of `main` between v0.5.0 and #282 wrote these secrets; no release did. The reason lives in the Javadoc of `CalendarLeftovers` and `ConfigFormat`.

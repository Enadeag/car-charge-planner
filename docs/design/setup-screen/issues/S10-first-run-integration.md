# First-run integration

Status: done (not committed yet)
Type: feature
Blocked by: S5, S6, S7, S8, S9 and open-source ticket 01 (neutral defaults, the not-set-up state)

Spec section 2, "When".

## Acceptance

- On the first start (not set up) the wizard opens by itself; never from `--background`, where a notification offers it instead.
- While not set up, the popup, plan window, tray icon and window title show "Not set up yet" with a **Set up** button that opens the wizard.
- Finish writes the config once, applies it, starts the first forecast fetch and shows the plan.
- Settings has **Run set-up again**; the wizard then opens pre-filled.
- A user who enters a location directly in Settings counts as set up (the wizard offers itself no longer).
- Tests, including a whole-app subprocess run on a tray-less desktop: first start opens the wizard, a background start does not, Finish leads to a plan with no network (fake fetch).

## Comments

- `Controller.offer_setup(background)` (called by `main()` after the shell has started): not set up and a normal start -> the wizard opens by itself; started hidden (`--background`, the autostart entry) -> it does **not**, and one notification, "Set-up needed", offers it (clicking it shows the plan, which has the Set up button). The notification is sent **once ever** (a hint in the state file), not at every login. Already set up -> nothing.
- The not-set-up plan page has a **Set up** button (it was "Open Settings..."), on both shells; the tray shell hides the popup as the wizard opens. The text says "Set up the app to get a plan. It takes a few minutes." The tray icon's tooltip and the window title already said "Not set up yet".
- `Controller.setup_completed` is emitted when the wizard finishes (after `apply_config`, which starts the first fetch); `main()` connects it to `shell.show_plan`.
- Settings: **"Run set-up again..."** next to Reset tab, shown only when the app can open the wizard. It asks once if there is something unsaved ("Discard your changes?"), closes Settings and opens the wizard from what is saved. A location typed into Settings counts as set up (nothing else was needed: the state follows the location).
- Tests: in-process (when it opens or is offered, once, the buttons on both shells, closing unfinished, finishing leads to a fetch and the plan with the window not visible before, the Settings button with and without edits), and **two whole-app subprocess runs** on a desktop without a tray (offscreen, a window shell, a fake forecast, private folders, no network): a first start opens the wizard and, driven to Finish, ends with a plan; a `--background` start opens nothing and sends the one notification. Mutation-checked in both layers.
- README "First run" rewritten around the wizard.
- A limit: the subprocess runs drive the wizard through its pages' own widgets, not by clicking, and the desktop has no tray, so the tray shell's first run is covered only in-process.

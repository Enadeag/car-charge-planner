# Single instance by local socket

Status: resolved
Type: task
Blocked by: 04

Spec 4.4. Replaces the lock file.

## Acceptance

- `app/instance.py`: the first instance listens on a local socket in the runtime directory; a second launch connects, sends "show", and exits with a short message.
- A stale socket from a crashed instance is detected and replaced.
- Running instance on show request: the active shell shows its plan (tray shell: opens the popup at its last or fallback position).
- Tests: second client reaches the first; no server, so the client becomes the server; stale socket recovery.

## Comments

Implemented: `app/instance.py` (`claim_instance`, `InstanceServer`, `request_show`, `socket_path`, `Outcome`), used by `app/main.py` in place of the lock file. The running instance connects `show_requested` to `shell.show_plan`. 16 tests in `tests/test_instance.py`, including a real two-process test (a first process claims the instance; `python -m app` started as the second hands over and exits 0; the first receives the show request).

Behaviour: a second start prints "Car charge planner is already running: asked it to show itself." on stderr and exits 0 (it used to exit 1 with "already running"). A socket nobody answers on is replaced. If no socket can be made the app starts anyway, with a warning.

**A real bug found by the race test, worth remembering:** with `QLocalServer.setSocketOptions(UserAccessOption)`, Qt's `listen()` on a path that a **live** server already holds *succeeds* and replaces its socket file. In a start-up race the loser would silently become a second primary and the first would become unreachable. (A plain `listen()` fails atomically with "Address in use" and leaves the live socket alone.) So the option is not used; privacy comes from the runtime directory (mode 0700, or a private `/tmp/car-charge-planner-<uid>` directory that is checked for owner and mode), plus `chmod 0600` on the socket file. A side effect of that Qt behaviour: the stale-socket cleanup was masked by the option, so it is covered by a test that failed when the cleanup was removed.

Mutation checks (each guard removed in turn makes at least one test fail): the second look before treating a socket as stale; replacing a stale socket; `chmod 0600`; not using `UserAccessOption`.

Not done here: a second start does not yet reach a window shell (there is none until ticket 06); it shows the tray popup.

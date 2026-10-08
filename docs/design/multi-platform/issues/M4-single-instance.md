# Single instance on every system

Status: ready-for-agent
Type: task
Blocked by: M2

## Acceptance

- `app/instance.py` no longer calls `os.getuid()` or `XDG_RUNTIME_DIR` unconditionally: the socket name is per user (`getpass.getuser()` or the user's id where it exists) on Windows, and the private directory with mode checks stays where file modes exist (Linux, macOS).
- A second start signals the first and exits on all three systems; tested with the existing tests plus a Windows and a macOS variant (marked) that use a local socket name.
- The "path in use by a live instance is not stolen" behaviour of the Linux implementation is kept and tested as before.

## Comments

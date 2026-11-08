# Security policy

## Reporting a problem

Please report a security problem privately, not as a public issue: open the repository's **Security** tab and choose **Report a vulnerability** (GitHub private reporting). Say what you found, how to reproduce it, and which version.

You will get an answer within a week or so. This is a small project run by one person, so there is no bug bounty and no fixed fix time, but a confirmed problem is fixed in the next release and credited if you wish.

## What matters here

The app runs on your own computer and only talks to the forecast provider you chose. Things worth reporting: a way to read or overwrite files outside the app's own folders, a forecast provider's API key shown or stored where other users can read it (`secrets.json`, used where there is no system keychain, must be readable by you alone), or a crash that a forecast response can trigger.

On Android the same holds for the phone app: an API key that leaves the encrypted keychain or reaches Logcat, another app reaching a component the app does not export or the app's own files, or a crash that a forecast response can trigger.

## Accepted risks

These are known and deliberately left as they are; a report that shows they matter more than described here is welcome.

- **Linux: the API keys cross the session bus unencrypted.** The app asks the Secret Service (GNOME Keyring, KWallet) for the API keys in a "plain" session, not an encrypted one. The API keys travel over your own session bus, which only programs running as you can use, and such a program can ask the Secret Service for them just as well; encrypting the session would not keep it out.
- **Linux: other programs in your session can use the tray icon.** The tray icon is a StatusNotifierItem on your session bus, so any program running as you can read its tooltip (the plan's state) and choose its menu entries: open the dashboard, Settings or About, or quit the app. It cannot change a setting or read an API key that way. Only programs running as you can reach your session bus.
- **The single-instance folder is checked one level up.** Before the app makes its private folder for the lock file and socket owner-only, it checks the folder itself (yours, not a link, not writable by others, no files of other users and no hard links) and its parent (yours or root's, not a link, not writable by others), and checks again after the change that it is still the same folder. It does not check every folder above it or the whole contents: the folder lives in your own `XDG_RUNTIME_DIR` or `~/.local/share`, and whoever can write in the folders above those already controls your account.
- **Android: the Keystore key is usable while the phone is locked, and is not in StrongBox.** The background rounds need the API keys with the screen off, so the key that encrypts them does not require an unlocked phone, and it lives in the Android Keystore without the StrongBox hardware. Another app cannot read it; someone who controls the unlocked system or the phone's root can use it.
- **Android: the settings move to your next phone.** The settings file, with the home's coordinates, and the trips travel in Android's device-to-device transfer (Android 12 and later; Android 10 and 11 transfer nothing). The API keys are kept out of it and must be typed again on the new phone.
- **Android: the debug APK is signed with a public key.** The debug build that CI attaches to a run uses a debug keystore that is in the repository. Anyone can sign an APK with it, so the debug build is for trying and testing only and its data is only as safe as that key; use a release build for real use.

## Supported versions

Only the latest release.

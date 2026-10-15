# Security policy

## Reporting a problem

Please report a security problem privately, not as a public issue: open the repository's **Security** tab and choose **Report a vulnerability** (GitHub private reporting). Say what you found, how to reproduce it, and which version.

You will get an answer within a week or so. This is a small project run by one person, so there is no bug bounty and no fixed fix time, but a confirmed problem is fixed in the next release and credited if you wish.

## What matters here

The app runs on your own computer and only talks to the forecast provider you chose. Things worth reporting: a way to read or overwrite files outside the app's own folders, a forecast provider key shown or stored where other users can read it (`secrets.json`, used where there is no system keychain, must be readable by you alone), or a crash that a forecast response can trigger.

## Supported versions

Only the latest release.

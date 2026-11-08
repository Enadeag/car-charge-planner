## What and why

<!-- What does this change, and what problem does it solve? -->

## How it was checked

- [ ] `./mvnw verify` passes (on Linux, and on Windows for anything that touches the platform)
- [ ] For a change under `android/` or in the engine: `cd android && ./gradlew check` passes too
- [ ] New texts have a `TextKey` and a key in every `messages_*.properties`; on Android, in every `values-*/strings.xml`
- [ ] Changed behaviour is in `CHANGELOG.md` under Unreleased

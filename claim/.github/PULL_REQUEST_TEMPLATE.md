## Summary

<!-- Describe the user-visible change. -->

## Verification

- [ ] `./gradlew clean check`
- [ ] `./gradlew runServer` when Paper behavior changed
- [ ] Focused tests cover changed behavior

## Checklist

- [ ] No hard dependency on LWC, Bolt, or LockettePro was added.
- [ ] JDBC work stays off the Paper main thread.
- [ ] Unresolved protected owners fail closed without mutation or player messaging.
- [ ] No `build/`, `.gradle/`, `run/`, secrets, or server state is included.

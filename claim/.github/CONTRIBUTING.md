# Contributing to ChestClaim

## Setup

Use Java 25 and the committed Gradle wrapper:

```bash
./gradlew clean check
```

Run the local Paper smoke with:

```bash
./gradlew runServer
```

Read `AGENTS.md` before changing the plugin. Keep reward producers behind `ClaimService`, preserve asynchronous JDBC access, and keep protection integrations optional.

## Pull requests

Describe the observable behavior, list exact verification commands, and include focused tests for changed contracts. Do not commit generated build output, Paper server state, credentials, or provider-specific runtime jars.

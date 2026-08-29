# ChestClaim Agent Guide

Paper 26.2 plugin. Java 25 and Gradle 9.7.1 are pinned; run `./gradlew clean check` before completion. Google Java Format is enforced through Spotless and Checkstyle. Keep all plugin implementation under `src/main/java/org/aincraft/chestclaim`, keep SQL in `src/main/resources/sql/`, and do not add hard dependencies on LWC, Bolt, or LockettePro.

All JDBC work runs on the dedicated database executor. Bukkit inventory and protection-provider calls run on the server thread. `returnLockedChest` must resolve one definitive owner before queueing; unresolved or conflicting ownership throws without mutation or player messaging. Do not add commands or GUI behavior without a new specification.

Never commit `build/`, `.gradle/`, `run/`, secrets, or server state. Verify with `./gradlew clean check`; use `./gradlew runServer` for the Paper startup smoke.

# Chest Claim Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Paper plugin in `./claim` that queues durable rewards, delivers them automatically on join, and routes protected chest returns to the resolved owner.

**Architecture:** `claim` is a single Paper plugin with a public `ClaimService` API, a SQLite/Hikari repository behind an executor, a main-thread inventory delivery coordinator, and a fail-closed protection resolver. LWC, Bolt, and LockettePro adapters use optional reflection so the plugin enables without any provider installed.

**Tech Stack:** Java 25, Gradle 9.7.1, Paper API `26.2.build.+`, SQLite supplied by Paper, HikariCP 7.0.2 shaded into the plugin JAR, JUnit 5, Spotless, Checkstyle, PMD, SpotBugs, and run-paper.

**Spec:** `docs/superpowers/specs/2026-08-29-chest-claim-design.md`

## Global Constraints

- `claim/` is the only plugin source tree; no root-project implementation is added.
- Paper API is compile-only; do not hard-depend on LWC, Bolt, or LockettePro.
- All JDBC work runs on the dedicated database executor; Bukkit inventory/provider calls run on the main thread.
- SQLite pool size is exactly one; use prepared statements and try-with-resources.
- `returnLockedChest` resolves ownership before queueing and throws `ProtectionOwnerResolutionException` for every non-definitive result.
- Failed owner resolution performs no repository write, block mutation, inventory mutation, or player messaging.
- No `/claim` command, GUI, reward producer, economy integration, or cross-server behavior is introduced.
- Every production behavior is preceded by a failing focused test; configuration and generated wrapper files are exempt.

---

### Task 1: Scaffold the Paper plugin and test harness [FR-001, FR-010]

**Files:**
- Create: `claim/settings.gradle.kts`
- Create: `claim/build.gradle.kts`
- Create: `claim/gradlew`
- Create: `claim/gradlew.bat`
- Create: `claim/gradle/wrapper/gradle-wrapper.jar`
- Create: `claim/gradle/wrapper/gradle-wrapper.properties`
- Create: `claim/src/main/resources/plugin.yml`
- Create: `claim/src/main/resources/config.yml`
- Create: `claim/src/test/java/org/aincraft/chestclaim/ClaimTestSupport.java`

**Interfaces:**
- Produces the Gradle project used by every later task.
- Establishes Java package root `org.aincraft.chestclaim`.

- [x] **Step 1: Create the settings and pinned build configuration**

Use `rootProject.name = "claim"`, Java 25, Paper API `io.papermc.paper:paper-api:26.2.build.+`, run-paper 3.1.0, Spotless 8.10.0/google-java-format 1.36.1, Checkstyle 13.11.0, PMD 7.26.0, SpotBugs 6.5.10/4.9.7, JUnit 5, and HikariCP 7.0.2. Configure `jar` to include HikariCP without including compile-only Paper or provider APIs. Configure `check` to depend on all static-analysis tasks and `runServer` for Paper 26.2.

- [x] **Step 2: Add plugin metadata and empty default configuration**

`plugin.yml` shall declare `ChestClaim`, main class `org.aincraft.chestclaim.ClaimPlugin`, version expansion `${version}`, quoted `api-version: '26.2'`, description, and `softdepend: [LWC, Bolt, LockettePro]`. `config.yml` shall contain the ordered provider IDs `lwc`, `bolt`, `lockettepro` and the SQLite filename `claim.db`.

- [x] **Step 3: Add the wrapper from the pinned local development-network checkout**

Copy the tracked wrapper scripts and `gradle/wrapper` files from `.agents/skills/development-network/` into `claim/`. Do not copy server state or build output.

- [x] **Step 4: Verify the scaffold before implementation**

Run `cd claim && ./gradlew tasks --no-daemon`. Expected: Gradle 9.7.1 starts and lists `test`, `check`, `jar`, and `runServer`.

---

### Task 2: Write the failing protection and service contract tests [FR-002, FR-006, FR-008, FR-009]

**Files:**
- Create: `claim/src/main/java/org/aincraft/chestclaim/api/ClaimService.java`
- Create: `claim/src/main/java/org/aincraft/chestclaim/claim/ItemStackCodec.java`
- Create: `claim/src/main/java/org/aincraft/chestclaim/claim/PendingClaim.java`
- Create: `claim/src/main/java/org/aincraft/chestclaim/storage/ClaimRepository.java`
- Create: `claim/src/main/java/org/aincraft/chestclaim/protection/ProtectionProvider.java`
- Create: `claim/src/main/java/org/aincraft/chestclaim/protection/ProtectionProviderException.java`
- Create: `claim/src/main/java/org/aincraft/chestclaim/protection/ProtectionOwnerResolutionException.java`
- Create: `claim/src/main/java/org/aincraft/chestclaim/protection/ProtectionResolver.java`
- Create: `claim/src/test/java/org/aincraft/chestclaim/protection/ProtectionResolverTest.java`
- Create: `claim/src/test/java/org/aincraft/chestclaim/claim/ClaimServiceContractTest.java`

**Interfaces:**
- `ClaimService` produces `CompletableFuture<Void> queueReward(UUID, ItemStack, String)` and `CompletableFuture<Void> returnLockedChest(Block, ItemStack)`.
- `ProtectionProvider` consumes a `Block` and produces `Optional<UUID>` or throws `ProtectionProviderException`.
- `ProtectionResolver.resolveOwner(Block)` produces a definitive `UUID` or throws `ProtectionOwnerResolutionException`.
- `ClaimRepository` consumes serialized claims and exposes synchronous JDBC-shaped methods; callers own the async executor boundary.

- [x] **Step 1: Write resolver tests first**

Cover: one provider resolves a UUID; same-owner results from multiple providers are accepted; no providers/no owner throws; provider failure is wrapped; conflicting UUIDs throw. Use small fake providers and a `Proxy` implementing Bukkit `Block` so tests do not need MockBukkit.

- [x] **Step 2: Write service contract tests first**

Cover: valid queue calls the repository; null UUID, null/air item, and blank source perform no write; a known locked-chest owner queues source `locked-chest-return`; an unresolved owner throws before repository invocation. Use a fake repository, fake resolver, and codec; use `new ItemStack(Material.CHEST)` only as an input value.

- [x] **Step 3: Run the focused tests to verify RED**

Run `cd claim && ./gradlew test --tests '*ProtectionResolverTest' --tests '*ClaimServiceContractTest' --no-daemon`. Expected: compilation/test failure because the implementation classes and service implementation are not yet present. If the failure is a test typo or dependency error, fix the test setup until the missing-behavior failure is clear.

---

### Task 3: Implement protection resolution and optional adapters [FR-006, FR-007, FR-009]

**Files:**
- Create: `claim/src/main/java/org/aincraft/chestclaim/protection/ReflectiveProtectionProvider.java`
- Create: `claim/src/main/java/org/aincraft/chestclaim/protection/LwcProtectionProvider.java`
- Create: `claim/src/main/java/org/aincraft/chestclaim/protection/BoltProtectionProvider.java`
- Create: `claim/src/main/java/org/aincraft/chestclaim/protection/LocketteProProtectionProvider.java`
- Create: `claim/src/main/java/org/aincraft/chestclaim/protection/ProtectionProviderDiscovery.java`
- Modify: `claim/src/main/java/org/aincraft/chestclaim/protection/ProtectionResolver.java`
- Create: `claim/src/test/java/org/aincraft/chestclaim/protection/ReflectiveProtectionProviderTest.java`

**Interfaces:**
- `ReflectiveProtectionProvider` accepts a Bukkit plugin object and candidate lookup/root method names; it returns an empty optional only when the protection lookup finds no protected record.
- Adapters expose IDs `lwc`, `bolt`, and `lockettepro`.
- `ProtectionProviderDiscovery.discover()` returns providers in config order and ignores absent optional plugins.

- [x] **Step 1: Add a failing reflective-adapter test**

Create fake plugin/protection objects where `findProtection(Block)` returns a protection object whose `getOwner()` returns a UUID. Assert the LWC adapter returns that UUID. Add a fake protected object whose owner is blank and assert `ProtectionProviderException`.

- [x] **Step 2: Run the adapter test to verify RED**

Run `cd claim && ./gradlew test --tests '*ReflectiveProtectionProviderTest' --no-daemon`. Expected: compilation failure because the adapter is not implemented.

- [x] **Step 3: Implement the reflection boundary**

Support lookup methods accepting `Block`, `Location`, or `World,int,int,int`; support provider manager roots such as `getProtectionManager`, `getManager`, and `getAPI`; support owner values of `UUID`, `Player`, `OfflinePlayer`, and UUID strings. A name is accepted only through an existing/cached Bukkit profile with a non-null UUID and `hasPlayedBefore()`/online evidence; otherwise throw. Include LockettePro static API class `org.yi.acru.bukkit.LocketteProAPI` as a candidate and never fail plugin enable when a class/plugin is absent.

- [x] **Step 4: Implement resolver fail-closed behavior**

Inspect every discovered provider, collect definitive owners, accept zero-or-more equal UUIDs, and throw for zero owners, provider errors, or conflicting owners. Preserve the original cause in the typed exception.

- [x] **Step 5: Run resolver and adapter tests to verify GREEN**

Run `cd claim && ./gradlew test --tests '*ProtectionResolverTest' --tests '*ReflectiveProtectionProviderTest' --no-daemon`. Expected: all focused tests pass.

---

### Task 4: Implement durable SQLite claim storage [FR-003, FR-005, FR-010]

**Files:**
- Create: `claim/src/main/java/org/aincraft/chestclaim/storage/SqliteClaimRepository.java`
- Create: `claim/src/main/resources/sql/schema.sql`
- Create: `claim/src/test/java/org/aincraft/chestclaim/storage/ClaimRepositoryContractTest.java`

**Interfaces:**
- `SqliteClaimRepository` implements `ClaimRepository` and owns one `HikariDataSource`.
- `claimPending(UUID, UUID)` atomically claims all pending rows for a player with a delivery token.
- `complete(long, UUID)` deletes only a row held by that token.
- `retain(PendingClaim, UUID, List<byte[]>)` returns the row to pending and inserts additional leftovers in one transaction.
- `release(long, UUID)` returns a failed decode to pending.

- [x] **Step 1: Write repository contract tests against an in-memory fake**

Test claim/complete token ownership, retain behavior with one and multiple serialized leftovers, and release behavior. Keep the tests independent of a local server or external database.

- [x] **Step 2: Run repository contract tests to verify RED**

Run `cd claim && ./gradlew test --tests '*ClaimRepositoryContractTest' --no-daemon`. Expected: failure because the fake contract implementation/test support is not complete.

- [x] **Step 3: Implement the schema and repository**

Use `pending_claims(id INTEGER PRIMARY KEY AUTOINCREMENT, player_uuid TEXT NOT NULL, item_blob BLOB NOT NULL, source TEXT NOT NULL, created_at INTEGER NOT NULL, state TEXT NOT NULL DEFAULT 'PENDING', delivery_token TEXT, claimed_at INTEGER)`, an index on `(player_uuid,state,id)`, a five-minute stale-delivery reset, one Hikari SQLite connection, and prepared statements with try-with-resources.

- [x] **Step 4: Run repository tests and compile**

Run `cd claim && ./gradlew test --tests '*ClaimRepositoryContractTest' --no-daemon`. Expected: PASS.

---

### Task 5: Implement queueing and automatic inventory delivery [FR-002, FR-003, FR-004, FR-005, FR-008, FR-009]

**Files:**
- Create: `claim/src/main/java/org/aincraft/chestclaim/claim/ClaimServiceImpl.java`
- Create: `claim/src/main/java/org/aincraft/chestclaim/claim/ClaimDeliveryService.java`
- Create: `claim/src/main/java/org/aincraft/chestclaim/claim/ClaimJoinListener.java`
- Create: `claim/src/test/java/org/aincraft/chestclaim/claim/ClaimDeliveryServiceTest.java`
- Modify: `claim/src/test/java/org/aincraft/chestclaim/claim/ClaimServiceContractTest.java`

**Interfaces:**
- `ClaimServiceImpl` owns validation, codec use, owner resolution, and async queue writes.
- `ClaimDeliveryService.deliver(Player)` owns the DB-async/main-thread-DB-async join pipeline.
- `ClaimJoinListener` invokes delivery and logs failures without sending player messages.

- [x] **Step 1: Write delivery tests first**

Cover a fully accepted stack marks the row complete; a partial insertion retains exactly the leftovers; an empty inventory leaves the row pending; decode failure releases the row and never calls inventory; database transitions receive the same delivery token. Use direct executors and dynamic proxies for `Player`/`Inventory`.

- [x] **Step 2: Run delivery tests to verify RED**

Run `cd claim && ./gradlew test --tests '*ClaimDeliveryServiceTest' --no-daemon`. Expected: compilation failure because delivery implementation is absent.

- [x] **Step 3: Implement the codec and service**

Use `ItemStack.serializeAsBytes()`/`ItemStack.deserializeBytes(byte[])`. Validate queue input before encoding. `returnLockedChest` resolves first and calls `queueReward(owner, chestItem, "locked-chest-return")`. The join path claims rows asynchronously, performs only inventory operations on the supplied main-thread executor, then completes/retains/releases rows asynchronously. Never drop leftovers or message the player.

- [x] **Step 4: Run service and delivery tests to verify GREEN**

Run `cd claim && ./gradlew test --tests '*ClaimServiceContractTest' --tests '*ClaimDeliveryServiceTest' --no-daemon`. Expected: PASS.

---

### Task 6: Wire plugin lifecycle and operator-facing files [FR-001, FR-002, FR-007, FR-010]

**Files:**
- Create: `claim/src/main/java/org/aincraft/chestclaim/ClaimPlugin.java`
- Create: `claim/README.md`
- Create: `claim/AGENTS.md`
- Create: `claim/.github/ISSUE_TEMPLATE/bug_report.yml`
- Create: `claim/.github/ISSUE_TEMPLATE/feature_request.yml`
- Create: `claim/.github/PULL_REQUEST_TEMPLATE.md`
- Create: `claim/.github/CONTRIBUTING.md`
- Create: `claim/.github/CODE_OF_CONDUCT.md`

**Interfaces:**
- `ClaimPlugin` registers `ClaimService` through Bukkit's service manager and registers `ClaimJoinListener` only after migration succeeds.
- `ClaimPlugin` unregisters the service and closes the repository/executor on disable.
- Provider discovery reads the ordered IDs from `config.yml` and never turns missing providers into startup failure.

- [x] **Step 1: Add lifecycle smoke-test scaffolding**

Add a test or test helper that verifies the production wiring passes a service registration and listener registration only after repository initialization. Keep the runtime-specific assertions small; the Paper startup smoke is the authoritative integration check.

- [x] **Step 2: Implement enable/disable wiring**

Create the data folder, initialize `SqliteClaimRepository`, create a single daemon DB executor, discover optional providers, register the service at normal priority, register the join listener, and use `Bukkit.getScheduler().runTask` as the main-thread executor. On initialization failure, log the exception and disable without registering partial services.

- [x] **Step 3: Write factual README, AGENTS.md, and community files**

Document only the implemented automatic delivery and service API. Include exact build/run commands, provider soft dependencies, no-message fail-closed behavior, and no generated `run/` state. Do not advertise commands or GUI behavior.

- [x] **Step 4: Run the plugin compile and startup smoke**

Run `cd claim && ./gradlew clean jar --no-daemon`, then `./gradlew runServer --no-daemon` long enough to observe `ChestClaim enabled` and a registered service. Expected: plugin JAR builds and Paper enables the plugin without any protection plugin installed.

---

### Task 7: Final verification and review [FR-001–FR-010, NFR-001–NFR-006]

**Files:**
- Modify: any implementation/test/docs files required by verification findings only.

- [x] **Step 1: Run the full quality gate**

Run `cd claim && ./gradlew clean check --no-daemon`. Expected: tests, Spotless, Checkstyle, PMD, and SpotBugs all pass.

- [x] **Step 2: Verify repository cleanliness and generated-state exclusions**

Run `git -C /home/jlo/dev/chestclaim diff --check` and inspect `git status --short`. Expected: no whitespace errors, only the intended `claim/` and `docs/superpowers/{specs,plans}/` paths changed, and no `claim/run/`, `build/`, `.gradle/`, or credentials are staged.

- [x] **Step 3: Review against the spec**

Check every FR/NFR and confirm that unresolved owner paths throw before repository writes and emit no player message, optional providers do not block enable, leftovers remain persisted, and all JDBC is off-thread. Fix any mismatch, rerun the focused test, then rerun `clean check`.

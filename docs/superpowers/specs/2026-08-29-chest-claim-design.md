## Status

> **APPROVED 2026-08-29** — explicit human approval via in-session selection of "Approve — verify and commit". Implementation (`claim/`) built, verified (`./gradlew clean check` exit 0; Paper 26.2 startup smoke showed `ChestClaim enabled; protection providers: []`), and committed as `9c21c25`. The requested default provider is Bolt; LWC and LockettePro remain supported as explicit opt-ins. Live Bolt 1.2.22 verification created a real Bolt protection, resolved its owner, and queued one `locked-chest-return` row; synthetic rows and the temporary harness were removed. Plan checkboxes ticked to match.

## Goal

Build a Paper plugin in `./claim` that gives other plugins one durable place to queue player rewards. Rewards are delivered automatically when the player joins. When a guild workflow returns a chest item from a protected chest, the plugin resolves the protection owner through an installed protection provider and queues the item for that owner. The return operation fails closed when ownership cannot be determined.

## Scope

### In scope

- A standalone Paper 26.2 plugin under `claim/`.
- A public `ClaimService` registered through Bukkit's `ServicesManager`.
- Durable pending-reward storage in SQLite.
- Asynchronous database I/O and synchronous Bukkit inventory operations.
- Automatic pending-reward delivery on player join.
- Retention of inventory leftovers instead of dropping or deleting them.
- Optional integrations with LWC, Bolt, and LockettePro without hard runtime dependencies.
- Typed fail-closed errors for missing, unresolved, or conflicting protection ownership.
- Unit tests for queueing, delivery, resolution, and failure atomicity.

### Out of scope

- Shop, crafting, guild, or holiday reward producers; those call `ClaimService` later.
- A `/claim` command, GUI, menus, or player-facing notification system.
- Breaking, removing, replacing, or unlocking world blocks.
- Economy, permissions, cross-server synchronization, or remote databases.
- Provider-specific compile-time dependencies or provider configuration beyond an ordered provider list.

## Functional requirements

### FR-001 — Plugin scaffold

The project shall build as a Paper 26.2 plugin with Java 25, Gradle 9.7.1, and the repository's pinned quality plugins. `plugin.yml` shall use a quoted `api-version: '26.2'` and identify the main plugin class.

**AC-1:** `./gradlew clean check` completes successfully and produces a plugin JAR.

### FR-002 — Reward service API

The plugin shall expose and register:

```java
CompletableFuture<Void> queueReward(UUID playerId, ItemStack item, String source);
CompletableFuture<Void> returnLockedChest(Block protectedChest, ItemStack chestItem);
```

`queueReward` shall reject null player IDs, null items, empty item stacks, and blank sources without writing a row. `returnLockedChest` shall resolve the owner before calling `queueReward`.

**AC-2:** A consumer can retrieve `ClaimService` from Bukkit's service registry after enable and queue a valid item for an offline UUID.

### FR-003 — Durable pending claims

Each queued reward shall be stored as a unique row containing an ID, player UUID, serialized item, source, creation timestamp, and delivery state. Rows shall survive plugin restarts and offline players. SQLite access shall use a single HikariCP writer connection and prepared statements.

**AC-3:** A queued item remains available after closing and reopening the repository.

### FR-004 — Automatic delivery

On `PlayerJoinEvent`, the plugin shall load the joining player's pending rows asynchronously, then add items to the player's inventory on the main thread. A completely accepted item shall be marked delivered. A partial insertion shall update the row with the remaining item. An inventory-full item shall remain pending.

**AC-4:** A join with capacity delivers the item; a join with insufficient capacity retains exactly the unaccepted amount.

### FR-005 — Delivery safety

The delivery path shall never perform JDBC work on the server main thread. The implementation shall serialize all item-stack bytes before asynchronous storage and deserialize them before synchronous inventory mutation. Delivery state updates shall be conditional on the row ID so one row cannot be completed twice by concurrent join handling.

**AC-5:** Tests cover conditional state transitions; code keeps JDBC work in repository futures and inventory mutation in the synchronous callback.

### FR-006 — Protection provider contract

A provider shall expose a stable ID and a lookup that returns one of: a definitive owner UUID, no protection/owner, or a provider failure. The resolver shall inspect providers in configured order. A provider returning a definitive owner ends resolution only when no earlier provider reported a conflicting owner.

**AC-6:** Provider order is deterministic and a definitive UUID is normalized into the same return operation.

### FR-007 — Optional provider integrations

The plugin shall support LWC, Bolt, and LockettePro when their Bukkit plugins are installed. Integrations shall be optional and loaded through narrow reflective runtime adapters; absence of a provider shall not prevent the plugin from enabling.

**AC-7:** The plugin enables with none, one, or several supported protection plugins installed; each adapter has a fake-surface unit test.

### FR-008 — Locked-chest return

`returnLockedChest` shall resolve the protected block's owner, then queue the supplied chest item with source `locked-chest-return`. It shall not mutate the block, inventory, or caller-owned item before ownership resolution succeeds.

**AC-8:** A known owner creates one pending reward and a failed resolution creates none.

### FR-009 — Fail closed with no message

If ownership is unavailable because no provider is installed, the block is not protected, the provider exposes only an unresolvable owner name, a provider fails, or providers disagree, `returnLockedChest` shall throw a typed `ProtectionOwnerResolutionException`. It shall not queue an item, mutate the world, or send a player message.

**AC-9:** Tests assert the exception type and zero repository writes for every unresolved/conflicting case.

### FR-010 — Lifecycle

The plugin shall run schema initialization before registering gameplay listeners, register the service on enable, and unregister it plus close the Hikari data source on disable.

**AC-10:** A startup smoke confirms the plugin enables and the service is registered.

## Non-functional requirements

- **NFR-001:** All JDBC operations execute off the Paper main thread; inventory and provider API calls execute on the main thread.
- **NFR-002:** The plugin has no hard runtime dependency on LWC, Bolt, or LockettePro.
- **NFR-003:** All SQL statements use prepared parameters and try-with-resources.
- **NFR-004:** Owner resolution is fail-closed; no heuristic or generated UUID is accepted.
- **NFR-005:** Local unit tests are deterministic and do not require a running Minecraft server or external database.
- **NFR-006:** The plugin emits no player-facing message for an unresolved owner.

## Failure handling

| Failure | Behavior |
|---|---|
| Invalid queue input | Complete the future exceptionally; perform no write. |
| Database open/migration failure | Fail plugin enable; do not register the service or listener. |
| Database write failure | Complete the queue future exceptionally; caller retains ownership of the item. |
| Item deserialization failure | Leave the row pending, log a severe diagnostic, and do not mutate inventory. |
| Inventory has no space | Leave the row pending; no item is dropped. |
| Partial inventory insertion | Persist only the unaccepted remainder. |
| No supported provider | Throw `ProtectionOwnerResolutionException`; no write and no message. |
| Provider reports no owner | Throw `ProtectionOwnerResolutionException`; no write and no message. |
| Provider owner cannot become a UUID | Throw `ProtectionOwnerResolutionException`; no write and no message. |
| Providers disagree | Throw `ProtectionOwnerResolutionException`; no write and no message. |
| Provider API throws | Wrap in `ProtectionOwnerResolutionException`; no write and no message. |

## Verification policy

Run from `claim/`:

```bash
./gradlew clean check
./gradlew test
```

For runtime verification, run the Paper harness with `./gradlew runServer`, confirm the plugin enables, and inspect that a service is registered. The final review shall confirm that only `claim/` and the design/plan artifacts changed, with no generated server state committed.

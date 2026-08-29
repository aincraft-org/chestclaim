# ChestClaim

Durable automatic rewards for Paper servers, including returns of items from protected chests to the protection owner's claim queue.

## Requirements

- Paper 26.2
- Java 25

## Install

1. Run `./gradlew clean build`.
2. Copy `build/libs/claim-<version>.jar` into the server's `plugins/` directory.
3. Restart the server.

ChestClaim has no player command or GUI. Pending items are delivered automatically when the owner joins.

## Producer integration

Other plugins obtain `org.aincraft.chestclaim.api.ClaimService` from Bukkit's service registry and call:

```java
claimService.queueReward(playerUuid, itemStack, "shop");
claimService.returnLockedChest(protectedBlock, chestItem);
```

`returnLockedChest` resolves the protected owner before queueing. It throws when no supported protection plugin can provide one definitive owner and does not send a player message or mutate the block on that path.

## Protection providers

LWC, Bolt, and LockettePro are optional soft dependencies. Configure their lookup order in `config.yml`; the default is LWC, then Bolt, then LockettePro. ChestClaim still enables when none of them is installed, but protected-chest returns fail closed until an owner can be resolved.

## Configuration

- `database.file` — SQLite file name stored under the plugin data folder.
- `protection-providers.order` — ordered optional provider IDs: `lwc`, `bolt`, and `lockettepro`.

## Verification

```bash
./gradlew clean check
./gradlew runServer
```

The runtime smoke should show ChestClaim enabling with zero protection providers. Pending claims remain stored when a player's inventory has no capacity.

## License

No license has been declared yet.

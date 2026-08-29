package org.aincraft.chestclaim.api;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

/** Queues player rewards and routes protected chest returns. */
public interface ClaimService {

  /** Queues an item for a player who may be offline. */
  CompletableFuture<Void> queueReward(UUID playerId, ItemStack item, String source);

  /** Resolves a protected chest owner and queues the supplied chest item for that owner. */
  CompletableFuture<Void> returnLockedChest(Block protectedChest, ItemStack chestItem);
}

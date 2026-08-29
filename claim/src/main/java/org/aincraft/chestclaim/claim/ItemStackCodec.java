package org.aincraft.chestclaim.claim;

import org.bukkit.inventory.ItemStack;

/** Encodes and decodes Bukkit item stacks for durable storage. */
public interface ItemStackCodec {

  /** Encodes an item for storage. */
  byte[] encode(ItemStack item);

  /** Decodes a stored item. */
  ItemStack decode(byte[] bytes);
}

package org.aincraft.chestclaim.claim;

import org.bukkit.inventory.ItemStack;

/** Paper serialization adapter for persistent item stacks. */
public final class BukkitItemStackCodec implements ItemStackCodec {

  @Override
  public byte[] encode(ItemStack item) {
    return item.serializeAsBytes();
  }

  @Override
  public ItemStack decode(byte[] bytes) {
    return ItemStack.deserializeBytes(bytes);
  }
}

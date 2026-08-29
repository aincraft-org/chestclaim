package org.aincraft.chestclaim.protection;

import java.util.Optional;
import java.util.UUID;
import org.bukkit.block.Block;

/** Optional integration that can resolve the owner of a protected block. */
public interface ProtectionProvider {

  /** Returns the stable configuration identifier for this provider. */
  String id();

  /** Resolves the owner when this provider has an applicable protection. */
  Optional<UUID> resolveOwner(Block block) throws ProtectionProviderException;
}

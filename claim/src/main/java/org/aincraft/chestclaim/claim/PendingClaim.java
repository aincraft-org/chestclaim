package org.aincraft.chestclaim.claim;

import java.util.UUID;

/** A serialized reward currently owned by the claim queue. */
public record PendingClaim(
    long id, UUID playerId, byte[] itemBytes, String source, long createdAt) {

  /** Creates a claim record while copying the serialized item bytes. */
  public PendingClaim {
    itemBytes = itemBytes.clone();
  }

  @Override
  public byte[] itemBytes() {
    return itemBytes.clone();
  }
}

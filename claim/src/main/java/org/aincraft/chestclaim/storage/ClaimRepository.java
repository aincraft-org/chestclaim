package org.aincraft.chestclaim.storage;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.aincraft.chestclaim.claim.PendingClaim;

/** Synchronous persistence boundary; callers must invoke it off the server thread. */
public interface ClaimRepository extends AutoCloseable {

  /** Creates the durable claim schema if it does not exist. */
  void initialize() throws SQLException;

  /** Enqueues one serialized item for a player. */
  long enqueue(UUID playerId, byte[] item, String source) throws SQLException;

  /** Claims pending rows for one delivery attempt. */
  List<PendingClaim> claimPending(UUID playerId, UUID deliveryToken) throws SQLException;

  /** Removes a row after successful delivery. */
  boolean complete(long claimId, UUID deliveryToken) throws SQLException;

  /** Retains leftovers while returning the row to the pending state. */
  boolean retain(PendingClaim claim, UUID deliveryToken, List<byte[]> items) throws SQLException;

  /** Releases a delivery row after an unsuccessful attempt. */
  boolean release(long claimId, UUID deliveryToken) throws SQLException;

  @Override
  void close();
}

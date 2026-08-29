package org.aincraft.chestclaim.storage;

import java.util.List;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

/** SQL Object operations for ChestClaim's durable pending-claim queue. */
public interface ClaimRepositoryDao {

  @SqlUpdate(
      "INSERT INTO pending_claims "
          + "(player_uuid, item_blob, source, created_at, state) "
          + "VALUES (:playerId, :item, :source, :createdAt, 'PENDING')")
  void insert(
      @Bind("playerId") String playerId,
      @Bind("item") byte[] item,
      @Bind("source") String source,
      @Bind("createdAt") long createdAt);

  @SqlQuery("SELECT last_insert_rowid()")
  long lastInsertedId();

  @SqlUpdate(
      "UPDATE pending_claims SET state = 'PENDING', delivery_token = NULL, claimed_at = NULL "
          + "WHERE player_uuid = :playerId AND state = 'DELIVERING' "
          + "AND (claimed_at IS NULL OR claimed_at < :staleBefore)")
  void resetStale(@Bind("playerId") String playerId, @Bind("staleBefore") long staleBefore);

  @SqlUpdate(
      "UPDATE pending_claims SET state = 'DELIVERING', delivery_token = :deliveryToken, "
          + "claimed_at = :claimedAt WHERE player_uuid = :playerId AND state = 'PENDING'")
  void claimPending(
      @Bind("playerId") String playerId,
      @Bind("deliveryToken") String deliveryToken,
      @Bind("claimedAt") long claimedAt);

  @RegisterConstructorMapper(ClaimRow.class)
  @SqlQuery(
      "SELECT id, player_uuid AS playerId, item_blob AS itemBytes, source, "
          + "created_at AS createdAt FROM pending_claims "
          + "WHERE player_uuid = :playerId AND state = 'DELIVERING' "
          + "AND delivery_token = :deliveryToken ORDER BY id")
  List<ClaimRow> selectClaimed(
      @Bind("playerId") String playerId, @Bind("deliveryToken") String deliveryToken);

  @SqlUpdate(
      "DELETE FROM pending_claims WHERE id = :claimId AND state = 'DELIVERING' "
          + "AND delivery_token = :deliveryToken")
  int complete(
      @Bind("claimId") long claimId, @Bind("deliveryToken") String deliveryToken);

  @SqlUpdate(
      "UPDATE pending_claims SET item_blob = :item, state = 'PENDING', "
          + "delivery_token = NULL, claimed_at = NULL WHERE id = :claimId "
          + "AND state = 'DELIVERING' AND delivery_token = :deliveryToken")
  int retain(
      @Bind("item") byte[] item,
      @Bind("claimId") long claimId,
      @Bind("deliveryToken") String deliveryToken);

  @SqlUpdate(
      "UPDATE pending_claims SET state = 'PENDING', delivery_token = NULL, claimed_at = NULL "
          + "WHERE id = :claimId AND state = 'DELIVERING' AND delivery_token = :deliveryToken")
  int release(
      @Bind("claimId") long claimId, @Bind("deliveryToken") String deliveryToken);

  record ClaimRow(long id, String playerId, byte[] itemBytes, String source, long createdAt) {}
}

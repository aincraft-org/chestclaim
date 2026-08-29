package org.aincraft.chestclaim.storage;

import com.zaxxer.hikari.HikariConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.aincraft.chestclaim.claim.PendingClaim;
import org.aincraft.db.sql.SqlDatabase;

/** SQLite-backed claim storage with token-guarded delivery transitions. */
public final class SqliteClaimRepository implements ClaimRepository {

  private static final long STALE_DELIVERY_MILLIS = 5 * 60 * 1000L;
  private final SqlDatabase database;
  private final ClaimRepositoryDao dao;

  /** Opens a single-connection SQLite repository at the configured path. */
  public SqliteClaimRepository(Path databasePath) {
    Objects.requireNonNull(databasePath, "databasePath");
    Path absolutePath = databasePath.toAbsolutePath();
    try {
      if (absolutePath.getParent() != null) {
        Files.createDirectories(absolutePath.getParent());
      }
    } catch (IOException exception) {
      throw new IllegalStateException("Could not create claim data directory", exception);
    }

    HikariConfig config = new HikariConfig();
    config.setJdbcUrl("jdbc:sqlite:" + absolutePath);
    config.setMaximumPoolSize(1);
    config.setMinimumIdle(1);
    config.setConnectionInitSql("PRAGMA foreign_keys = ON");

    ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
    SqlDatabase openedDatabase = null;
    try {
      openedDatabase = SqlDatabase.create(config, "classpath:chestclaim-no-runtime-migrations");
      ClaimRepositoryDao openedDao = openedDatabase.onDemand(ClaimRepositoryDao.class);
      database = openedDatabase;
      dao = openedDao;
    } catch (RuntimeException exception) {
      if (openedDatabase != null) {
        openedDatabase.close();
      }
      throw new IllegalStateException("Could not open claim database", exception);
    } finally {
      Thread.currentThread().setContextClassLoader(previousClassLoader);
    }
  }

  @Override
  public void initialize() {
    ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
    try {
      database.jdbi().useTransaction(ClaimSchemaMigrator::migrate);
    } catch (RuntimeException exception) {
      throw new IllegalStateException("Could not migrate claim database", exception);
    } finally {
      Thread.currentThread().setContextClassLoader(previousClassLoader);
    }
  }

  @Override
  public long enqueue(UUID playerId, byte[] item, String source) {
    Objects.requireNonNull(playerId, "playerId");
    Objects.requireNonNull(item, "item");
    Objects.requireNonNull(source, "source");
    long createdAt = System.currentTimeMillis();
    return database.inTransaction(
        ClaimRepositoryDao.class,
        transaction -> {
          transaction.insert(playerId.toString(), item, source, createdAt);
          return transaction.lastInsertedId();
        });
  }

  @Override
  public List<PendingClaim> claimPending(UUID playerId, UUID deliveryToken) {
    Objects.requireNonNull(playerId, "playerId");
    Objects.requireNonNull(deliveryToken, "deliveryToken");
    long now = System.currentTimeMillis();
    List<ClaimRepositoryDao.ClaimRow> rows =
        database.inTransaction(
            ClaimRepositoryDao.class,
            transaction -> {
              String player = playerId.toString();
              String token = deliveryToken.toString();
              transaction.resetStale(player, now - STALE_DELIVERY_MILLIS);
              transaction.claimPending(player, token, now);
              return transaction.selectClaimed(player, token);
            });
    return rows.stream().map(SqliteClaimRepository::toPendingClaim).toList();
  }

  @Override
  public boolean complete(long claimId, UUID deliveryToken) {
    Objects.requireNonNull(deliveryToken, "deliveryToken");
    return dao.complete(claimId, deliveryToken.toString()) == 1;
  }

  @Override
  public boolean retain(PendingClaim claim, UUID deliveryToken, List<byte[]> items) {
    Objects.requireNonNull(claim, "claim");
    Objects.requireNonNull(deliveryToken, "deliveryToken");
    Objects.requireNonNull(items, "items");
    if (items.isEmpty()) {
      throw new IllegalArgumentException("at least one remainder is required");
    }

    return database.inTransaction(
        ClaimRepositoryDao.class,
        transaction -> {
          String token = deliveryToken.toString();
          if (transaction.retain(items.get(0), claim.id(), token) != 1) {
            return false;
          }
          for (byte[] item : items.subList(1, items.size())) {
            transaction.insert(
                claim.playerId().toString(), item, claim.source(), claim.createdAt());
          }
          return true;
        });
  }

  @Override
  public boolean release(long claimId, UUID deliveryToken) {
    Objects.requireNonNull(deliveryToken, "deliveryToken");
    return dao.release(claimId, deliveryToken.toString()) == 1;
  }

  @Override
  public void close() {
    database.close();
  }

  private static PendingClaim toPendingClaim(ClaimRepositoryDao.ClaimRow row) {
    return new PendingClaim(
        row.id(), UUID.fromString(row.playerId()), row.itemBytes(), row.source(), row.createdAt());
  }
}

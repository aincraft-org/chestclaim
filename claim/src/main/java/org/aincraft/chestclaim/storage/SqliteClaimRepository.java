package org.aincraft.chestclaim.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.aincraft.chestclaim.claim.PendingClaim;

/** SQLite-backed claim storage with token-guarded delivery transitions. */
public final class SqliteClaimRepository implements ClaimRepository {

  private static final int SINGLE_UPDATED_ROW = 1;
  private static final long STALE_DELIVERY_MILLIS = 5 * 60 * 1000L;
  private final HikariDataSource dataSource;

  /** Opens a single-connection SQLite repository at the configured path. */
  public SqliteClaimRepository(Path databasePath) {
    Objects.requireNonNull(databasePath, "databasePath");
    Path absolutePath = databasePath.toAbsolutePath();
    Path parent = absolutePath.getParent();
    try {
      if (parent != null) {
        Files.createDirectories(parent);
      }
    } catch (IOException exception) {
      throw new IllegalStateException("Could not create claim data directory", exception);
    }
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl("jdbc:sqlite:" + absolutePath);
    config.setMaximumPoolSize(1);
    config.setMinimumIdle(1);
    config.setConnectionInitSql("PRAGMA foreign_keys = ON");
    dataSource = new HikariDataSource(config);
  }

  @Override
  public void initialize() throws SQLException {
    try (InputStream input = getClass().getResourceAsStream("/sql/schema.sql")) {
      if (input == null) {
        throw new SQLException("Missing claim schema resource");
      }
      String schema = new String(input.readAllBytes(), StandardCharsets.UTF_8);
      try (Connection connection = dataSource.getConnection();
          Statement statement = connection.createStatement()) {
        for (String statementSql : schema.split(";")) {
          if (!statementSql.isBlank()) {
            statement.executeUpdate(statementSql);
          }
        }
      }
    } catch (IOException exception) {
      throw new SQLException("Could not close claim schema resource", exception);
    }
  }

  @Override
  public long enqueue(UUID playerId, byte[] item, String source) throws SQLException {
    String sql =
        "INSERT INTO pending_claims "
            + "(player_uuid, item_blob, source, created_at, state) VALUES (?, ?, ?, ?, 'PENDING')";
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement =
            connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
      statement.setString(1, playerId.toString());
      statement.setBytes(2, item);
      statement.setString(3, source);
      statement.setLong(4, System.currentTimeMillis());
      statement.executeUpdate();
      try (ResultSet keys = statement.getGeneratedKeys()) {
        if (!keys.next()) {
          throw new SQLException("Claim insert returned no generated ID");
        }
        return keys.getLong(1);
      }
    }
  }

  @Override
  public List<PendingClaim> claimPending(UUID playerId, UUID deliveryToken) throws SQLException {
    Objects.requireNonNull(playerId, "playerId");
    Objects.requireNonNull(deliveryToken, "deliveryToken");
    long now = System.currentTimeMillis();
    try (Connection connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      try {
        resetStaleClaims(connection, playerId, now - STALE_DELIVERY_MILLIS);
        claimPendingRows(connection, playerId, deliveryToken, now);
        List<PendingClaim> claims = readClaimedRows(connection, playerId, deliveryToken);
        connection.commit();
        return claims;
      } catch (SQLException exception) {
        rollback(connection, exception);
        throw exception;
      } finally {
        connection.setAutoCommit(true);
      }
    }
  }

  @Override
  public boolean complete(long claimId, UUID deliveryToken) throws SQLException {
    String sql =
        "DELETE FROM pending_claims "
            + "WHERE id = ? AND state = 'DELIVERING' AND delivery_token = ?";
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setLong(1, claimId);
      statement.setString(2, deliveryToken.toString());
      return statement.executeUpdate() == 1;
    }
  }

  @Override
  public boolean retain(PendingClaim claim, UUID deliveryToken, List<byte[]> items)
      throws SQLException {
    Objects.requireNonNull(claim, "claim");
    Objects.requireNonNull(deliveryToken, "deliveryToken");
    Objects.requireNonNull(items, "items");
    if (items.isEmpty()) {
      throw new IllegalArgumentException("at least one remainder is required");
    }
    String updateSql =
        "UPDATE pending_claims SET item_blob = ?, state = 'PENDING', "
            + "delivery_token = NULL, claimed_at = NULL "
            + "WHERE id = ? AND state = 'DELIVERING' AND delivery_token = ?";
    String insertSql =
        "INSERT INTO pending_claims "
            + "(player_uuid, item_blob, source, created_at, state) VALUES (?, ?, ?, ?, 'PENDING')";
    try (Connection connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      try (PreparedStatement update = connection.prepareStatement(updateSql);
          PreparedStatement insert = connection.prepareStatement(insertSql)) {
        update.setBytes(1, items.get(0));
        update.setLong(2, claim.id());
        update.setString(3, deliveryToken.toString());
        if (update.executeUpdate() != SINGLE_UPDATED_ROW) {
          connection.rollback();
          return false;
        }
        for (byte[] item : items.subList(1, items.size())) {
          insert.setString(1, claim.playerId().toString());
          insert.setBytes(2, item);
          insert.setString(3, claim.source());
          insert.setLong(4, claim.createdAt());
          insert.addBatch();
        }
        insert.executeBatch();
        connection.commit();
        return true;
      } catch (SQLException exception) {
        rollback(connection, exception);
        throw exception;
      } finally {
        connection.setAutoCommit(true);
      }
    }
  }

  @Override
  public boolean release(long claimId, UUID deliveryToken) throws SQLException {
    String sql =
        "UPDATE pending_claims SET state = 'PENDING', delivery_token = NULL, claimed_at = NULL "
            + "WHERE id = ? AND state = 'DELIVERING' AND delivery_token = ?";
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setLong(1, claimId);
      statement.setString(2, deliveryToken.toString());
      return statement.executeUpdate() == 1;
    }
  }

  @Override
  public void close() {
    dataSource.close();
  }

  private static void resetStaleClaims(Connection connection, UUID playerId, long staleBefore)
      throws SQLException {
    String sql =
        "UPDATE pending_claims SET state = 'PENDING', delivery_token = NULL, claimed_at = NULL "
            + "WHERE player_uuid = ? AND state = 'DELIVERING' "
            + "AND (claimed_at IS NULL OR claimed_at < ?)";
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, playerId.toString());
      statement.setLong(2, staleBefore);
      statement.executeUpdate();
    }
  }

  private static void claimPendingRows(
      Connection connection, UUID playerId, UUID deliveryToken, long now) throws SQLException {
    String sql =
        "UPDATE pending_claims SET state = 'DELIVERING', delivery_token = ?, claimed_at = ? "
            + "WHERE player_uuid = ? AND state = 'PENDING'";
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, deliveryToken.toString());
      statement.setLong(2, now);
      statement.setString(3, playerId.toString());
      statement.executeUpdate();
    }
  }

  private static List<PendingClaim> readClaimedRows(
      Connection connection, UUID playerId, UUID deliveryToken) throws SQLException {
    String sql =
        "SELECT id, player_uuid, item_blob, source, created_at FROM pending_claims "
            + "WHERE player_uuid = ? AND state = 'DELIVERING' AND delivery_token = ? ORDER BY id";
    List<PendingClaim> claims = new ArrayList<>();
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, playerId.toString());
      statement.setString(2, deliveryToken.toString());
      try (ResultSet results = statement.executeQuery()) {
        while (results.next()) {
          claims.add(
              new PendingClaim(
                  results.getLong("id"),
                  UUID.fromString(results.getString("player_uuid")),
                  results.getBytes("item_blob"),
                  results.getString("source"),
                  results.getLong("created_at")));
        }
      }
    }
    return claims;
  }

  private static void rollback(Connection connection, SQLException original) {
    try {
      connection.rollback();
    } catch (SQLException rollbackException) {
      original.addSuppressed(rollbackException);
    }
  }
}

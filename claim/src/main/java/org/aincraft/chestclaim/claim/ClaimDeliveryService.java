package org.aincraft.chestclaim.claim;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import org.aincraft.chestclaim.storage.ClaimRepository;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Coordinates asynchronous claim loading with main-thread inventory mutation. */
public final class ClaimDeliveryService {

  private final ClaimRepository repository;
  private final ItemStackCodec itemStackCodec;
  private final Executor databaseExecutor;
  private final Executor mainThreadExecutor;
  private final Consumer<Throwable> failureLogger;

  /** Creates a delivery coordinator with explicit database and main-thread executors. */
  public ClaimDeliveryService(
      ClaimRepository repository,
      ItemStackCodec itemStackCodec,
      Executor databaseExecutor,
      Executor mainThreadExecutor,
      Consumer<Throwable> failureLogger) {
    this.repository = Objects.requireNonNull(repository, "repository");
    this.itemStackCodec = Objects.requireNonNull(itemStackCodec, "itemStackCodec");
    this.databaseExecutor = Objects.requireNonNull(databaseExecutor, "databaseExecutor");
    this.mainThreadExecutor = Objects.requireNonNull(mainThreadExecutor, "mainThreadExecutor");
    this.failureLogger = Objects.requireNonNull(failureLogger, "failureLogger");
  }

  /** Delivers all currently pending claims for a joining player. */
  public CompletableFuture<Void> deliver(Player player) {
    Objects.requireNonNull(player, "player");
    UUID playerId = Objects.requireNonNull(player.getUniqueId(), "player.uniqueId");
    UUID deliveryToken = UUID.randomUUID();
    return database(
            () -> repository.claimPending(playerId, deliveryToken),
            "Could not load pending claims for " + playerId)
        .thenComposeAsync(
            claims -> deliverOnMainThread(player, deliveryToken, claims), mainThreadExecutor);
  }

  private CompletableFuture<Void> deliverOnMainThread(
      Player player, UUID deliveryToken, List<PendingClaim> claims) {
    List<CompletableFuture<Void>> transitions = new ArrayList<>();
    for (PendingClaim claim : claims) {
      transitions.add(deliverClaim(player, deliveryToken, claim));
    }
    return CompletableFuture.allOf(transitions.toArray(CompletableFuture[]::new));
  }

  @SuppressWarnings("PMD.AvoidCatchingGenericException")
  private CompletableFuture<Void> deliverClaim(
      Player player, UUID deliveryToken, PendingClaim claim) {
    try {
      ItemStack item = itemStackCodec.decode(claim.itemBytes());
      if (item == null || item.isEmpty()) {
        throw new IllegalArgumentException("claim decoded to an empty item");
      }
      Map<Integer, ItemStack> leftovers =
          Objects.requireNonNull(player.getInventory().addItem(item), "inventory leftovers");
      if (leftovers.isEmpty()) {
        return databaseTransition(
            () -> requireTransition(repository.complete(claim.id(), deliveryToken)),
            "Could not complete claim " + claim.id());
      }
      List<byte[]> remainderBytes = encodeRemainders(leftovers);
      if (remainderBytes.isEmpty()) {
        return databaseTransition(
            () -> requireTransition(repository.complete(claim.id(), deliveryToken)),
            "Could not complete claim " + claim.id());
      }
      return databaseTransition(
          () -> requireTransition(repository.retain(claim, deliveryToken, remainderBytes)),
          "Could not retain leftovers for claim " + claim.id());
    } catch (RuntimeException exception) {
      failureLogger.accept(exception);
      return databaseTransition(
          () -> requireTransition(repository.release(claim.id(), deliveryToken)),
          "Could not release claim " + claim.id());
    }
  }

  private List<byte[]> encodeRemainders(Map<Integer, ItemStack> leftovers) {
    List<byte[]> encoded = new ArrayList<>();
    for (ItemStack remainder : leftovers.values()) {
      if (remainder == null || remainder.isEmpty()) {
        continue;
      }
      encoded.add(itemStackCodec.encode(remainder));
    }
    return List.copyOf(encoded);
  }

  private CompletableFuture<Void> databaseTransition(
      SqlSupplier<Boolean> transition, String failureMessage) {
    return database(
        () -> {
          requireTransition(transition.get());
          return null;
        },
        failureMessage);
  }

  private <T> CompletableFuture<T> database(SqlSupplier<T> operation, String failureMessage) {
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            return operation.get();
          } catch (SQLException exception) {
            throw new CompletionException(failureMessage, exception);
          }
        },
        databaseExecutor);
  }

  private static boolean requireTransition(boolean transitioned) {
    if (!transitioned) {
      throw new IllegalStateException("Claim delivery token no longer owns the row");
    }
    return true;
  }

  @FunctionalInterface
  private interface SqlSupplier<T> {
    T get() throws SQLException;
  }
}

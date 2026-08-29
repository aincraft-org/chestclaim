package org.aincraft.chestclaim.claim;

import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import org.aincraft.chestclaim.api.ClaimService;
import org.aincraft.chestclaim.protection.ProtectionResolver;
import org.aincraft.chestclaim.storage.ClaimRepository;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

/** Default claim service implementation. */
public final class ClaimServiceImpl implements ClaimService {

  public static final String LOCKED_CHEST_RETURN_SOURCE = "locked-chest-return";

  private final ClaimRepository repository;
  private final ProtectionResolver protectionResolver;
  private final ItemStackCodec itemStackCodec;
  private final Executor databaseExecutor;

  /** Creates a service backed by the supplied asynchronous repository boundary. */
  public ClaimServiceImpl(
      ClaimRepository repository,
      ProtectionResolver protectionResolver,
      ItemStackCodec itemStackCodec,
      Executor databaseExecutor) {
    this.repository = Objects.requireNonNull(repository, "repository");
    this.protectionResolver = Objects.requireNonNull(protectionResolver, "protectionResolver");
    this.itemStackCodec = Objects.requireNonNull(itemStackCodec, "itemStackCodec");
    this.databaseExecutor = Objects.requireNonNull(databaseExecutor, "databaseExecutor");
  }

  @Override
  public CompletableFuture<Void> queueReward(UUID playerId, ItemStack item, String source) {
    validateQueueInput(playerId, item, source);
    byte[] encodedItem = itemStackCodec.encode(item);
    return CompletableFuture.runAsync(
        () -> {
          try {
            repository.enqueue(playerId, encodedItem, source.trim());
          } catch (SQLException exception) {
            throw new CompletionException("Failed to queue claim reward", exception);
          }
        },
        databaseExecutor);
  }

  @Override
  public CompletableFuture<Void> returnLockedChest(Block protectedChest, ItemStack chestItem) {
    UUID owner = protectionResolver.resolveOwner(protectedChest);
    return queueReward(owner, chestItem, LOCKED_CHEST_RETURN_SOURCE);
  }

  private static void validateQueueInput(UUID playerId, ItemStack item, String source) {
    if (playerId == null) {
      throw new IllegalArgumentException("playerId is required");
    }
    if (item == null || item.isEmpty() || item.getAmount() <= 0) {
      throw new IllegalArgumentException("item must contain at least one non-empty item");
    }
    if (source == null || source.isBlank()) {
      throw new IllegalArgumentException("source is required");
    }
  }
}

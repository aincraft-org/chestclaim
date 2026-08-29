package org.aincraft.chestclaim.claim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import org.aincraft.chestclaim.storage.ClaimRepository;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

class ClaimDeliveryServiceTest {

  private static final Executor DIRECT_EXECUTOR = Runnable::run;

  @Test
  void fullyAcceptedRewardIsCompleted() {
    UUID playerId = UUID.randomUUID();
    try (RecordingRepository repository = repositoryFor(playerId, new byte[] {5})) {
      PlayerInventory inventory = mock(PlayerInventory.class);
      when(inventory.addItem(any(ItemStack[].class))).thenReturn(new HashMap<>());

      newService(repository, new CodecFixture(), inventory, playerId)
          .deliver(player(playerId, inventory))
          .join();

      assertEquals(1, repository.completed.size());
      assertTrue(repository.retained.isEmpty());
    }
  }

  @Test
  void partiallyAcceptedRewardRetainsOnlyLeftovers() {
    UUID playerId = UUID.randomUUID();
    try (RecordingRepository repository = repositoryFor(playerId, new byte[] {5})) {
      PlayerInventory inventory = mock(PlayerInventory.class);
      ItemStack leftover = item(2);
      HashMap<Integer, ItemStack> leftovers = new HashMap<>();
      leftovers.put(0, leftover);
      when(inventory.addItem(any(ItemStack[].class))).thenReturn(leftovers);
      newService(repository, new CodecFixture(), inventory, playerId)
          .deliver(player(playerId, inventory))
          .join();

      assertTrue(repository.completed.isEmpty());
      assertEquals(List.of(List.of(2)), repository.retained);
    }
  }

  @Test
  void decodeFailureReleasesClaimWithoutTouchingInventory() {
    UUID playerId = UUID.randomUUID();
    try (RecordingRepository repository = repositoryFor(playerId, new byte[] {5})) {
      PlayerInventory inventory = mock(PlayerInventory.class);
      List<Throwable> failures = new ArrayList<>();
      ItemStackCodec codec =
          new ItemStackCodec() {
            @Override
            public byte[] encode(ItemStack item) {
              return new byte[] {(byte) item.getAmount()};
            }

            @Override
            public ItemStack decode(byte[] bytes) {
              throw new IllegalArgumentException("bad item");
            }
          };

      newService(repository, codec, inventory, playerId, failures::add)
          .deliver(player(playerId, inventory))
          .join();

      assertEquals(1, repository.released.size());
      assertEquals(1, failures.size());
      verifyNoInteractions(inventory);
    }
  }

  private static ClaimDeliveryService newService(
      RecordingRepository repository,
      ItemStackCodec codec,
      PlayerInventory inventory,
      UUID playerId) {
    return newService(repository, codec, inventory, playerId, ignored -> {});
  }

  private static ClaimDeliveryService newService(
      RecordingRepository repository,
      ItemStackCodec codec,
      PlayerInventory inventory,
      UUID playerId,
      Consumer<Throwable> failureLogger) {
    return new ClaimDeliveryService(
        repository, codec, DIRECT_EXECUTOR, DIRECT_EXECUTOR, failureLogger);
  }

  private static Player player(UUID playerId, PlayerInventory inventory) {
    Player player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(playerId);
    when(player.getInventory()).thenReturn(inventory);
    return player;
  }

  private static ItemStack item(int amount) {
    ItemStack item = mock(ItemStack.class);
    when(item.isEmpty()).thenReturn(false);
    when(item.getAmount()).thenReturn(amount);
    return item;
  }

  private static RecordingRepository repositoryFor(UUID playerId, byte[] bytes) {
    RecordingRepository repository = new RecordingRepository();
    repository.claims.add(new PendingClaim(1, playerId, bytes, "shop", 1));
    return repository;
  }

  private static final class CodecFixture implements ItemStackCodec {
    @Override
    public byte[] encode(ItemStack item) {
      return new byte[] {(byte) item.getAmount()};
    }

    @Override
    public ItemStack decode(byte[] bytes) {
      return item(bytes[0]);
    }
  }

  private static final class RecordingRepository implements ClaimRepository {
    private final List<PendingClaim> claims = new ArrayList<>();
    private final List<Long> completed = new ArrayList<>();
    private final List<List<Integer>> retained = new ArrayList<>();
    private final List<Long> released = new ArrayList<>();

    @Override
    public void initialize() throws SQLException {}

    @Override
    public long enqueue(UUID playerId, byte[] item, String source) throws SQLException {
      return 0;
    }

    @Override
    public List<PendingClaim> claimPending(UUID playerId, UUID deliveryToken) throws SQLException {
      return List.copyOf(claims);
    }

    @Override
    public boolean complete(long claimId, UUID deliveryToken) throws SQLException {
      completed.add(claimId);
      return true;
    }

    @Override
    public boolean retain(PendingClaim claim, UUID deliveryToken, List<byte[]> items)
        throws SQLException {
      retained.add(items.stream().map(item -> (int) item[0]).toList());
      return true;
    }

    @Override
    public boolean release(long claimId, UUID deliveryToken) throws SQLException {
      released.add(claimId);
      return true;
    }

    @Override
    public void close() {}
  }
}

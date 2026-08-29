package org.aincraft.chestclaim.claim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.aincraft.chestclaim.protection.ProtectionOwnerResolutionException;
import org.aincraft.chestclaim.protection.ProtectionProvider;
import org.aincraft.chestclaim.protection.ProtectionResolver;
import org.aincraft.chestclaim.storage.ClaimRepository;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class ClaimServiceContractTest {

  private static final Block BLOCK =
      (Block)
          Proxy.newProxyInstance(
              Thread.currentThread().getContextClassLoader(),
              new Class<?>[] {Block.class},
              (proxy, method, args) -> null);
  private static final String SHOP_SOURCE = "shop";
  private static final String LWC_PROVIDER_ID = "lwc";
  private static final Executor DIRECT_EXECUTOR = Runnable::run;

  @Test
  void queueRewardPersistsValidReward() {
    try (RecordingRepository repository = new RecordingRepository()) {
      ClaimServiceImpl service = newService(repository, ownerProvider());

      service.queueReward(UUID.randomUUID(), validItem(), SHOP_SOURCE).join();

      assertEquals(1, repository.enqueued.size());
      assertEquals(SHOP_SOURCE, repository.enqueued.get(0).source());
    }
  }

  @Test
  void queueRewardRejectsInvalidInputWithoutWriting() {
    try (RecordingRepository repository = new RecordingRepository()) {
      ClaimServiceImpl service = newService(repository, ownerProvider());

      assertThrows(
          IllegalArgumentException.class,
          () -> service.queueReward(null, validItem(), SHOP_SOURCE).join());
      assertThrows(
          IllegalArgumentException.class,
          () -> service.queueReward(UUID.randomUUID(), emptyItem(), SHOP_SOURCE).join());
      assertThrows(
          IllegalArgumentException.class,
          () -> service.queueReward(UUID.randomUUID(), validItem(), " ").join());

      assertEquals(0, repository.enqueued.size());
    }
  }

  @Test
  void returnLockedChestQueuesForResolvedOwner() {
    try (RecordingRepository repository = new RecordingRepository()) {
      UUID owner = UUID.randomUUID();
      ClaimServiceImpl service =
          newService(repository, provider(LWC_PROVIDER_ID, Optional.of(owner)));

      service.returnLockedChest(BLOCK, validItem()).join();

      assertEquals(1, repository.enqueued.size());
      assertEquals(owner, repository.enqueued.get(0).playerId());
      assertEquals("locked-chest-return", repository.enqueued.get(0).source());
    }
  }

  @Test
  void returnLockedChestFailsBeforeWritingWhenOwnerIsUnknown() {
    try (RecordingRepository repository = new RecordingRepository()) {
      ClaimServiceImpl service =
          newService(repository, provider(LWC_PROVIDER_ID, Optional.empty()));
      assertThrows(
          ProtectionOwnerResolutionException.class,
          () -> service.returnLockedChest(BLOCK, validItem()));

      assertEquals(0, repository.enqueued.size());
    }
  }

  private static ItemStack validItem() {
    ItemStack item = mock(ItemStack.class);
    when(item.isEmpty()).thenReturn(false);
    when(item.getAmount()).thenReturn(1);
    return item;
  }

  private static ItemStack emptyItem() {
    ItemStack item = mock(ItemStack.class);
    when(item.isEmpty()).thenReturn(true);
    return item;
  }

  private static ClaimServiceImpl newService(
      RecordingRepository repository, ProtectionProvider provider) {
    return new ClaimServiceImpl(
        repository, new ProtectionResolver(List.of(provider)), new CodecFixture(), DIRECT_EXECUTOR);
  }

  private static ProtectionProvider ownerProvider() {
    return provider(LWC_PROVIDER_ID, Optional.of(UUID.randomUUID()));
  }

  private static ProtectionProvider provider(String id, Optional<UUID> owner) {
    return new ProtectionProvider() {
      @Override
      public String id() {
        return id;
      }

      @Override
      public Optional<UUID> resolveOwner(Block block) {
        return owner;
      }
    };
  }

  private record Enqueued(UUID playerId, byte[] item, String source) {}

  private static final class RecordingRepository implements ClaimRepository {
    private final List<Enqueued> enqueued = new ArrayList<>();

    @Override
    public void initialize() throws SQLException {}

    @Override
    public long enqueue(UUID playerId, byte[] item, String source) throws SQLException {
      enqueued.add(new Enqueued(playerId, item, source));
      return enqueued.size();
    }

    @Override
    public List<PendingClaim> claimPending(UUID playerId, UUID deliveryToken) throws SQLException {
      return List.of();
    }

    @Override
    public boolean complete(long claimId, UUID deliveryToken) throws SQLException {
      return true;
    }

    @Override
    public boolean retain(PendingClaim claim, UUID deliveryToken, List<byte[]> items)
        throws SQLException {
      return true;
    }

    @Override
    public boolean release(long claimId, UUID deliveryToken) throws SQLException {
      return true;
    }

    @Override
    public void close() {}
  }

  private static final class CodecFixture implements ItemStackCodec {
    @Override
    public byte[] encode(ItemStack item) {
      return new byte[] {(byte) item.getAmount()};
    }

    @Override
    public ItemStack decode(byte[] bytes) {
      ItemStack item = mock(ItemStack.class);
      when(item.isEmpty()).thenReturn(false);
      when(item.getAmount()).thenReturn((int) bytes[0]);
      return item;
    }
  }
}

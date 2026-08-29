package org.aincraft.chestclaim.protection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.UUID;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;

class ReflectiveProtectionProviderTest {

  private static final Block BLOCK =
      (Block)
          Proxy.newProxyInstance(
              Thread.currentThread().getContextClassLoader(),
              new Class<?>[] {Block.class},
              (proxy, method, args) -> null);

  @Test
  void lwcAdapterReadsUuidOwnerFromProtectionRecord() throws ProtectionProviderException {
    UUID owner = UUID.randomUUID();

    UUID resolved = new LwcProtectionProvider(new FakeLwc(owner)).resolveOwner(BLOCK).orElseThrow();

    assertEquals(owner, resolved);
  }

  @Test
  void skipsUnsupportedLookupOverloads() throws ProtectionProviderException {
    UUID owner = UUID.randomUUID();

    UUID resolved =
        new LwcProtectionProvider(new FakeLwcWithUnrelatedOverload(owner))
            .resolveOwner(BLOCK)
            .orElseThrow();

    assertEquals(owner, resolved);
  }

  @Test
  void lwcAdapterUsesPluginLwcAccessor() throws ProtectionProviderException {
    UUID owner = UUID.randomUUID();

    UUID resolved =
        new LwcProtectionProvider(new FakeLwcPlugin(owner)).resolveOwner(BLOCK).orElseThrow();

    assertEquals(owner, resolved);
  }

  @Test
  void adapterReadsOwnerFromPermissionMap() throws ProtectionProviderException {
    UUID owner = UUID.randomUUID();

    UUID resolved =
        new LwcProtectionProvider(new FakeLwcWithPermissionMap(owner))
            .resolveOwner(BLOCK)
            .orElseThrow();

    assertEquals(owner, resolved);
  }

  @Test
  void adapterRejectsBlankOwnerFromProtectedRecord() {
    ProtectionProviderException exception =
        assertThrows(
            ProtectionProviderException.class,
            () -> new LwcProtectionProvider(new FakeLwc(" ")).resolveOwner(BLOCK));

    assertEquals("lwc returned a protected record without a usable owner", exception.getMessage());
  }

  @Test
  void boltAdapterReadsOwnerThroughProtectionManager() throws ProtectionProviderException {
    UUID owner = UUID.randomUUID();

    UUID resolved =
        new BoltProtectionProvider(new FakeBolt(owner)).resolveOwner(BLOCK).orElseThrow();

    assertEquals(owner, resolved);
  }

  @Test
  void locketteProAdapterReadsDirectOwnerLookup() throws ProtectionProviderException {
    UUID owner = UUID.randomUUID();

    UUID resolved =
        new LocketteProProtectionProvider(new FakeLockettePro(owner))
            .resolveOwner(BLOCK)
            .orElseThrow();

    assertEquals(owner, resolved);
  }

  private static final class FakeBolt {
    private final UUID owner;

    private FakeBolt(UUID owner) {
      this.owner = owner;
    }

    public FakeBoltManager getProtectionManager() {
      return new FakeBoltManager(owner);
    }
  }

  private static final class FakeBoltManager {
    private final UUID owner;

    private FakeBoltManager(UUID owner) {
      this.owner = owner;
    }

    public FakeProtection getProtection(Block block) {
      return new FakeProtection(owner);
    }
  }

  private static final class FakeLockettePro {
    private final UUID owner;

    private FakeLockettePro(UUID owner) {
      this.owner = owner;
    }

    public UUID getProtectedOwner(Block block) {
      return owner;
    }
  }

  private static final class FakeLwcPlugin {
    private final UUID owner;

    private FakeLwcPlugin(UUID owner) {
      this.owner = owner;
    }

    public FakeLwc getLwc() {
      return new FakeLwc(owner);
    }
  }

  private static final class FakeLwc {
    private final Object owner;

    private FakeLwc(Object owner) {
      this.owner = owner;
    }

    public FakeProtection findProtection(Block block) {
      return new FakeProtection(owner);
    }
  }

  private static final class FakeLwcWithUnrelatedOverload {
    private final UUID owner;

    private FakeLwcWithUnrelatedOverload(UUID owner) {
      this.owner = owner;
    }

    public FakeProtection findProtection(String ignored) {
      return new FakeProtection(owner);
    }

    public FakeProtection getProtection(Block block) {
      return new FakeProtection(owner);
    }
  }

  private enum FakePermission {
    OWNER
  }

  private static final class FakeLwcWithPermissionMap {
    private final UUID owner;

    private FakeLwcWithPermissionMap(UUID owner) {
      this.owner = owner;
    }

    public FakePermissionRecord findProtection(Block block) {
      return new FakePermissionRecord(owner);
    }
  }

  private static final class FakePermissionRecord {
    private final UUID owner;

    private FakePermissionRecord(UUID owner) {
      this.owner = owner;
    }

    public boolean hasPdcData() {
      return true;
    }

    public boolean isLocked() {
      return true;
    }

    public Map<String, FakePermission> permissions() {
      return Map.of(owner.toString(), FakePermission.OWNER);
    }
  }

  private static final class FakeProtection {
    private final Object owner;

    private FakeProtection(Object owner) {
      this.owner = owner;
    }

    public Object getOwner() {
      return owner;
    }
  }
}

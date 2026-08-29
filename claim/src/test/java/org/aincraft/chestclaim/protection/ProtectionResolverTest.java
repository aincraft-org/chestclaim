package org.aincraft.chestclaim.protection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;

class ProtectionResolverTest {

  private static final String LWC_PROVIDER_ID = "lwc";
  private static final Block BLOCK =
      (Block)
          Proxy.newProxyInstance(
              Thread.currentThread().getContextClassLoader(),
              new Class<?>[] {Block.class},
              (proxy, method, args) -> defaultValue(method.getReturnType()));

  @Test
  void resolvesOneDefinitiveOwner() throws ProtectionOwnerResolutionException {
    UUID owner = UUID.randomUUID();

    UUID resolved =
        new ProtectionResolver(List.of(provider(LWC_PROVIDER_ID, Optional.of(owner))))
            .resolveOwner(BLOCK);

    assertEquals(owner, resolved);
  }

  @Test
  void acceptsMatchingOwnersFromMultipleProviders() throws ProtectionOwnerResolutionException {
    UUID owner = UUID.randomUUID();

    UUID resolved =
        new ProtectionResolver(
                List.of(
                    provider(LWC_PROVIDER_ID, Optional.of(owner)),
                    provider("bolt", Optional.of(owner))))
            .resolveOwner(BLOCK);

    assertEquals(owner, resolved);
  }

  @Test
  void rejectsMissingOwner() {
    ProtectionOwnerResolutionException exception =
        assertThrows(
            ProtectionOwnerResolutionException.class,
            () ->
                new ProtectionResolver(List.of(provider(LWC_PROVIDER_ID, Optional.empty())))
                    .resolveOwner(BLOCK));

    assertEquals("Unable to determine a definitive protected-block owner", exception.getMessage());
  }

  @Test
  void wrapsProviderFailure() {
    ProtectionProviderException cause = new ProtectionProviderException("provider failed");
    ProtectionOwnerResolutionException exception =
        assertThrows(
            ProtectionOwnerResolutionException.class,
            () ->
                new ProtectionResolver(
                        List.of(
                            new ProtectionProvider() {
                              @Override
                              public String id() {
                                return LWC_PROVIDER_ID;
                              }

                              @Override
                              public Optional<UUID> resolveOwner(Block block)
                                  throws ProtectionProviderException {
                                throw cause;
                              }
                            }))
                    .resolveOwner(BLOCK));

    assertSame(cause, exception.getCause());
  }

  @Test
  void rejectsConflictingOwners() {
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();

    ProtectionOwnerResolutionException exception =
        assertThrows(
            ProtectionOwnerResolutionException.class,
            () ->
                new ProtectionResolver(
                        List.of(
                            provider(LWC_PROVIDER_ID, Optional.of(first)),
                            provider("bolt", Optional.of(second))))
                    .resolveOwner(BLOCK));

    assertEquals("Protection providers returned conflicting owners", exception.getMessage());
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

  private static Object defaultValue(Class<?> type) {
    if (!type.isPrimitive()) {
      return null;
    }
    if (type == boolean.class) {
      return false;
    }
    if (type == char.class) {
      return '\0';
    }
    if (type == byte.class) {
      return (byte) 0;
    }
    if (type == short.class) {
      return (short) 0;
    }
    if (type == int.class) {
      return 0;
    }
    if (type == long.class) {
      return 0L;
    }
    if (type == float.class) {
      return 0F;
    }
    if (type == double.class) {
      return 0D;
    }
    return null;
  }
}

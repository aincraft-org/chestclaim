package org.aincraft.chestclaim.protection;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.bukkit.block.Block;

/** Resolves a protected block only when every positive provider agrees. */
public final class ProtectionResolver {

  private static final String UNKNOWN_OWNER_MESSAGE =
      "Unable to determine a definitive protected-block owner";
  private static final String CONFLICTING_OWNER_MESSAGE =
      "Protection providers returned conflicting owners";

  private static final int MAX_SINGLE_OWNER_COUNT = 1;
  private final List<ProtectionProvider> providers;

  /** Creates a resolver with providers checked in list order. */
  public ProtectionResolver(List<ProtectionProvider> providers) {
    this.providers = List.copyOf(Objects.requireNonNull(providers, "providers"));
  }

  /** Resolves a definitive owner or fails closed when ownership is uncertain. */
  @SuppressWarnings("PMD.AvoidCatchingGenericException")
  public UUID resolveOwner(Block block) {
    Objects.requireNonNull(block, "block");
    Set<UUID> owners = new LinkedHashSet<>();
    for (ProtectionProvider provider : providers) {
      if (provider == null) {
        throw new ProtectionOwnerResolutionException(UNKNOWN_OWNER_MESSAGE);
      }
      try {
        var owner = provider.resolveOwner(block);
        if (owner == null) {
          throw new ProtectionProviderException("Provider returned null instead of Optional");
        }
        owner.ifPresent(owners::add);
      } catch (ProtectionProviderException exception) {
        throw new ProtectionOwnerResolutionException(
            "Protection provider '" + provider.id() + "' failed", exception);
      } catch (RuntimeException exception) {
        throw new ProtectionOwnerResolutionException(
            "Protection provider '" + provider.id() + "' failed", exception);
      }
    }
    if (owners.size() > MAX_SINGLE_OWNER_COUNT) {
      throw new ProtectionOwnerResolutionException(CONFLICTING_OWNER_MESSAGE);
    }
    return owners.stream()
        .findFirst()
        .orElseThrow(() -> new ProtectionOwnerResolutionException(UNKNOWN_OWNER_MESSAGE));
  }
}

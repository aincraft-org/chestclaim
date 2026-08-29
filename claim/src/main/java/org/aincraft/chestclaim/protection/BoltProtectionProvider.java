package org.aincraft.chestclaim.protection;

import java.util.List;

/** Reflective Bolt protection lookup. */
public final class BoltProtectionProvider extends ReflectiveProtectionProvider {

  /** Creates an adapter for an enabled Bolt plugin instance. */
  public BoltProtectionProvider(Object plugin) {
    super(
        "bolt",
        plugin,
        List.of(
            "getProtection",
            "getProtectionAt",
            "getProtectedBlock",
            "loadProtection",
            "findProtection"),
        List.of("getProtectionManager", "getManager", "getAPI"),
        List.of("org.popcraft.bolt.BoltAPI"));
  }
}

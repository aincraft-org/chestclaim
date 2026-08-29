package org.aincraft.chestclaim.protection;

import java.util.List;

/** Reflective LockettePro protection lookup. */
public final class LocketteProProtectionProvider extends ReflectiveProtectionProvider {

  /** Creates an adapter for an enabled LockettePro plugin instance. */
  public LocketteProProtectionProvider(Object plugin) {
    super(
        "lockettepro",
        plugin,
        List.of("getProtectedOwner", "getOwner", "getProtection", "getProtector", "getLockData"),
        List.of("getAPI", "getManager"),
        List.of(
            "me.crafter.mc.lockettepro.LocketteProAPI",
            "me.crafter.mc.lockettepro.ContainerPdcLockManager",
            "org.yi.acru.bukkit.LocketteProAPI"));
  }
}

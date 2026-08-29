package org.aincraft.chestclaim.protection;

import java.util.List;

/** Reflective LWC protection lookup. */
public final class LwcProtectionProvider extends ReflectiveProtectionProvider {

  /** Creates an adapter for an enabled LWC plugin instance. */
  public LwcProtectionProvider(Object plugin) {
    super(
        "lwc",
        plugin,
        List.of("findProtection", "getProtection"),
        List.of("getProtectionManager", "getManager", "getAPI", "getLWC", "getLwc", "getInstance"),
        List.of("com.griefcraft.lwc.LWC"));
  }
}

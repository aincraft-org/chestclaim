package org.aincraft.chestclaim.protection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

/** Finds only enabled optional protection plugins in configured order. */
public final class ProtectionProviderDiscovery {

  private ProtectionProviderDiscovery() {}

  /** Creates adapters for enabled optional providers in the requested order. */
  public static List<ProtectionProvider> discover(
      PluginManager pluginManager, List<String> configuredOrder) {
    Objects.requireNonNull(pluginManager, "pluginManager");
    Objects.requireNonNull(configuredOrder, "configuredOrder");
    List<ProtectionProvider> providers = new ArrayList<>();
    for (String configuredId : configuredOrder) {
      if (configuredId == null) {
        continue;
      }
      String id = configuredId.trim().toLowerCase(Locale.ROOT);
      Plugin plugin = pluginManager.getPlugin(pluginName(id));
      if (plugin == null || !plugin.isEnabled()) {
        continue;
      }
      ProtectionProvider provider = providerFor(id, plugin);
      if (provider != null) {
        providers.add(provider);
      }
    }
    return List.copyOf(providers);
  }

  private static String pluginName(String id) {
    return switch (id) {
      case "lwc" -> "LWC";
      case "bolt" -> "Bolt";
      case "lockettepro" -> "LockettePro";
      default -> id;
    };
  }

  private static ProtectionProvider providerFor(String id, Plugin plugin) {
    return switch (id) {
      case "lwc" -> new LwcProtectionProvider(plugin);
      case "bolt" -> new BoltProtectionProvider(plugin);
      case "lockettepro" -> new LocketteProProtectionProvider(plugin);
      default -> null;
    };
  }
}

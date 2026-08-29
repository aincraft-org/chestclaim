package org.aincraft.chestclaim.protection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;

class ProtectionProviderDiscoveryTest {

  @Test
  void absentOptionalProvidersDoNotPreventDiscovery() {
    PluginManager pluginManager = mock(PluginManager.class);

    List<ProtectionProvider> providers =
        ProtectionProviderDiscovery.discover(pluginManager, List.of("lwc", "bolt", "lockettepro"));

    assertEquals(List.of(), providers);
  }

  @Test
  void enabledProvidersFollowConfiguredOrder() {
    PluginManager pluginManager = mock(PluginManager.class);
    Plugin lwc = enabledPlugin();
    Plugin bolt = enabledPlugin();
    when(pluginManager.getPlugin("LWC")).thenReturn(lwc);
    when(pluginManager.getPlugin("Bolt")).thenReturn(bolt);

    List<ProtectionProvider> providers =
        ProtectionProviderDiscovery.discover(pluginManager, List.of("bolt", "lwc", "lockettepro"));

    assertEquals(List.of("bolt", "lwc"), providers.stream().map(ProtectionProvider::id).toList());
  }

  private static Plugin enabledPlugin() {
    Plugin plugin = mock(Plugin.class);
    when(plugin.isEnabled()).thenReturn(true);
    return plugin;
  }
}

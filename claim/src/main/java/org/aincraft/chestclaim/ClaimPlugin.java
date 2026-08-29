package org.aincraft.chestclaim;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import org.aincraft.chestclaim.api.ClaimService;
import org.aincraft.chestclaim.claim.BukkitItemStackCodec;
import org.aincraft.chestclaim.claim.ClaimDeliveryService;
import org.aincraft.chestclaim.claim.ClaimJoinListener;
import org.aincraft.chestclaim.claim.ClaimServiceImpl;
import org.aincraft.chestclaim.protection.ProtectionProvider;
import org.aincraft.chestclaim.protection.ProtectionProviderDiscovery;
import org.aincraft.chestclaim.protection.ProtectionResolver;
import org.aincraft.chestclaim.storage.ClaimRepository;
import org.aincraft.chestclaim.storage.SqliteClaimRepository;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/** Plugin entrypoint for durable reward claims. */
public final class ClaimPlugin extends JavaPlugin {

  private ExecutorService databaseExecutor;
  private ClaimRepository repository;

  @Override
  public void onEnable() {
    saveDefaultConfig();
    databaseExecutor =
        Executors.newSingleThreadExecutor(
            runnable -> {
              Thread thread = new Thread(runnable, "chestclaim-database");
              thread.setDaemon(true);
              return thread;
            });
    try {
      Path databasePath =
          getDataFolder().toPath().resolve(getConfig().getString("database.file", "claim.db"));
      repository = new SqliteClaimRepository(databasePath);
      initializeRepository();
      List<ProtectionProvider> providers =
          ProtectionProviderDiscovery.discover(
              getServer().getPluginManager(),
              getConfig().getStringList("protection-providers.order"));
      ClaimServiceImpl claimService =
          new ClaimServiceImpl(
              repository,
              new ProtectionResolver(providers),
              new BukkitItemStackCodec(),
              databaseExecutor);
      Executor mainThreadExecutor = command -> getServer().getScheduler().runTask(this, command);
      ClaimDeliveryService deliveryService =
          new ClaimDeliveryService(
              repository,
              new BukkitItemStackCodec(),
              databaseExecutor,
              mainThreadExecutor,
              failure -> getLogger().log(Level.SEVERE, "Claim delivery failed", failure));
      getServer()
          .getServicesManager()
          .register(ClaimService.class, claimService, this, ServicePriority.Normal);
      getServer()
          .getPluginManager()
          .registerEvents(
              new ClaimJoinListener(
                  deliveryService,
                  failure -> getLogger().log(Level.SEVERE, "Claim delivery failed", failure)),
              this);
      getLogger()
          .info(
              "ChestClaim enabled; protection providers: "
                  + providers.stream().map(ProtectionProvider::id).toList());
    } catch (SQLException | IllegalArgumentException | IllegalStateException exception) {
      getLogger().log(Level.SEVERE, "ChestClaim could not initialize", exception);
      closeResources();
      Bukkit.getPluginManager().disablePlugin(this);
    }
  }

  @Override
  public void onDisable() {
    Bukkit.getServicesManager().unregister(ClaimService.class, this);
    closeResources();
  }

  private void initializeRepository() throws SQLException {
    Future<Void> initialization =
        databaseExecutor.submit(
            () -> {
              repository.initialize();
              return null;
            });
    try {
      initialization.get();
    } catch (InterruptedException exception) {
      initialization.cancel(true);
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Claim repository initialization interrupted", exception);
    } catch (ExecutionException exception) {
      Throwable cause = exception.getCause();
      if (cause instanceof SQLException sqlException) {
        throw sqlException;
      }
      if (cause instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      throw new IllegalStateException("Claim repository initialization failed", cause);
    }
  }

  private void closeResources() {
    if (databaseExecutor != null) {
      databaseExecutor.shutdownNow();
    }
    if (repository != null) {
      repository.close();
    }
  }
}

package org.aincraft.chestclaim.claim;

import java.util.Objects;
import java.util.function.Consumer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** Starts automatic pending-claim delivery for each joining player. */
public final class ClaimJoinListener implements Listener {

  private final ClaimDeliveryService deliveryService;
  private final Consumer<Throwable> failureLogger;

  /** Creates a listener that logs delivery failures without messaging players. */
  public ClaimJoinListener(
      ClaimDeliveryService deliveryService, Consumer<Throwable> failureLogger) {
    this.deliveryService = Objects.requireNonNull(deliveryService, "deliveryService");
    this.failureLogger = Objects.requireNonNull(failureLogger, "failureLogger");
  }

  /** Starts asynchronous delivery after the join event reaches the main thread. */
  @EventHandler
  public void onPlayerJoin(PlayerJoinEvent event) {
    deliveryService
        .deliver(event.getPlayer())
        .whenComplete(
            (ignored, failure) -> {
              if (failure != null) {
                failureLogger.accept(failure);
              }
            });
  }
}

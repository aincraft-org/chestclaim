package org.aincraft.chestclaim.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.aincraft.chestclaim.claim.PendingClaim;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClaimRepositoryContractTest {

  @TempDir Path temporaryDirectory;

  private ClaimRepository repository;

  @AfterEach
  void closeRepository() {
    if (repository != null) {
      repository.close();
    }
  }

  @Test
  void claimedRowCanOnlyBeCompletedByItsDeliveryToken() throws Exception {
    repository = openRepository();
    UUID playerId = UUID.randomUUID();
    UUID deliveryToken = UUID.randomUUID();
    repository.enqueue(playerId, new byte[] {1}, "shop");

    List<PendingClaim> claims = repository.claimPending(playerId, deliveryToken);

    assertEquals(1, claims.size());
    assertFalse(repository.complete(claims.get(0).id(), UUID.randomUUID()));
    assertTrue(repository.complete(claims.get(0).id(), deliveryToken));
    assertTrue(repository.claimPending(playerId, UUID.randomUUID()).isEmpty());
  }

  @Test
  void retainRequeuesAllLeftoverSerializedItems() throws Exception {
    repository = openRepository();
    UUID playerId = UUID.randomUUID();
    UUID firstToken = UUID.randomUUID();
    repository.enqueue(playerId, new byte[] {1}, "holiday");
    PendingClaim claim = repository.claimPending(playerId, firstToken).get(0);

    assertTrue(repository.retain(claim, firstToken, List.of(new byte[] {2}, new byte[] {3})));

    List<PendingClaim> retained = repository.claimPending(playerId, UUID.randomUUID());
    assertEquals(2, retained.size());
    assertEquals(List.of(2, 3), retained.stream().map(item -> (int) item.itemBytes()[0]).toList());
  }

  @Test
  void releaseMakesClaimAvailableAfterDeliveryFailure() throws Exception {
    repository = openRepository();
    UUID playerId = UUID.randomUUID();
    repository.enqueue(playerId, new byte[] {7}, "return");
    UUID firstToken = UUID.randomUUID();
    PendingClaim claim = repository.claimPending(playerId, firstToken).get(0);

    assertTrue(repository.release(claim.id(), firstToken));
    assertEquals(1, repository.claimPending(playerId, UUID.randomUUID()).size());
  }

  @Test
  void pendingRowsSurviveRepositoryReopen() throws Exception {
    UUID playerId = UUID.randomUUID();
    repository = openRepository();
    repository.enqueue(playerId, new byte[] {9, 8}, "craft");
    repository.close();

    repository = openRepository();
    PendingClaim claim = repository.claimPending(playerId, UUID.randomUUID()).get(0);

    assertArrayEquals(new byte[] {9, 8}, claim.itemBytes());
    assertEquals("craft", claim.source());
  }

  private ClaimRepository openRepository() throws Exception {
    ClaimRepository result = new SqliteClaimRepository(temporaryDirectory.resolve("claim.db"));
    result.initialize();
    return result;
  }
}

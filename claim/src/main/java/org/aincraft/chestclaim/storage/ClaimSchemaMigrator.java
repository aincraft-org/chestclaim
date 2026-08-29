package org.aincraft.chestclaim.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.jdbi.v3.core.Handle;

/** Applies idempotent consumer-owned schema migrations for the claim queue. */
final class ClaimSchemaMigrator {

  private static final String INITIAL_MIGRATION = "/sql/migration/V1__initial.sql";

  private ClaimSchemaMigrator() {}

  static void migrate(Handle handle) {
    Objects.requireNonNull(handle, "handle");
    try (InputStream input = ClaimSchemaMigrator.class.getResourceAsStream(INITIAL_MIGRATION)) {
      if (input == null) {
        throw new IllegalStateException("Missing claim schema migration resource");
      }
      String script = new String(input.readAllBytes(), StandardCharsets.UTF_8);
      for (String statement : script.split(";")) {
        if (!statement.isBlank()) {
          handle.execute(statement);
        }
      }
    } catch (IOException exception) {
      throw new IllegalStateException("Could not read claim schema migration", exception);
    }
  }
}

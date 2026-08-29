package org.aincraft.chestclaim.protection;

/** Thrown when a protected block has no one definitive owner. */
public class ProtectionOwnerResolutionException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /** Creates an ownership resolution failure. */
  public ProtectionOwnerResolutionException(String message) {
    super(message);
  }

  /** Creates an ownership resolution failure with its cause. */
  public ProtectionOwnerResolutionException(String message, Throwable cause) {
    super(message, cause);
  }
}

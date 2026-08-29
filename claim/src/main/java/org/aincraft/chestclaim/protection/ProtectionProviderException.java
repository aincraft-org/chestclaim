package org.aincraft.chestclaim.protection;

/** A provider could not answer a protection ownership lookup. */
public class ProtectionProviderException extends Exception {

  private static final long serialVersionUID = 1L;

  /** Creates a provider failure. */
  public ProtectionProviderException(String message) {
    super(message);
  }

  /** Creates a provider failure with its cause. */
  public ProtectionProviderException(String message, Throwable cause) {
    super(message, cause);
  }
}

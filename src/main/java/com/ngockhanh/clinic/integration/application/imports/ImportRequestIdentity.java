package com.ngockhanh.clinic.integration.application.imports;

import java.util.UUID;

/**
 * Identity of one import request for idempotency: who sent it, for which agreement, under which
 * client key, and the fingerprint of everything that defines the request.
 *
 * @param requestHash SHA-256 fingerprint, exactly 32 bytes; copied on construction and on read
 */
public record ImportRequestIdentity(
    UUID organizationId, UUID agreementId, UUID actorId, UUID requestKey, byte[] requestHash) {
  public ImportRequestIdentity {
    if (organizationId == null
        || agreementId == null
        || actorId == null
        || requestKey == null
        || requestHash == null
        || requestHash.length != 32)
      throw new IllegalArgumentException("Invalid import request identity");
    requestHash = requestHash.clone();
  }

  @Override
  public byte[] requestHash() {
    return requestHash.clone();
  }
}

package com.ngockhanh.clinic.integration.application.query;

import java.util.UUID;

/**
 * Published read contract of the integration module that tells whether a health examination
 * agreement is referenced by integration history.
 *
 * <p>It only protects history; it does not expose import jobs, rows or staging data.
 */
public interface AgreementHistoryQuery {
  /**
   * Whether any import job references the agreement, in any status.
   *
   * @param agreementId health examination agreement identifier
   */
  boolean hasAgreementReferences(UUID agreementId);
}

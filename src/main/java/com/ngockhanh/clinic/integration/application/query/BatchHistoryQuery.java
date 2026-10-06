package com.ngockhanh.clinic.integration.application.query;

import java.util.UUID;

/**
 * Published read contract of the integration module that tells whether a health examination batch
 * is referenced by integration history.
 *
 * <p>It only protects history; it does not expose import jobs, rows or staging data.
 */
public interface BatchHistoryQuery {
  /**
   * Whether any import job references the batch, in any status.
   *
   * @param batchId health examination batch identifier
   */
  boolean hasBatchReferences(UUID batchId);
}

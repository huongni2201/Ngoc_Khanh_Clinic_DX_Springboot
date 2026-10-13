package com.ngockhanh.clinic.accesscontrol.application.response;

import java.util.List;

/**
 * Bounded list of account owner candidates.
 *
 * @param items at most 20 candidates ordered by full name
 * @param limited true when more profiles may match and the keyword should be refined
 */
public record AccountOwnerCandidateListResponse(
    List<AccountOwnerCandidateResponse> items, Boolean limited) {
  public AccountOwnerCandidateListResponse {
    items = items == null ? List.of() : List.copyOf(items);
  }
}

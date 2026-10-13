package com.ngockhanh.clinic.accesscontrol.application.response;

import java.util.List;

/**
 * Bounded list of eligible owners.
 *
 * @param items at most 20 eligible owners ordered by full name
 * @param limited true when more profiles may match and the keyword should be refined
 */
public record EligibleOwnerListResponse(List<EligibleOwnerResponse> items, Boolean limited) {
  public EligibleOwnerListResponse {
    items = items == null ? List.of() : List.copyOf(items);
  }
}

package com.ngockhanh.clinic.healthexamination.api.request;

import com.ngockhanh.clinic.healthexamination.application.query.AssignmentListQuery;
import com.ngockhanh.clinic.shared.validation.AllowedSortKeys;
import com.ngockhanh.clinic.shared.web.BasePagination;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/** Query parameters of the Participant list of a session. */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@AllowedSortKeys({"id", "fullName", "participantCode", "departmentName", "status"})
public class AssignmentListRequest extends BasePagination {
  @Size(max = 20)
  private String status;

  /** Maps the validated HTTP input to the application query. */
  public AssignmentListQuery toQuery() {
    return AssignmentListQuery.builder()
        .page(getPage())
        .size(getSize())
        .searchKey(getSearchKey())
        .status(status)
        .sortKey(getSortKey())
        .sortBy(getSortBy())
        .build();
  }
}

package com.ngockhanh.clinic.healthexamination.api.request;

import com.ngockhanh.clinic.healthexamination.application.query.ExaminationDetailListQuery;
import com.ngockhanh.clinic.shared.validation.AllowedSortKeys;
import com.ngockhanh.clinic.shared.web.BasePagination;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Query parameters of the examination detail list. Structural bounds live here; the allowed values
 * of the status filters are checked by the use case so every caller gets the same rules.
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@AllowedSortKeys({"id", "participantCode", "fullName", "examinationDate"})
public class ExaminationDetailListRequest extends BasePagination {
  @Size(max = 20)
  private String rosterStatus;

  @Size(max = 20)
  private String attendanceStatus;

  @Size(max = 20)
  private String reconciliationStatus;

  /** Maps the validated HTTP input to the application query. */
  public ExaminationDetailListQuery toQuery() {
    return ExaminationDetailListQuery.builder()
        .page(getPage())
        .size(getSize())
        .searchKey(getSearchKey())
        .rosterStatus(rosterStatus)
        .attendanceStatus(attendanceStatus)
        .reconciliationStatus(reconciliationStatus)
        .sortKey(getSortKey())
        .sortBy(getSortBy())
        .build();
  }
}

package com.ngockhanh.clinic.healthexamination.api.request;

import com.ngockhanh.clinic.healthexamination.application.query.ParticipantListQuery;
import com.ngockhanh.clinic.shared.validation.AllowedSortKeys;
import com.ngockhanh.clinic.shared.web.BasePagination;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Query parameters of the Participant list. Structural bounds live here; the allowed values of the
 * status filters are checked by the use case so every caller gets the same rules.
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@AllowedSortKeys({"id", "fullName", "participantCode", "examinationDate", "createdAt"})
public class ParticipantListRequest extends BasePagination {
  @Size(max = 20, message = "IdentificationNumber must not exceed 20 characters")
  private String identificationNumber;

  private UUID batchDayId;

  @Size(max = 20)
  private String rosterStatus;

  @Size(max = 20)
  private String attendanceStatus;

  @Size(max = 20)
  private String reconciliationStatus;

  /** Maps the validated HTTP input to the application query. */
  public ParticipantListQuery toQuery() {
    return ParticipantListQuery.builder()
        .page(getPage())
        .size(getSize())
        .searchKey(getSearchKey())
        .identificationNumber(identificationNumber)
        .batchDayId(batchDayId)
        .rosterStatus(rosterStatus)
        .attendanceStatus(attendanceStatus)
        .reconciliationStatus(reconciliationStatus)
        .sortKey(getSortKey())
        .sortBy(getSortBy())
        .build();
  }
}

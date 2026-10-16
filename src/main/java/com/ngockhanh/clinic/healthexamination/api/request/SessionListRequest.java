package com.ngockhanh.clinic.healthexamination.api.request;

import com.ngockhanh.clinic.healthexamination.application.query.SessionListQuery;
import com.ngockhanh.clinic.shared.validation.AllowedSortKeys;
import com.ngockhanh.clinic.shared.web.BasePagination;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Query parameters of the session list. Structural bounds live here; the allowed values of the
 * filters are checked by the use case so every caller gets the same rules.
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@AllowedSortKeys({"id", "sessionDate", "activity", "status", "createdAt"})
public class SessionListRequest extends BasePagination {
  private UUID agreementId;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
  private LocalDate from;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
  private LocalDate to;

  @Size(max = 20)
  private String activity;

  @Size(max = 20)
  private String status;

  /** Maps the validated HTTP input to the application query. */
  public SessionListQuery toQuery() {
    return SessionListQuery.builder()
        .page(getPage())
        .size(getSize())
        .searchKey(getSearchKey())
        .agreementId(agreementId)
        .from(from)
        .to(to)
        .activity(activity)
        .status(status)
        .sortKey(getSortKey())
        .sortBy(getSortBy())
        .build();
  }
}

package com.ngockhanh.clinic.healthexamination.api.request;

import com.ngockhanh.clinic.shared.validation.AllowedSortKeys;
import com.ngockhanh.clinic.shared.web.BasePagination;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@AllowedSortKeys({"id", "name", "taxCode", "contactName", "contactPhone", "status", "createdAt"})
public class OrganizationListRequest extends BasePagination {

	@Pattern(
      regexp = "(?i)^(ACTIVE|INACTIVE)$",
      message = "Status must be ACTIVE or INACTIVE"
  )
	private String status;
}

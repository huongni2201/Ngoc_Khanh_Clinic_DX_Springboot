package com.ngockhanh.clinic.healthexamination.api.request;

import com.ngockhanh.clinic.shared.validation.AllowedSortKeys;
import com.ngockhanh.clinic.shared.web.BasePagination;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/** Query parameters for listing organizations. */
@NoArgsConstructor
@SuperBuilder
@AllowedSortKeys({"id", "taxCode", "name"})
public class ListOrganizationRequest extends BasePagination {}

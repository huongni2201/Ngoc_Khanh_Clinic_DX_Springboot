package com.ngockhanh.clinic.accesscontrol.api.request;

import com.ngockhanh.clinic.shared.validation.AllowedSortKeys;
import com.ngockhanh.clinic.shared.web.BasePagination;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/** Query parameters for listing staff members. */
@NoArgsConstructor
@SuperBuilder
@AllowedSortKeys({"id", "staffCode", "fullName"})
public class ListStaffMemberRequest extends BasePagination {}

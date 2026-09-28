package com.ngockhanh.clinic.healthcheck.api.request;

import com.ngockhanh.clinic.shared.validation.AllowedSortTypes;
import com.ngockhanh.clinic.shared.web.BasePagination;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@AllowedSortTypes({
    "id",
    "employeeId",
    "employeeCode",
    "fullName",
    "departmentName",
    "jobTitle",
    "occupation",
    "status",
    "createdAt"
})
public class EmployeeRequest extends BasePagination {

}

package com.ngockhanh.clinic.healthexamination.api.request;

import com.ngockhanh.clinic.shared.validation.AllowedSortKeys;
import com.ngockhanh.clinic.shared.web.BasePagination;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@AllowedSortKeys({
    "id",
    "participantId",
    "participantCode",
    "fullName",
    "departmentName",
    "jobTitle",
    "occupation",
    "status",
    "createdAt"
})
public class OrganizationBatchParticipantRequest extends BasePagination {

}


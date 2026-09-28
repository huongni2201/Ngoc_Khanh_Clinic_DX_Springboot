package com.ngockhanh.clinic.healthcheck.infrastructure.persistence.converter;

import com.ngockhanh.clinic.healthcheck.api.response.AdministrativeSnapshotResponse;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.AdministrativeSnapshot;

public final class AdministrativeSnapshotResponseConverter {
    private AdministrativeSnapshotResponseConverter() {}

    public static AdministrativeSnapshotResponse from(AdministrativeSnapshot snapshot) {
        return new AdministrativeSnapshotResponse(snapshot.fullName(), snapshot.dateOfBirth(), snapshot.sex(),
                new AdministrativeSnapshotResponse.IdentificationNumberResponse(snapshot.identificationNumber().value()),
                snapshot.identificationNumberIssueDate(), snapshot.identificationNumberIssuePlace(),
                snapshot.ethnicity(), snapshot.subjectType(), snapshot.payerSource(), snapshot.bloodGroup(),
                snapshot.phone(), snapshot.province(), snapshot.ward(), snapshot.addressDetail(), snapshot.occupation(),
                snapshot.workplaceOrSchool(), snapshot.healthExaminationReason());
    }
}

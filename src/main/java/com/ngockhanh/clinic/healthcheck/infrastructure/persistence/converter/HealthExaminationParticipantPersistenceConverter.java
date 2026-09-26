package com.ngockhanh.clinic.healthcheck.infrastructure.persistence.converter;

import com.ngockhanh.clinic.healthcheck.domain.aggregate.HealthExaminationParticipant;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.IdentificationNumber;
import com.ngockhanh.clinic.healthcheck.infrastructure.persistence.record.HealthExaminationParticipantRecord;

public final class HealthExaminationParticipantPersistenceConverter {
    public HealthExaminationParticipant toDomain(HealthExaminationParticipantRecord record) {
        if (record == null) return null;
        return HealthExaminationParticipant.restore(record.id(), record.organizationId(), record.participantCode(),
                IdentificationNumber.of(record.identificationNumber()), record.fullName(), record.dateOfBirth(),
                record.sex(), record.departmentName(), record.jobTitle(), record.occupation(), record.status(),
                record.patientId());
    }

    public HealthExaminationParticipantRecord toRecord(HealthExaminationParticipant participant) {
        return new HealthExaminationParticipantRecord(participant.id(), participant.organizationId(), participant.patientId(),
                participant.participantCode(), participant.identificationNumber().value(), participant.fullName(),
                participant.dateOfBirth(), participant.sex(), participant.departmentName(), participant.jobTitle(),
                participant.occupation(), participant.status(), null, null);
    }
}

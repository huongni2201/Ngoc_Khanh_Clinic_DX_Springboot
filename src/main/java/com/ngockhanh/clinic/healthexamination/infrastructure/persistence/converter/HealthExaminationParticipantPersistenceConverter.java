package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.converter;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationParticipant;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.IdentificationNumber;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record.HealthExaminationParticipantRecord;

public final class HealthExaminationParticipantPersistenceConverter {
    public HealthExaminationParticipant toDomain(HealthExaminationParticipantRecord record) {
        if (record == null) return null;
        return HealthExaminationParticipant.restore(new AggregateId(record.id()),
                new AggregateId(record.organizationId()), record.participantCode(),
                IdentificationNumber.of(record.identificationNumber()), record.fullName(), record.dateOfBirth(),
                record.sex(), record.departmentName(), record.jobTitle(), record.occupation(), record.status(),
                record.patientId() == null ? null : new AggregateId(record.patientId()));
    }

    public HealthExaminationParticipantRecord toRecord(HealthExaminationParticipant participant) {
        return new HealthExaminationParticipantRecord(participant.id().value(), participant.organizationId().value(),
                participant.patientId() == null ? null : participant.patientId().value(),
                participant.participantCode(), participant.identificationNumber().value(), participant.fullName(),
                participant.dateOfBirth(), participant.sex(), participant.departmentName(), participant.jobTitle(),
                participant.occupation(), participant.status(), null, null);
    }
}

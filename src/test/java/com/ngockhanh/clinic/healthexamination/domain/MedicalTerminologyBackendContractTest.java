package com.ngockhanh.clinic.healthexamination.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.Organization;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationParticipant;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.IdentificationNumber;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;

class MedicalTerminologyBackendContractTest {
    @Test
    void organizationAndParticipantExposeCanonicalIdentityNames() {
        AggregateId organizationId = new AggregateId(UUID.randomUUID());
        Organization organization = Organization.create(
                organizationId, "Ngoc Khanh Clinic", "Contact", "0900000000");
        HealthExaminationParticipant participant = HealthExaminationParticipant.create(
                new AggregateId(UUID.randomUUID()), organization.id(), "PART-01", IdentificationNumber.of("012345678901"),
                "Nguyen A", java.time.LocalDate.of(1990, 1, 1), "MALE");

        assertThat(organization.name()).isEqualTo("Ngoc Khanh Clinic");
        assertThat(participant.organizationId()).isEqualTo(organizationId);
        assertThat(participant.participantCode()).isEqualTo("PART-01");
    }
}

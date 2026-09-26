package com.ngockhanh.clinic.healthcheck.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ngockhanh.clinic.healthcheck.domain.aggregate.HealthExaminationParticipant;
import com.ngockhanh.clinic.healthcheck.domain.aggregate.Organization;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.IdentificationNumber;

class MedicalTerminologyBackendContractTest {
    @Test
    void organizationAndParticipantExposeCanonicalIdentityNames() {
        Organization organization = Organization.create(
                UUID.randomUUID(), "ORG-01", "Ngoc Khanh Clinic", "Contact", "0900000000");
        HealthExaminationParticipant participant = HealthExaminationParticipant.create(
                UUID.randomUUID(), organization.id(), "PART-01", IdentificationNumber.of("012345678901"),
                "Nguyen A", java.time.LocalDate.of(1990, 1, 1), "MALE");

        assertThat(organization.name()).isEqualTo("Ngoc Khanh Clinic");
        assertThat(participant.organizationId()).isEqualTo(organization.id());
        assertThat(participant.participantCode()).isEqualTo("PART-01");
    }
}

package com.ngockhanh.clinic.healthcheck.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ngockhanh.clinic.healthcheck.application.importparticipant.ParticipantImportBatchContext;
import com.ngockhanh.clinic.healthcheck.application.port.ParticipantImportBatchQuery;
import com.ngockhanh.clinic.healthcheck.domain.aggregate.HealthExaminationImportJob;
import com.ngockhanh.clinic.healthcheck.domain.entity.HealthExaminationImportRow;
import com.ngockhanh.clinic.healthcheck.domain.enums.ImportStatus;
import com.ngockhanh.clinic.healthcheck.domain.enums.ImportType;
import com.ngockhanh.clinic.healthcheck.domain.repository.HealthExaminationBatchParticipantRepository;
import com.ngockhanh.clinic.healthcheck.domain.repository.HealthExaminationImportJobRepository;
import com.ngockhanh.clinic.healthcheck.domain.repository.HealthExaminationParticipantRepository;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.AdministrativeSnapshot;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.IdentificationNumber;
import com.ngockhanh.clinic.shared.infrastructure.id.IdGenerator;

@ExtendWith(MockitoExtension.class)
class ConfirmParticipantImportUseCaseTest {
    @Mock ParticipantImportBatchQuery batchQuery;
    @Mock HealthExaminationImportJobRepository importJobRepository;
    @Mock HealthExaminationParticipantRepository participantRepository;
    @Mock HealthExaminationBatchParticipantRepository batchParticipantRepository;

    @Test
    void confirmsValidRowsAndKeepsInvalidRowsAsPartial() {
        UUID organizationId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        UUID importId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        var snapshot = new AdministrativeSnapshot("NGUYỄN VĂN A", LocalDate.of(1990, 1, 1), "MALE",
                IdentificationNumber.of("001234567890"));
        var job = HealthExaminationImportJob.create(importId, batchId, ImportType.PARTICIPANT_LIST,
                UUID.randomUUID(), actorId, Instant.now());
        job.addRow(HealthExaminationImportRow.roster(UUID.randomUUID(), 2, "E-01", snapshot, "Phòng A", "Bác sĩ", null));
        job.addRow(HealthExaminationImportRow.invalid(UUID.randomUUID(), 3, List.of("REQUIRED_FIELD"),
                null, null, null, null, null));
        job.validate();
        when(batchQuery.findById(batchId)).thenReturn(Optional.of(
                new ParticipantImportBatchContext(batchId, organizationId, LocalDate.of(2026, 9, 26))));
        when(importJobRepository.findByIdForUpdate(importId)).thenReturn(Optional.of(job));
        when(participantRepository.findByOrganizationAndCodes(organizationId, List.of("E-01"))).thenReturn(List.of());
        when(participantRepository.findByOrganizationAndIdentificationNumbers(organizationId,
                List.of(snapshot.identificationNumber()))).thenReturn(List.of());
        when(batchParticipantRepository.findParticipantIdsByBatch(eq(batchId), any())).thenReturn(Set.of());
        ConfirmParticipantImportUseCase useCase = new ConfirmParticipantImportUseCase(batchQuery,
                importJobRepository, participantRepository, batchParticipantRepository, (IdGenerator) UUID::randomUUID);

        var result = useCase.execute(organizationId, batchId, importId, actorId);

        assertThat(result.status()).isEqualTo(ImportStatus.PARTIAL);
        assertThat(result.validRows()).isEqualTo(1);
        assertThat(result.errorRows()).isEqualTo(1);
        assertThat(job.confirmedByUserId()).isEqualTo(actorId);
        assertThat(job.rows().getFirst().resolvedParticipantId()).isNotNull();
        verify(participantRepository).save(any());
        verify(batchParticipantRepository).save(any());
        verify(importJobRepository).save(job);
    }
}

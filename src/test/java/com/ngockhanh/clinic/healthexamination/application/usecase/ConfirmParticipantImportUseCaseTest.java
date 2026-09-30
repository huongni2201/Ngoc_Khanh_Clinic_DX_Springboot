package com.ngockhanh.clinic.healthexamination.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.ngockhanh.clinic.healthexamination.application.response.ParticipantImportConfirmResponse;
import com.ngockhanh.clinic.healthexamination.application.port.out.ParticipantImportAuditWriter;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationImportJob;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationParticipant;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationImportRow;
import com.ngockhanh.clinic.healthexamination.domain.enums.BatchStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.ImportRowAction;
import com.ngockhanh.clinic.healthexamination.domain.enums.ImportType;
import com.ngockhanh.clinic.healthexamination.domain.enums.ParticipantImportField;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchParticipantRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchParticipantRepository.BatchParticipantRosterSnapshot;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository.HealthExaminationBatchReference;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationImportJobRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationParticipantRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.IdentificationNumber;
import com.ngockhanh.clinic.shared.exception.BusinessRuleException;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;

class ConfirmParticipantImportUseCaseTest {
    private static final UUID ORGANIZATION_ID = id(1);
    private static final UUID BATCH_ID = id(2);
    private static final UUID JOB_ID = id(3);
    private static final UUID ACTOR_ID = id(4);

    @Test
    void oneInvalidRowPreventsEveryBusinessWrite() {
        Fixture fixture = new Fixture();
        fixture.job = validatedJob(
                row(5, "012345678901", "Nguyen An"),
                HealthExaminationImportRow.invalid(new AggregateId(id(6)), 6, "MISSING_IDENTIFICATION_NUMBER"));
        fixture.stubJob();

        assertThatThrownBy(() -> fixture.useCase.execute(ORGANIZATION_ID, BATCH_ID, JOB_ID, ACTOR_ID))
                .isInstanceOf(BusinessRuleException.class);

        verify(fixture.participants, never()).saveAll(any());
        verify(fixture.batchParticipants, never()).insertRosterSnapshots(any(), any());
        verify(fixture.batchParticipants, never()).updateRosterSnapshots(any(), any());
        verify(fixture.jobs, never()).save(any());
        verify(fixture.auditWriter, never()).record(any());
    }

    @Test
    void createsRosterRowsAndReturnsSavedCountsOnRetry() {
        Fixture fixture = new Fixture();
        fixture.job = validatedJob(row(5, "012345678901", "Nguyen An", ImportRowAction.CREATE));
        fixture.stubJob();
        when(fixture.participants.findByOrganizationAndIdentificationNumbersForUpdate(any(), any()))
                .thenReturn(List.of());
        when(fixture.batchParticipants.findRosterSnapshotsForUpdate(any(), any())).thenReturn(List.of());
        when(fixture.batchParticipants.findBatchParticipantIdsWithHealthRecords(any())).thenReturn(Set.of());

        ParticipantImportConfirmResponse first = fixture.useCase.execute(ORGANIZATION_ID, BATCH_ID, JOB_ID, ACTOR_ID);
        ParticipantImportConfirmResponse retry = fixture.useCase.execute(ORGANIZATION_ID, BATCH_ID, JOB_ID, ACTOR_ID);

        assertThat(first).isEqualTo(new ParticipantImportConfirmResponse(JOB_ID, "CONFIRMED", 1, 1, 0, 0));
        assertThat(retry).isEqualTo(first);
        assertThat(fixture.job.rows().getFirst().getAppliedAction()).isEqualTo(ImportRowAction.CREATE);
        assertThat(fixture.job.rows().getFirst().getResolvedParticipantId()).isNotNull();
        verify(fixture.participants).saveAll(any());
        verify(fixture.batchParticipants).insertRosterSnapshots(eq(AggregateId.of(BATCH_ID)), any());
        verify(fixture.batchParticipants, never()).updateRosterSnapshots(any(), any());
        verify(fixture.jobs).save(fixture.job);
        verify(fixture.auditWriter).record(any());
    }

    @Test
    void updatesExistingParticipantWithoutChangingPatientOrBatchMembershipIds() {
        Fixture fixture = new Fixture();
        HealthExaminationImportRow row = row(5, "012345678901", "Nguyen Thi An", ImportRowAction.UPDATE);
        fixture.job = validatedJob(row);
        fixture.stubJob();

        UUID participantId = id(20);
        UUID patientId = id(21);
        UUID batchParticipantId = id(22);
        HealthExaminationParticipant participant = HealthExaminationParticipant.restore(
                AggregateId.of(participantId), AggregateId.of(ORGANIZATION_ID), "internal-code-20",
                IdentificationNumber.of("012345678901"), "Nguyen An", LocalDate.of(1990, 1, 1), "Nam",
                null, null, "Kế toán", "ACTIVE", AggregateId.of(patientId));
        when(fixture.participants.findByOrganizationAndIdentificationNumbersForUpdate(any(), any()))
                .thenReturn(List.of(participant));
        BatchParticipantRosterSnapshot snapshot = snapshot(participantId, batchParticipantId,
                "Nguyen An", LocalDate.of(1990, 1, 1), "Kế toán");
        when(fixture.batchParticipants.findRosterSnapshotsForUpdate(AggregateId.of(BATCH_ID),
                List.of(AggregateId.of(participantId)))).thenReturn(List.of(snapshot));
        when(fixture.batchParticipants.findBatchParticipantIdsWithHealthRecords(List.of(AggregateId.of(batchParticipantId))))
                .thenReturn(Set.of(AggregateId.of(batchParticipantId)));
        row.setPreviewFingerprint(ValidateParticipantImportUseCase.previewFingerprint(participant, snapshot, true,
                LocalDate.of(2026, 10, 1)));

        ParticipantImportConfirmResponse response = fixture.useCase.execute(ORGANIZATION_ID, BATCH_ID, JOB_ID, ACTOR_ID);

        assertThat(response.updatedRows()).isEqualTo(1);
        assertThat(row.getResolvedParticipantId()).isEqualTo(AggregateId.of(participantId));
        assertThat(row.getResolvedBatchParticipantId()).isEqualTo(AggregateId.of(batchParticipantId));
        assertThat(row.getWarningCodes()).contains("HEALTH_RECORD_SNAPSHOT_UNCHANGED");
        verify(fixture.participants).saveAll(org.mockito.ArgumentMatchers.argThat(items -> items.stream()
                .anyMatch(item -> item.patientId().equals(AggregateId.of(patientId))
                        && item.fullName().equals("Nguyen Thi An"))));
        verify(fixture.batchParticipants).updateRosterSnapshots(eq(AggregateId.of(BATCH_ID)),
                org.mockito.ArgumentMatchers.argThat(items -> items.stream()
                        .anyMatch(item -> item.batchParticipantId().equals(AggregateId.of(batchParticipantId)))));
    }

    @Test
    void returnsStalePreviewConflictBeforeWritingWhenTheBatchMembershipAppearedAfterPreview() {
        Fixture fixture = new Fixture();
        HealthExaminationImportRow row = row(5, "012345678901", "Nguyen An", ImportRowAction.CREATE);
        fixture.job = validatedJob(row);
        fixture.stubJob();
        UUID participantId = id(20);
        UUID batchParticipantId = id(22);
        HealthExaminationParticipant participant = HealthExaminationParticipant.create(
                AggregateId.of(participantId), AggregateId.of(ORGANIZATION_ID), "internal-code-20",
                IdentificationNumber.of("012345678901"), "Nguyen An", LocalDate.of(1990, 1, 1), "Nam");
        when(fixture.participants.findByOrganizationAndIdentificationNumbersForUpdate(any(), any()))
                .thenReturn(List.of(participant));
        when(fixture.batchParticipants.findRosterSnapshotsForUpdate(AggregateId.of(BATCH_ID),
                List.of(AggregateId.of(participantId)))).thenReturn(List.of(snapshot(participantId,
                batchParticipantId, "Nguyen An", LocalDate.of(1990, 1, 1), null)));
        when(fixture.batchParticipants.findBatchParticipantIdsWithHealthRecords(List.of(AggregateId.of(batchParticipantId))))
                .thenReturn(Set.of());

        assertThatThrownBy(() -> fixture.useCase.execute(ORGANIZATION_ID, BATCH_ID, JOB_ID, ACTOR_ID))
                .isInstanceOfSatisfying(ConcurrentUpdateException.class, error ->
                        assertThat(error.errorCode()).isEqualTo("IMPORT_PREVIEW_STALE"));

        verify(fixture.participants, never()).saveAll(any());
        verify(fixture.batchParticipants, never()).insertRosterSnapshots(any(), any());
        verify(fixture.batchParticipants, never()).updateRosterSnapshots(any(), any());
        verify(fixture.jobs, never()).save(any());
        verify(fixture.auditWriter, never()).record(any());
    }

    @Test
    void unchangedExistingRosterSkipsParticipantAndMembershipWrites() {
        Fixture fixture = new Fixture();
        HealthExaminationImportRow row = row(5, "012345678901", "Nguyen An", ImportRowAction.UNCHANGED);
        fixture.job = validatedJob(row);
        fixture.stubJob();
        UUID participantId = id(20);
        UUID batchParticipantId = id(22);
        HealthExaminationParticipant participant = HealthExaminationParticipant.create(
                AggregateId.of(participantId), AggregateId.of(ORGANIZATION_ID), "internal-code-20",
                IdentificationNumber.of("012345678901"), "Nguyen An", LocalDate.of(1990, 1, 1), "Nam");
        when(fixture.participants.findByOrganizationAndIdentificationNumbersForUpdate(any(), any()))
                .thenReturn(List.of(participant));
        when(fixture.batchParticipants.findRosterSnapshotsForUpdate(AggregateId.of(BATCH_ID),
                List.of(AggregateId.of(participantId)))).thenReturn(List.of(snapshot(participantId,
                batchParticipantId, "Nguyen An", LocalDate.of(1990, 1, 1), null)));
        when(fixture.batchParticipants.findBatchParticipantIdsWithHealthRecords(List.of(AggregateId.of(batchParticipantId))))
                .thenReturn(Set.of());
        row.setPreviewFingerprint(ValidateParticipantImportUseCase.previewFingerprint(participant,
                snapshot(participantId, batchParticipantId, "Nguyen An", LocalDate.of(1990, 1, 1), null), false,
                LocalDate.of(2026, 10, 1)));

        ParticipantImportConfirmResponse response = fixture.useCase.execute(ORGANIZATION_ID, BATCH_ID, JOB_ID, ACTOR_ID);

        assertThat(response.unchangedRows()).isEqualTo(1);
        verify(fixture.participants, never()).saveAll(any());
        verify(fixture.batchParticipants, never()).insertRosterSnapshots(any(), any());
        verify(fixture.batchParticipants, never()).updateRosterSnapshots(any(), any());
    }

    @Test
    void newMembershipUsesOnlyImportedOptionalValues() {
        Fixture fixture = new Fixture();
        fixture.job = validatedJob(row(5, "012345678901", "Nguyen An", ImportRowAction.CREATE));
        fixture.stubJob();
        HealthExaminationParticipant participant = HealthExaminationParticipant.create(
                AggregateId.of(id(20)), AggregateId.of(ORGANIZATION_ID), "internal-code-20",
                IdentificationNumber.of("012345678901"), "Nguyen An", LocalDate.of(1990, 1, 1), "Nam",
                "Finance", "Accountant", "Engineer");
        when(fixture.participants.findByOrganizationAndIdentificationNumbersForUpdate(any(), any()))
                .thenReturn(List.of(participant));
        fixture.job.rows().getFirst().setPreviewFingerprint(
                ValidateParticipantImportUseCase.previewFingerprint(participant, null, false, LocalDate.of(2026, 10, 1)));

        ParticipantImportConfirmResponse response = fixture.useCase.execute(ORGANIZATION_ID, BATCH_ID, JOB_ID, ACTOR_ID);

        assertThat(response.createdRows()).isEqualTo(1);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BatchParticipantRosterSnapshot>> snapshots = ArgumentCaptor.forClass(List.class);
        verify(fixture.batchParticipants).insertRosterSnapshots(eq(AggregateId.of(BATCH_ID)), snapshots.capture());
        assertThat(snapshots.getValue()).singleElement().satisfies(created -> {
            assertThat(created.participantId()).isEqualTo(participant.id());
            assertThat(created.departmentName()).isNull();
            assertThat(created.jobTitle()).isNull();
            assertThat(created.occupation()).isNull();
            assertThat(created.phone()).isEqualTo("0900000000");
        });
        verify(fixture.participants, never()).saveAll(any());
    }

    @Test
    void blankOccupationPreservesDifferentParticipantAndBatchValues() {
        Fixture fixture = new Fixture();
        fixture.job = validatedJob(row(5, "012345678901", "Nguyen An", ImportRowAction.UNCHANGED));
        fixture.stubJob();
        HealthExaminationParticipant participant = HealthExaminationParticipant.create(
                AggregateId.of(id(20)), AggregateId.of(ORGANIZATION_ID), "internal-code-20",
                IdentificationNumber.of("012345678901"), "Nguyen An", LocalDate.of(1990, 1, 1), "Nam",
                null, null, "Engineer");
        when(fixture.participants.findByOrganizationAndIdentificationNumbersForUpdate(any(), any()))
                .thenReturn(List.of(participant));
        when(fixture.batchParticipants.findRosterSnapshotsForUpdate(any(), any()))
                .thenReturn(List.of(snapshot(id(20), id(22), "Nguyen An", LocalDate.of(1990, 1, 1), "Accountant")));
        fixture.job.rows().getFirst().setPreviewFingerprint(ValidateParticipantImportUseCase.previewFingerprint(participant,
                snapshot(id(20), id(22), "Nguyen An", LocalDate.of(1990, 1, 1), "Accountant"), false,
                LocalDate.of(2026, 10, 1)));

        ParticipantImportConfirmResponse response = fixture.useCase.execute(ORGANIZATION_ID, BATCH_ID, JOB_ID, ACTOR_ID);

        assertThat(response.unchangedRows()).isEqualTo(1);
        verify(fixture.participants, never()).saveAll(any());
        verify(fixture.batchParticipants, never()).insertRosterSnapshots(any(), any());
        verify(fixture.batchParticipants, never()).updateRosterSnapshots(any(), any());
    }

    @Test
    void staleUpdatePreviewCannotOverwriteNewerRosterValues() {
        Fixture fixture = new Fixture();
        fixture.job = validatedJob(row(5, "012345678901", "Import A", ImportRowAction.UPDATE));
        HealthExaminationParticipant reviewed = HealthExaminationParticipant.create(
                AggregateId.of(id(20)), AggregateId.of(ORGANIZATION_ID), "internal-code-20",
                IdentificationNumber.of("012345678901"), "Reviewed Name", LocalDate.of(1990, 1, 1), "Nam");
        fixture.job.rows().getFirst().setPreviewFingerprint(ValidateParticipantImportUseCase.previewFingerprint(reviewed,
                snapshot(id(20), id(22), "Reviewed Name", LocalDate.of(1990, 1, 1), null), false,
                LocalDate.of(2026, 10, 1)));
        fixture.stubJob();
        HealthExaminationParticipant current = HealthExaminationParticipant.create(
                AggregateId.of(id(20)), AggregateId.of(ORGANIZATION_ID), "internal-code-20",
                IdentificationNumber.of("012345678901"), "Import B", LocalDate.of(1990, 1, 1), "Nam");
        when(fixture.participants.findByOrganizationAndIdentificationNumbersForUpdate(any(), any()))
                .thenReturn(List.of(current));
        when(fixture.batchParticipants.findRosterSnapshotsForUpdate(any(), any()))
                .thenReturn(List.of(snapshot(id(20), id(22), "Import B", LocalDate.of(1990, 1, 1), null)));

        assertThatThrownBy(() -> fixture.useCase.execute(ORGANIZATION_ID, BATCH_ID, JOB_ID, ACTOR_ID))
                .isInstanceOfSatisfying(ConcurrentUpdateException.class, error ->
                        assertThat(error.errorCode()).isEqualTo("IMPORT_PREVIEW_STALE"));
        verify(fixture.participants, never()).saveAll(any());
        verify(fixture.batchParticipants, never()).updateRosterSnapshots(any(), any());
        verify(fixture.jobs, never()).save(any());
        verify(fixture.auditWriter, never()).record(any());
    }

    @Test
    void previewWithoutSavedStateMustBeValidatedAgain() {
        Fixture fixture = new Fixture();
        HealthExaminationImportRow row = row(5, "012345678901", "Nguyen An", ImportRowAction.CREATE);
        row.setPreviewFingerprint(null);
        fixture.job = validatedJob(row);
        fixture.stubJob();

        assertThatThrownBy(() -> fixture.useCase.execute(ORGANIZATION_ID, BATCH_ID, JOB_ID, ACTOR_ID))
                .isInstanceOfSatisfying(ConcurrentUpdateException.class, error ->
                        assertThat(error.errorCode()).isEqualTo("IMPORT_PREVIEW_STALE"));
        verify(fixture.participants, never()).saveAll(any());
        verify(fixture.batchParticipants, never()).insertRosterSnapshots(any(), any());
        verify(fixture.jobs, never()).save(any());
    }

    @Test
    void changedPlannedDateRequiresAnotherAdultEligibilityValidation() {
        Fixture fixture = new Fixture();
        HealthExaminationImportRow row = HealthExaminationImportRow.roster(AggregateId.of(id(105)), 5, null,
                "Nguyen An", LocalDate.of(2008, 10, 1), "Nam", IdentificationNumber.of("012345678901"));
        row.setAppliedAction(ImportRowAction.CREATE);
        row.setPreviewFingerprint(ValidateParticipantImportUseCase.previewFingerprint(null, null, false, LocalDate.of(2026, 10, 1)));
        fixture.job = validatedJob(row);
        fixture.stubJob();
        when(fixture.batches.findByIdAndOrganizationIdForUpdate(
                AggregateId.of(BATCH_ID), AggregateId.of(ORGANIZATION_ID))).thenReturn(Optional.of(
                new HealthExaminationBatchReference(AggregateId.of(BATCH_ID), AggregateId.of(ORGANIZATION_ID),
                        LocalDate.of(2026, 9, 30), BatchStatus.DRAFT)));

        assertThatThrownBy(() -> fixture.useCase.execute(ORGANIZATION_ID, BATCH_ID, JOB_ID, ACTOR_ID))
                .isInstanceOfSatisfying(ConcurrentUpdateException.class, error ->
                        assertThat(error.errorCode()).isEqualTo("IMPORT_PREVIEW_STALE"));
        verify(fixture.participants, never()).saveAll(any());
        verify(fixture.batchParticipants, never()).insertRosterSnapshots(any(), any());
        verify(fixture.jobs, never()).save(any());
    }

    private static HealthExaminationImportJob validatedJob(HealthExaminationImportRow... rows) {
        HealthExaminationImportJob job = HealthExaminationImportJob.create(
                AggregateId.of(JOB_ID), AggregateId.of(BATCH_ID), ImportType.PARTICIPANT_LIST,
                AggregateId.of(id(8)), AggregateId.of(ACTOR_ID), Instant.parse("2026-09-30T00:00:00Z"));
        EnumMap<ParticipantImportField, Integer> columns = new EnumMap<>(ParticipantImportField.class);
        columns.put(ParticipantImportField.FULL_NAME, 1);
        columns.put(ParticipantImportField.SEX, 2);
        columns.put(ParticipantImportField.DATE_OF_BIRTH, 3);
        columns.put(ParticipantImportField.IDENTIFICATION_NUMBER, 5);
        job.mapColumns(com.ngockhanh.clinic.healthexamination.domain.valueobject.ParticipantImportColumnMapping.of(columns));
        for (HealthExaminationImportRow row : rows) job.addRow(row);
        job.validate();
        return job;
    }

    private static HealthExaminationImportRow row(int rowNumber, String cccd, String name) {
        return row(rowNumber, cccd, name, ImportRowAction.CREATE);
    }

    private static HealthExaminationImportRow row(int rowNumber, String cccd, String name, ImportRowAction action) {
        HealthExaminationImportRow row = HealthExaminationImportRow.roster(
                AggregateId.of(id(rowNumber + 100)), rowNumber, null, name, LocalDate.of(1990, 1, 1), "Nam",
                IdentificationNumber.of(cccd), null, null, null, null, null, null, "0900000000",
                null, null, "Hà Nội", null, null, null, null, null, null);
        row.setAppliedAction(action);
        row.setPreviewFingerprint(ValidateParticipantImportUseCase.previewFingerprint(null, null, false, LocalDate.of(2026, 10, 1)));
        return row;
    }

    private static BatchParticipantRosterSnapshot snapshot(UUID participantId, UUID batchParticipantId,
                                                             String name, LocalDate dob, String occupation) {
        return new BatchParticipantRosterSnapshot(AggregateId.of(batchParticipantId), AggregateId.of(participantId),
                "internal-code-20", null, null, occupation, name, dob, "Nam", IdentificationNumber.of("012345678901"),
                null, null, null, null, null, null, "0900000000", null, null, "Hà Nội", null, null, null, null);
    }

    private static UUID id(long value) {
        return new UUID(0L, value);
    }

    private static final class Fixture {
        private final HealthExaminationBatchRepository batches = mock(HealthExaminationBatchRepository.class);
        private final HealthExaminationImportJobRepository jobs = mock(HealthExaminationImportJobRepository.class);
        private final HealthExaminationParticipantRepository participants = mock(HealthExaminationParticipantRepository.class);
        private final HealthExaminationBatchParticipantRepository batchParticipants =
                mock(HealthExaminationBatchParticipantRepository.class);
        private final ParticipantImportAuditWriter auditWriter = mock(ParticipantImportAuditWriter.class);
        private final AtomicLong nextId = new AtomicLong(50);
        private HealthExaminationImportJob job;
        private final ConfirmParticipantImportUseCase useCase = new ConfirmParticipantImportUseCase(
                batches, jobs, participants, batchParticipants, auditWriter,
                (IdGenerator) () -> id(nextId.getAndIncrement()));

        private Fixture() {
            when(batches.findByIdAndOrganizationIdForUpdate(
                    AggregateId.of(BATCH_ID), AggregateId.of(ORGANIZATION_ID))).thenReturn(Optional.of(
                    new HealthExaminationBatchReference(AggregateId.of(BATCH_ID), AggregateId.of(ORGANIZATION_ID),
                            LocalDate.of(2026, 10, 1), BatchStatus.READY)));
        }

        private void stubJob() {
            when(jobs.findByIdAndBatchIdForUpdate(
                    AggregateId.of(JOB_ID), AggregateId.of(BATCH_ID))).thenAnswer(ignored -> Optional.of(job));
        }
    }
}

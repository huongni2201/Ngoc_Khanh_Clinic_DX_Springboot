package com.ngockhanh.clinic.healthexamination.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import com.ngockhanh.clinic.healthexamination.application.port.out.ImportAttachmentMetadataRepository.ImportAttachmentMetadata;
import com.ngockhanh.clinic.healthexamination.application.port.out.ImportAttachmentMetadataRepository;
import com.ngockhanh.clinic.healthexamination.application.port.out.ImportFileStorage;
import com.ngockhanh.clinic.healthexamination.application.port.out.ParticipantSpreadsheetReader;
import com.ngockhanh.clinic.healthexamination.application.response.ParticipantImportSummaryResponse;
import com.ngockhanh.clinic.healthexamination.application.validation.ParticipantRosterRowValidator;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationImportJob;
import com.ngockhanh.clinic.healthexamination.domain.enums.BatchStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.ImportType;
import com.ngockhanh.clinic.healthexamination.domain.enums.ParticipantImportField;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchParticipantRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository.HealthExaminationBatchReference;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationImportJobRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationParticipantRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;

class ValidateParticipantImportUseCaseTest {
    private static final UUID ORGANIZATION_ID = id(1);
    private static final UUID BATCH_ID = id(2);
    private static final UUID JOB_ID = id(3);
    private static final UUID SOURCE_ID = id(4);
    private static final UUID ACTOR_ID = id(5);

    @Test
    void validatesAllRowsAndReturnsMappingWarningsAndConfirmEligibility() throws Exception {
        Fixture fixture = new Fixture(List.of(row(3, "012345678901")));

        ParticipantImportSummaryResponse response = fixture.useCase.execute(
                ORGANIZATION_ID, BATCH_ID, JOB_ID, ACTOR_ID, mapping());

        assertThat(response.status()).isEqualTo("VALIDATED");
        assertThat(response.totalRows()).isEqualTo(1);
        assertThat(response.validRows()).isEqualTo(1);
        assertThat(response.warningRows()).isEqualTo(1);
        assertThat(response.errorRows()).isZero();
        assertThat(response.confirmAllowed()).isTrue();
        assertThat(response.columnMapping()).containsEntry(ParticipantImportField.FULL_NAME, 1);
        verify(fixture.jobs).save(fixture.job);
    }

    @Test
    void oneInvalidRowPreventsConfirmationForTheEntireFile() throws Exception {
        Fixture fixture = new Fixture(List.of(row(3, "012345678901"), row(4, null)));

        ParticipantImportSummaryResponse response = fixture.useCase.execute(
                ORGANIZATION_ID, BATCH_ID, JOB_ID, ACTOR_ID, mapping());

        assertThat(response.totalRows()).isEqualTo(2);
        assertThat(response.validRows()).isEqualTo(1);
        assertThat(response.errorRows()).isEqualTo(1);
        assertThat(response.confirmAllowed()).isFalse();
    }

    @Test
    void marksEveryOccurrenceOfDuplicateCccdAsBlocking() throws Exception {
        Fixture fixture = new Fixture(List.of(row(3, "012345678901"), row(4, "012345678901")));

        ParticipantImportSummaryResponse response = fixture.useCase.execute(
                ORGANIZATION_ID, BATCH_ID, JOB_ID, ACTOR_ID, mapping());

        assertThat(response.validRows()).isZero();
        assertThat(response.errorRows()).isEqualTo(2);
        assertThat(response.confirmAllowed()).isFalse();
    }

    @Test
    void refreshesAValidatedPreviewForAStaleConfirmAttempt() throws Exception {
        Fixture fixture = new Fixture(List.of(row(3, "012345678901")));

        fixture.useCase.execute(ORGANIZATION_ID, BATCH_ID, JOB_ID, ACTOR_ID, mapping());
        ParticipantImportSummaryResponse refreshed = fixture.useCase.execute(
                ORGANIZATION_ID, BATCH_ID, JOB_ID, ACTOR_ID, mapping());

        assertThat(refreshed.status()).isEqualTo("VALIDATED");
        assertThat(refreshed.totalRows()).isEqualTo(1);
        assertThat(refreshed.confirmAllowed()).isTrue();
        org.mockito.Mockito.verify(fixture.jobs, org.mockito.Mockito.times(2)).save(fixture.job);
    }

    private static Map<String, Integer> mapping() {
        Map<String, Integer> columns = new java.util.HashMap<>();
        columns.put(ParticipantImportField.FULL_NAME.name(), 1);
        columns.put(ParticipantImportField.SEX.name(), 2);
        columns.put(ParticipantImportField.DATE_OF_BIRTH.name(), 3);
        columns.put(ParticipantImportField.IDENTIFICATION_NUMBER.name(), 5);
        return columns;
    }

    private static ParticipantSpreadsheetReader.SpreadsheetRow row(int number, String identificationNumber) {
        Map<Integer, String> cells = new java.util.HashMap<>();
        cells.put(1, "Test Person " + number);
        cells.put(2, "Nam");
        cells.put(3, "32874");
        if (identificationNumber != null) cells.put(5, identificationNumber);
        return new ParticipantSpreadsheetReader.SpreadsheetRow(number, cells);
    }

    private static UUID id(long value) {
        return new UUID(0L, value);
    }

    private static final class Fixture {
        private final HealthExaminationBatchRepository batches = mock(HealthExaminationBatchRepository.class);
        private final HealthExaminationImportJobRepository jobs = mock(HealthExaminationImportJobRepository.class);
        private final ImportAttachmentMetadataRepository attachments = mock(ImportAttachmentMetadataRepository.class);
        private final ImportFileStorage storage = mock(ImportFileStorage.class);
        private final ParticipantSpreadsheetReader spreadsheets = mock(ParticipantSpreadsheetReader.class);
        private final HealthExaminationParticipantRepository participants = mock(HealthExaminationParticipantRepository.class);
        private final HealthExaminationBatchParticipantRepository batchParticipants = mock(HealthExaminationBatchParticipantRepository.class);
        private final StoreValidatedParticipantImportUseCase storeValidated;
        private final HealthExaminationImportJob job = HealthExaminationImportJob.create(
                new AggregateId(JOB_ID), new AggregateId(BATCH_ID), ImportType.PARTICIPANT_LIST,
                new AggregateId(SOURCE_ID), new AggregateId(ACTOR_ID), java.time.Instant.parse("2026-09-30T00:00:00Z"));
        private final ValidateParticipantImportUseCase useCase;

        private Fixture(List<ParticipantSpreadsheetReader.SpreadsheetRow> rows) throws Exception {
            when(batches.findByIdAndOrganizationIdForUpdate(
                    new AggregateId(BATCH_ID), new AggregateId(ORGANIZATION_ID))).thenReturn(Optional.of(
                    new HealthExaminationBatchReference(new AggregateId(BATCH_ID), new AggregateId(ORGANIZATION_ID),
                            LocalDate.of(2026, 10, 1), BatchStatus.READY)));
            when(batches.findByIdAndOrganizationId(
                    new AggregateId(BATCH_ID), new AggregateId(ORGANIZATION_ID))).thenReturn(Optional.of(
                    new HealthExaminationBatchReference(new AggregateId(BATCH_ID), new AggregateId(ORGANIZATION_ID),
                            LocalDate.of(2026, 10, 1), BatchStatus.READY)));
            when(jobs.findByIdAndBatchId(new AggregateId(JOB_ID), new AggregateId(BATCH_ID)))
                    .thenReturn(Optional.of(job));
            when(jobs.findByIdAndBatchIdForUpdate(new AggregateId(JOB_ID), new AggregateId(BATCH_ID)))
                    .thenReturn(Optional.of(job));
            when(attachments.findById(SOURCE_ID)).thenReturn(Optional.of(new ImportAttachmentMetadata(
                    SOURCE_ID, JOB_ID, ACTOR_ID, "health-examination-imports/source.gcm", "roster.xls",
                    "application/vnd.ms-excel", 1, "sha256", java.time.Instant.parse("2026-09-30T00:00:00Z"))));
            when(storage.open("health-examination-imports/source.gcm")).thenReturn(new ByteArrayInputStream(new byte[]{1}));
            when(spreadsheets.readHeader(any(), any())).thenReturn(new ParticipantSpreadsheetReader.SpreadsheetHeader(
                    2, List.of("STT", "Họ tên", "Giới tính", "Ngày sinh", "Điện thoại", "CCCD")));
            doAnswer(invocation -> {
                @SuppressWarnings("unchecked") Consumer<ParticipantSpreadsheetReader.SpreadsheetRow> consumer = invocation.getArgument(2);
                rows.forEach(consumer);
                return null;
            }).when(spreadsheets).readRows(any(), any(), any());
            when(participants.findByOrganizationAndIdentificationNumbers(any(), any())).thenReturn(List.of());
            when(batchParticipants.findRosterSnapshots(any(), any())).thenReturn(List.of());
            when(batchParticipants.findBatchParticipantIdsWithHealthRecords(any())).thenReturn(Set.of());
            storeValidated = new StoreValidatedParticipantImportUseCase(batches, jobs);
            AtomicInteger nextId = new AtomicInteger(10);
            IdGenerator ids = () -> id(nextId.getAndIncrement());
            useCase = new ValidateParticipantImportUseCase(batches, jobs, attachments, storage, spreadsheets,
                    new ParticipantRosterRowValidator(), participants, batchParticipants, ids, storeValidated);
        }
    }
}

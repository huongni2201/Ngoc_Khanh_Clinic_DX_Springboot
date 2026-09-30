package com.ngockhanh.clinic.healthexamination.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ngockhanh.clinic.healthexamination.application.port.out.ImportAttachmentMetadataRepository.ImportAttachmentMetadata;
import com.ngockhanh.clinic.healthexamination.application.port.out.ImportAttachmentMetadataRepository;
import com.ngockhanh.clinic.healthexamination.application.port.out.ImportFileStorage;
import com.ngockhanh.clinic.healthexamination.application.port.out.ParticipantSpreadsheetReader;
import com.ngockhanh.clinic.healthexamination.application.port.out.ParticipantSpreadsheetReader.SpreadsheetFormat;
import com.ngockhanh.clinic.healthexamination.application.response.ParticipantImportRowsPageResponse;
import com.ngockhanh.clinic.healthexamination.application.usecase.ListParticipantImportRowsUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.GetParticipantImportUseCase;
import com.ngockhanh.clinic.healthexamination.application.validation.ParticipantRosterHeaderMapper;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationImportJob;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationImportRow;
import com.ngockhanh.clinic.healthexamination.domain.enums.BatchStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.ImportType;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository.HealthExaminationBatchReference;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationImportJobRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.IdentificationNumber;

class ParticipantImportReadUseCasesTest {
    private static final UUID ORGANIZATION_ID = id(1);
    private static final UUID BATCH_ID = id(2);
    private static final UUID JOB_ID = id(3);
    private static final UUID SOURCE_ID = id(4);

    @Test
    void returnsMaskedRowsForTheRequestedPageAndFilter() {
        HealthExaminationBatchRepository batches = mock(HealthExaminationBatchRepository.class);
        HealthExaminationImportJobRepository jobs = mock(HealthExaminationImportJobRepository.class);
        var valid = HealthExaminationImportRow.roster(AggregateId.of(id(5)), 5, null, "Nguyen An",
                LocalDate.of(1990, 1, 1), "Nam", IdentificationNumber.of("012345678901"));
        var invalid = HealthExaminationImportRow.invalid(AggregateId.of(id(6)), 6, "MISSING_IDENTIFICATION_NUMBER");
        var job = HealthExaminationImportJob.restore(AggregateId.of(JOB_ID), AggregateId.of(BATCH_ID),
                ImportType.PARTICIPANT_LIST, com.ngockhanh.clinic.healthexamination.domain.enums.ImportStatus.VALIDATED,
                List.of(valid, invalid));
        stubOwnership(batches, jobs, job);
        ListParticipantImportRowsUseCase useCase = new ListParticipantImportRowsUseCase(batches, jobs);

        ParticipantImportRowsPageResponse response = useCase.execute(ORGANIZATION_ID, BATCH_ID, JOB_ID, 1, 10, "VALID");

        assertThat(response.totalRows()).isEqualTo(1);
        assertThat(response.rows()).singleElement().satisfies(row -> {
            assertThat(row.rowNumber()).isEqualTo(5);
            assertThat(row.maskedIdentificationNumber()).isEqualTo("••••••8901");
        });
    }

    @Test
    void rejectsUnknownRowFilters() {
        HealthExaminationBatchRepository batches = mock(HealthExaminationBatchRepository.class);
        HealthExaminationImportJobRepository jobs = mock(HealthExaminationImportJobRepository.class);
        stubOwnership(batches, jobs, HealthExaminationImportJob.restore(AggregateId.of(JOB_ID),
                AggregateId.of(BATCH_ID), ImportType.PARTICIPANT_LIST,
                com.ngockhanh.clinic.healthexamination.domain.enums.ImportStatus.VALIDATED, List.of()));
        ListParticipantImportRowsUseCase useCase = new ListParticipantImportRowsUseCase(batches, jobs);

        assertThatThrownBy(() -> useCase.execute(ORGANIZATION_ID, BATCH_ID, JOB_ID, 1, 10, "SOMEONE"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void returnsSuggestedMappingWhenAnUploadedJobHasNoSavedMappingYet() throws Exception {
        HealthExaminationBatchRepository batches = mock(HealthExaminationBatchRepository.class);
        HealthExaminationImportJobRepository jobs = mock(HealthExaminationImportJobRepository.class);
        ImportAttachmentMetadataRepository attachments = mock(ImportAttachmentMetadataRepository.class);
        ImportFileStorage storage = mock(ImportFileStorage.class);
        ParticipantSpreadsheetReader spreadsheets = mock(ParticipantSpreadsheetReader.class);
        HealthExaminationImportJob job = HealthExaminationImportJob.create(AggregateId.of(JOB_ID),
                AggregateId.of(BATCH_ID), ImportType.PARTICIPANT_LIST, AggregateId.of(SOURCE_ID),
                AggregateId.of(id(9)), Instant.parse("2026-09-30T00:00:00Z"));
        stubOwnership(batches, jobs, job);
        when(attachments.findById(SOURCE_ID)).thenReturn(Optional.of(new ImportAttachmentMetadata(SOURCE_ID,
                JOB_ID, id(9), "health-examination-imports/source.gcm", "roster.xls", "application/vnd.ms-excel",
                10, "hash", Instant.parse("2026-09-30T00:00:00Z"))));
        when(storage.open("health-examination-imports/source.gcm")).thenReturn(new ByteArrayInputStream(new byte[]{1}));
        when(spreadsheets.readHeader(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(SpreadsheetFormat.XLS)))
                .thenReturn(new ParticipantSpreadsheetReader.SpreadsheetHeader(2,
                        List.of("STT", "Họ và tên", "Giới tính", "Ngày sinh", "CCCD")));
        GetParticipantImportUseCase useCase = new GetParticipantImportUseCase(batches, jobs, attachments,
                storage, spreadsheets, new ParticipantRosterHeaderMapper());

        var response = useCase.execute(ORGANIZATION_ID, BATCH_ID, JOB_ID);

        assertThat(response.status()).isEqualTo("UPLOADED");
        assertThat(response.confirmAllowed()).isFalse();
        assertThat(response.columnMapping()).containsKeys(
                com.ngockhanh.clinic.healthexamination.domain.enums.ParticipantImportField.FULL_NAME,
                com.ngockhanh.clinic.healthexamination.domain.enums.ParticipantImportField.IDENTIFICATION_NUMBER);
    }

    private static void stubOwnership(HealthExaminationBatchRepository batches,
                                     HealthExaminationImportJobRepository jobs,
                                     HealthExaminationImportJob job) {
        when(batches.findByIdAndOrganizationId(AggregateId.of(BATCH_ID), AggregateId.of(ORGANIZATION_ID)))
                .thenReturn(Optional.of(new HealthExaminationBatchReference(
                AggregateId.of(BATCH_ID), AggregateId.of(ORGANIZATION_ID), LocalDate.of(2026, 10, 1), BatchStatus.READY)));
        when(jobs.findByIdAndBatchId(AggregateId.of(JOB_ID), AggregateId.of(BATCH_ID))).thenReturn(Optional.of(job));
    }

    private static UUID id(long value) {
        return new UUID(0L, value);
    }
}

package com.ngockhanh.clinic.healthexamination.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.ngockhanh.clinic.healthexamination.application.command.UploadParticipantImportCommand;
import com.ngockhanh.clinic.healthexamination.application.port.out.ImportAttachmentMetadataRepository.ImportAttachmentMetadata;
import com.ngockhanh.clinic.healthexamination.application.port.out.ImportFileStorage;
import com.ngockhanh.clinic.healthexamination.application.port.out.ParticipantSpreadsheetReader;
import com.ngockhanh.clinic.healthexamination.application.port.out.ParticipantSpreadsheetReader.SpreadsheetFormat;
import com.ngockhanh.clinic.healthexamination.application.validation.ParticipantRosterHeaderMapper;
import com.ngockhanh.clinic.healthexamination.domain.enums.BatchStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.ParticipantImportField;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository.HealthExaminationBatchReference;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationImportJob;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;

class UploadParticipantImportUseCaseTest {
    private static final UUID ORGANIZATION_ID = id(1);
    private static final UUID BATCH_ID = id(2);
    private static final UUID ACTOR_ID = id(3);
    private static final UUID JOB_ID = id(4);
    private static final UUID ATTACHMENT_ID = id(5);

    @Test
    void uploadsXlsAndCreatesAnUnmappedStagingJob() throws Exception {
        HealthExaminationBatchRepository batches = mock(HealthExaminationBatchRepository.class);
        ImportFileStorage storage = mock(ImportFileStorage.class);
        ParticipantSpreadsheetReader reader = mock(ParticipantSpreadsheetReader.class);
        RegisterParticipantImportUseCase register = mock(RegisterParticipantImportUseCase.class);
        when(batches.findByIdAndOrganizationId(AggregateId.of(BATCH_ID), AggregateId.of(ORGANIZATION_ID)))
                .thenReturn(java.util.Optional.of(
                new HealthExaminationBatchReference(AggregateId.of(BATCH_ID), AggregateId.of(ORGANIZATION_ID),
                        LocalDate.of(2026, 10, 1), BatchStatus.READY)));
        when(storage.store(eq(JOB_ID), any(), anyLong()))
                .thenReturn(new ImportFileStorage.StoredFile("health-examination-imports/fixture.gcm", 12, "hash"));
        when(storage.open("health-examination-imports/fixture.gcm"))
                .thenReturn(new ByteArrayInputStream(new byte[]{1}));
        when(reader.readHeader(any(), eq(SpreadsheetFormat.XLS))).thenReturn(new ParticipantSpreadsheetReader.SpreadsheetHeader(
                2, List.of("STT", "Họ và tên", "Giới tính", "Ngày sinh", "Điện thoại", "CCCD")));
        AtomicInteger nextId = new AtomicInteger();
        IdGenerator ids = () -> nextId.getAndIncrement() == 0 ? JOB_ID : ATTACHMENT_ID;
        UploadParticipantImportUseCase useCase = new UploadParticipantImportUseCase(
                batches, storage, reader, new ParticipantRosterHeaderMapper(), ids, register);

        byte[] xlsMagic = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
        var result = useCase.execute(new UploadParticipantImportCommand(ORGANIZATION_ID, BATCH_ID, ACTOR_ID,
                "roster.xls", "application/vnd.ms-excel", xlsMagic.length, new ByteArrayInputStream(xlsMagic)));

        assertThat(result.importId()).isEqualTo(JOB_ID);
        assertThat(result.status()).isEqualTo("UPLOADED");
        assertThat(result.headerRowNumber()).isEqualTo(2);
        assertThat(result.suggestedMapping()).containsEntry(ParticipantImportField.IDENTIFICATION_NUMBER, 5);
        var jobCaptor = org.mockito.ArgumentCaptor.forClass(HealthExaminationImportJob.class);
        var attachmentCaptor = org.mockito.ArgumentCaptor.forClass(ImportAttachmentMetadata.class);
        verify(register).execute(eq(ORGANIZATION_ID), eq(BATCH_ID), jobCaptor.capture(), attachmentCaptor.capture());
        assertThat(jobCaptor.getValue().status().name()).isEqualTo("UPLOADED");
        assertThat(jobCaptor.getValue().columnMapping()).isNull();
        assertThat(attachmentCaptor.getValue().createdByUserId()).isEqualTo(ACTOR_ID);
    }

    private static UUID id(long value) {
        return new UUID(0L, value);
    }
}

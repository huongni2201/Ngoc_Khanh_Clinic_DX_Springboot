package com.ngockhanh.clinic.healthexamination.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ngockhanh.clinic.healthexamination.application.port.out.ImportAttachmentMetadataRepository.ImportAttachmentMetadata;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper.ImportAttachmentMetadataMapper;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository.MyBatisImportAttachmentMetadataRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MyBatisImportAttachmentMetadataRepositoryTest {
  @Test
  void findsStoredSourceMetadataByAttachmentAndImportJobId() {
    ImportAttachmentMetadataMapper mapper = mock(ImportAttachmentMetadataMapper.class);
    UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
    ImportAttachmentMetadata metadata =
        new ImportAttachmentMetadata(
            id,
            UUID.randomUUID(),
            UUID.randomUUID(),
            "health-examination-imports/file.gcm",
            "roster.xls",
            "application/vnd.ms-excel",
            100,
            "sha256",
            Instant.parse("2026-09-30T00:00:00Z"));
    when(mapper.findByIdAndImportJobId(id, metadata.importJobId())).thenReturn(metadata);
    MyBatisImportAttachmentMetadataRepository repository =
        new MyBatisImportAttachmentMetadataRepository(mapper);

    assertThat(repository.findByIdAndImportJobId(id, metadata.importJobId())).contains(metadata);
  }
}

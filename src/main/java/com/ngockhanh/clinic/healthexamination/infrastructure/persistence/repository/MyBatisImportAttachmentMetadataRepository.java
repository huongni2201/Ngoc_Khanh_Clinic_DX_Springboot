package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository;

import com.ngockhanh.clinic.healthexamination.application.port.out.ImportAttachmentMetadataRepository;
import com.ngockhanh.clinic.healthexamination.application.port.out.ImportAttachmentMetadataRepository.ImportAttachmentMetadata;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper.ImportAttachmentMetadataMapper;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MyBatisImportAttachmentMetadataRepository
    implements ImportAttachmentMetadataRepository {
  private final ImportAttachmentMetadataMapper mapper;

  @Override
  public Optional<ImportAttachmentMetadata> findByIdAndImportJobId(UUID id, UUID importJobId) {
    return Optional.ofNullable(mapper.findByIdAndImportJobId(id, importJobId));
  }

  @Override
  public void save(ImportAttachmentMetadata attachment) {
    if (mapper.insert(attachment) != 1)
      throw new IllegalStateException("Import source metadata was not saved");
  }
}

package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository;

import lombok.RequiredArgsConstructor;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

import com.ngockhanh.clinic.healthexamination.application.port.out.ImportAttachmentMetadataRepository.ImportAttachmentMetadata;
import com.ngockhanh.clinic.healthexamination.application.port.out.ImportAttachmentMetadataRepository;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper.ImportAttachmentMetadataMapper;

@Repository
@RequiredArgsConstructor
public class MyBatisImportAttachmentMetadataRepository implements ImportAttachmentMetadataRepository {
    private final ImportAttachmentMetadataMapper mapper;

    @Override
    public Optional<ImportAttachmentMetadata> findById(UUID id) {
        return Optional.ofNullable(mapper.findById(id));
    }

    @Override
    public void save(ImportAttachmentMetadata attachment) {
        if (mapper.insert(attachment) != 1) throw new IllegalStateException("Import source metadata was not saved");
    }
}

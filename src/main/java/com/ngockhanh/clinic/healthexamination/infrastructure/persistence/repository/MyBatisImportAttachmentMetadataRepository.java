package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.ngockhanh.clinic.healthexamination.application.port.ImportAttachmentMetadata;
import com.ngockhanh.clinic.healthexamination.application.port.ImportAttachmentMetadataRepository;
import com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper.ImportAttachmentMetadataMapper;

@Repository
@RequiredArgsConstructor
public class MyBatisImportAttachmentMetadataRepository implements ImportAttachmentMetadataRepository {
    private final ImportAttachmentMetadataMapper mapper;

    @Override
    public void save(ImportAttachmentMetadata attachment) {
        if (mapper.insert(attachment) != 1) throw new IllegalStateException("Import source metadata was not saved");
    }
}

package com.ngockhanh.clinic.healthcheck.infrastructure.persistence.repository;

import org.springframework.stereotype.Repository;

import com.ngockhanh.clinic.healthcheck.application.port.ImportAttachmentMetadata;
import com.ngockhanh.clinic.healthcheck.application.port.ImportAttachmentMetadataRepository;
import com.ngockhanh.clinic.healthcheck.infrastructure.persistence.mapper.ImportAttachmentMetadataMapper;

@Repository
public final class MyBatisImportAttachmentMetadataRepository implements ImportAttachmentMetadataRepository {
    private final ImportAttachmentMetadataMapper mapper;

    public MyBatisImportAttachmentMetadataRepository(ImportAttachmentMetadataMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(ImportAttachmentMetadata attachment) {
        if (mapper.insert(attachment) != 1) throw new IllegalStateException("Import source metadata was not saved");
    }
}

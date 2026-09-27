package com.ngockhanh.clinic.healthcheck.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.ngockhanh.clinic.healthcheck.application.port.ImportAttachmentMetadata;

@Mapper
public interface ImportAttachmentMetadataMapper {
    int insert(ImportAttachmentMetadata attachment);
}

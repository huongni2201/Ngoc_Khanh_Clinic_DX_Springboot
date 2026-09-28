package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.ngockhanh.clinic.healthexamination.application.port.ImportAttachmentMetadata;

@Mapper
public interface ImportAttachmentMetadataMapper {
    int insert(ImportAttachmentMetadata attachment);
}

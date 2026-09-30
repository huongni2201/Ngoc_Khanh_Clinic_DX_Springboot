package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ngockhanh.clinic.healthexamination.application.port.out.ImportAttachmentMetadataRepository.ImportAttachmentMetadata;

@Mapper
public interface ImportAttachmentMetadataMapper {
    ImportAttachmentMetadata findById(@Param("id") java.util.UUID id);
    int insert(ImportAttachmentMetadata attachment);
}

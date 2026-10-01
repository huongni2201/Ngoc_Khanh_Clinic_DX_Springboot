package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.mapper;

import com.ngockhanh.clinic.healthexamination.application.port.out.ImportAttachmentMetadataRepository.ImportAttachmentMetadata;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ImportAttachmentMetadataMapper {
  ImportAttachmentMetadata findByIdAndImportJobId(
      @Param("id") java.util.UUID id, @Param("importJobId") java.util.UUID importJobId);

  int insert(ImportAttachmentMetadata attachment);
}

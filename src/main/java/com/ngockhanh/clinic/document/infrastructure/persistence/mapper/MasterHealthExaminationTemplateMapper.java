package com.ngockhanh.clinic.document.infrastructure.persistence.mapper;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MasterHealthExaminationTemplateMapper {
  List<UUID> findEffectiveVersions();
}

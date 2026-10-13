package com.ngockhanh.clinic.document.infrastructure.persistence.mapper;

import com.ngockhanh.clinic.document.application.port.NewFile;
import com.ngockhanh.clinic.document.application.port.NewPublishedVersion;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** MyBatis access to templates, template versions and their files. */
@Mapper
public interface DocumentTemplateMapper {
  List<EffectiveVersionRow> findEffective(
      @Param("templateType") String templateType, @Param("at") Instant at);

  int lockForSeeding(@Param("key") String key);

  boolean hasVersions(@Param("templateType") String templateType);

  UUID findDefaultTemplateId(@Param("templateType") String templateType);

  int insertDefaultTemplate(
      @Param("id") UUID id,
      @Param("code") String code,
      @Param("name") String name,
      @Param("templateType") String templateType);

  int insertFile(NewFile file);

  int insertPublishedVersion(NewPublishedVersion version);
}

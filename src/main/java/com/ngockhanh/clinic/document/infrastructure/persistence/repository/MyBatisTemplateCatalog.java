package com.ngockhanh.clinic.document.infrastructure.persistence.repository;

import com.ngockhanh.clinic.document.application.port.EffectiveTemplateVersion;
import com.ngockhanh.clinic.document.application.port.NewFile;
import com.ngockhanh.clinic.document.application.port.NewPublishedVersion;
import com.ngockhanh.clinic.document.application.port.TemplateCatalog;
import com.ngockhanh.clinic.document.infrastructure.persistence.mapper.DocumentTemplateMapper;
import com.ngockhanh.clinic.shared.exception.BusinessRuleException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MyBatisTemplateCatalog implements TemplateCatalog {
  private final DocumentTemplateMapper mapper;

  @Override
  public Optional<EffectiveTemplateVersion> findEffective(String templateType, Instant at) {
    var rows = mapper.findEffective(templateType, at);
    if (rows.size() > 1)
      throw new BusinessRuleException("Multiple effective templates for " + templateType);
    return rows.stream()
        .findFirst()
        .map(
            row ->
                new EffectiveTemplateVersion(
                    row.versionId(),
                    row.versionNo(),
                    row.contractVersion(),
                    row.storageProvider(),
                    row.storageKey(),
                    row.checksum(),
                    row.pagesPerRecord()));
  }

  @Override
  public void lockForSeeding(String templateType) {
    mapper.lockForSeeding("document-template-seed:" + templateType);
  }

  @Override
  public boolean hasVersions(String templateType) {
    return mapper.hasVersions(templateType);
  }

  @Override
  public Optional<UUID> findDefaultTemplateId(String templateType) {
    return Optional.ofNullable(mapper.findDefaultTemplateId(templateType));
  }

  @Override
  public void insertDefaultTemplate(UUID id, String code, String name, String templateType) {
    requireOne(mapper.insertDefaultTemplate(id, code, name, templateType), "template");
  }

  @Override
  public void insertFile(NewFile file) {
    requireOne(mapper.insertFile(file), "file");
  }

  @Override
  public void insertPublishedVersion(NewPublishedVersion version) {
    requireOne(mapper.insertPublishedVersion(version), "template version");
  }

  private static void requireOne(int rows, String what) {
    if (rows != 1) throw new IllegalStateException("The " + what + " was not saved");
  }
}

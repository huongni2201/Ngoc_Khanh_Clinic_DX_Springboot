package com.ngockhanh.clinic.document.application.port;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Storage port for templates and their versions. */
public interface TemplateCatalog {
  /**
   * Finds the published version of the default template of a type that is in effect at an instant.
   *
   * @throws com.ngockhanh.clinic.shared.exception.BusinessRuleException when more than one version
   *     is in effect
   */
  Optional<EffectiveTemplateVersion> findEffective(String templateType, Instant at);

  /** Serializes concurrent seeding of one type until the transaction ends. */
  void lockForSeeding(String templateType);

  /** Whether any template of the type has a version in any status. */
  boolean hasVersions(String templateType);

  /** Finds the default template of a type. */
  Optional<UUID> findDefaultTemplateId(String templateType);

  /** Inserts an active default template. */
  void insertDefaultTemplate(UUID id, String code, String name, String templateType);

  /** Inserts a file row. */
  void insertFile(NewFile file);

  /** Inserts a published version seeded by the system. */
  void insertPublishedVersion(NewPublishedVersion version);
}

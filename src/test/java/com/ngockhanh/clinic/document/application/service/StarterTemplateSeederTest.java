package com.ngockhanh.clinic.document.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ngockhanh.clinic.document.application.contract.DocumentTypeContract;
import com.ngockhanh.clinic.document.application.contract.PlaceholderSpec;
import com.ngockhanh.clinic.document.application.port.EffectiveTemplateVersion;
import com.ngockhanh.clinic.document.application.port.NewFile;
import com.ngockhanh.clinic.document.application.port.NewPublishedVersion;
import com.ngockhanh.clinic.document.application.port.TemplateCatalog;
import com.ngockhanh.clinic.document.infrastructure.rendering.DomDocxTemplateEngine;
import com.ngockhanh.clinic.document.infrastructure.storage.ConfiguredDocumentStorage;
import com.ngockhanh.clinic.healthexamination.application.contract.PeriodicFormBlankContract;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StarterTemplateSeederTest {
  private static final Instant NOW = Instant.parse("2026-10-12T01:00:00Z");

  private final PeriodicFormBlankContract contract = new PeriodicFormBlankContract();
  private final InMemoryCatalog catalog = new InMemoryCatalog();
  private final ConfiguredDocumentStorage storage =
      new ConfiguredDocumentStorage(null, getClass().getClassLoader());
  private final DomDocxTemplateEngine engine = new DomDocxTemplateEngine();

  private StarterTemplateSeeder seeder(DocumentTypeContract... contracts) {
    return new StarterTemplateSeeder(
        List.of(contracts), catalog, storage, engine, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void seedsVersionOneOfAnEmptyTypeFromTheStarter() throws Exception {
    assertThat(seeder(contract).seed()).isEqualTo(1);

    assertThat(catalog.templates).hasSize(1);
    assertThat(catalog.files).hasSize(1);
    assertThat(catalog.versions).hasSize(1);
    NewPublishedVersion version = catalog.versions.getFirst();
    assertThat(version.versionNo()).isEqualTo(1);
    assertThat(version.contractVersion()).isEqualTo(1);
    assertThat(version.renderMode()).isEqualTo("MASTER_FORM");
    assertThat(version.paperSize()).isEqualTo("A3");
    assertThat(version.orientation()).isEqualTo("LANDSCAPE");
    assertThat(version.pageWidthMm()).isEqualByComparingTo("420.1");
    assertThat(version.pagesPerRecord()).isEqualTo((short) 2);
    assertThat(version.publishedAt()).isEqualTo(NOW);
    NewFile file = catalog.files.getFirst();
    byte[] starter = storage.read("CLASSPATH", contract.starterResource(), null);
    assertThat(file.storageProvider()).isEqualTo("CLASSPATH");
    assertThat(file.storageKey()).isEqualTo(contract.starterResource());
    assertThat(file.originalFilename()).isEqualTo("starter-v1.docx");
    assertThat(file.sizeBytes()).isEqualTo(starter.length);
    assertThat(file.checksum()).isEqualTo(MessageDigest.getInstance("SHA-256").digest(starter));
    assertThat(catalog.locked).containsExactly(PeriodicFormBlankContract.DOCUMENT_TYPE);
  }

  @Test
  void aTypeThatAlreadyHasVersionsIsLeftAlone() {
    seeder(contract).seed();

    assertThat(seeder(contract).seed()).isZero();
    assertThat(catalog.versions).hasSize(1);
  }

  @Test
  void reusesAnExistingDefaultTemplateThatHasNoVersion() {
    UUID existing = UUID.randomUUID();
    catalog.defaultTemplateId = existing;

    seeder(contract).seed();

    assertThat(catalog.templates).isEmpty();
    assertThat(catalog.versions.getFirst().templateId()).isEqualTo(existing);
  }

  @Test
  void refusesAStarterThatDoesNotMatchItsContract() {
    var mismatched =
        new DocumentTypeContract() {
          @Override
          public String documentType() {
            return "MISMATCHED";
          }

          @Override
          public String templateCode() {
            return "MISMATCHED";
          }

          @Override
          public String templateName() {
            return "Mismatched";
          }

          @Override
          public int contractVersion() {
            return 1;
          }

          @Override
          public String starterResource() {
            return contract.starterResource();
          }

          @Override
          public Integer pagesPerRecord() {
            return null;
          }

          @Override
          public List<PlaceholderSpec> placeholders() {
            return List.of(
                new PlaceholderSpec("participant.code", "Code", "", true),
                new PlaceholderSpec("batch.code", "Batch", "", true));
          }
        };

    assertThatThrownBy(() -> seeder(mismatched).seed())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("unknown=[participant.address")
        .hasMessageContaining("missing required=[batch.code]");
    assertThat(catalog.versions).isEmpty();
  }

  /** Keeps what the seeder writes; enough to observe it without a database. */
  private static final class InMemoryCatalog implements TemplateCatalog {
    final List<UUID> templates = new ArrayList<>();
    final List<NewFile> files = new ArrayList<>();
    final List<NewPublishedVersion> versions = new ArrayList<>();
    final Set<String> locked = new HashSet<>();
    UUID defaultTemplateId;

    @Override
    public Optional<EffectiveTemplateVersion> findEffective(String templateType, Instant at) {
      return Optional.empty();
    }

    @Override
    public void lockForSeeding(String templateType) {
      locked.add(templateType);
    }

    @Override
    public boolean hasVersions(String templateType) {
      return !versions.isEmpty();
    }

    @Override
    public Optional<UUID> findDefaultTemplateId(String templateType) {
      return Optional.ofNullable(defaultTemplateId);
    }

    @Override
    public void insertDefaultTemplate(UUID id, String code, String name, String templateType) {
      templates.add(id);
      defaultTemplateId = id;
    }

    @Override
    public void insertFile(NewFile file) {
      files.add(file);
    }

    @Override
    public void insertPublishedVersion(NewPublishedVersion version) {
      versions.add(version);
    }
  }
}

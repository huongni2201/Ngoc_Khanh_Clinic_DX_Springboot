package com.ngockhanh.clinic.document.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ngockhanh.clinic.document.application.port.DocumentStorage;
import com.ngockhanh.clinic.document.application.port.DocxTemplateEngine;
import com.ngockhanh.clinic.document.application.port.PdfConverter;
import com.ngockhanh.clinic.document.application.port.TemplateCatalog;
import com.ngockhanh.clinic.document.application.service.DocumentRenderingService;
import com.ngockhanh.clinic.document.application.service.StarterTemplateSeeder;
import com.ngockhanh.clinic.document.infrastructure.persistence.repository.MyBatisTemplateCatalog;
import com.ngockhanh.clinic.document.infrastructure.rendering.DomDocxTemplateEngine;
import com.ngockhanh.clinic.document.infrastructure.storage.ConfiguredDocumentStorage;
import com.ngockhanh.clinic.healthexamination.application.contract.PeriodicFormBlankContract;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Seeds the starter template into a real PostgreSQL database and renders it through the real
 * catalog, storage and engine (skipped without Docker). Only the PDF conversion is replaced, so the
 * test shows the filled Word file the converter would receive.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
    classes = StarterTemplateSeedIntegrationTest.SeedTestConfiguration.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE)
class StarterTemplateSeedIntegrationTest {
  @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
  @org.springframework.boot.autoconfigure.EnableAutoConfiguration
  @org.mybatis.spring.annotation.MapperScan(
      basePackages = "com.ngockhanh.clinic.document.infrastructure.persistence.mapper")
  @org.springframework.context.annotation.Import({
    MyBatisTemplateCatalog.class,
    StarterTemplateSeeder.class,
    PeriodicFormBlankContract.class
  })
  static class SeedTestConfiguration {
    @Bean
    DocumentStorage documentStorage() {
      return new ConfiguredDocumentStorage(null, SeedTestConfiguration.class.getClassLoader());
    }

    @Bean
    DocxTemplateEngine docxTemplateEngine() {
      return new DomDocxTemplateEngine();
    }

    @Bean
    Clock clock() {
      return Clock.systemUTC();
    }
  }

  @Container static final PostgreSQLContainer DB = new PostgreSQLContainer("postgres:18-alpine");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", DB::getJdbcUrl);
    r.add("spring.datasource.username", DB::getUsername);
    r.add("spring.datasource.password", DB::getPassword);
  }

  @Autowired JdbcTemplate jdbc;
  @Autowired StarterTemplateSeeder seeder;
  @Autowired TemplateCatalog catalog;
  @Autowired DocumentStorage storage;
  @Autowired DocxTemplateEngine engine;

  @BeforeEach
  void emptyDocumentTables() {
    jdbc.execute("TRUNCATE public.template_versions, public.files, public.templates CASCADE");
  }

  @Test
  void seedsOnePublishedA3LandscapeVersionAndIsIdempotent() {
    assertThat(seeder.seed()).isEqualTo(1);
    assertThat(seeder.seed()).isZero();

    var row =
        jdbc.queryForMap(
            "SELECT t.code, t.is_default, t.active, v.version_no, v.status, v.render_mode,"
                + " v.paper_size, v.orientation, v.pages_per_record, v.contract_version,"
                + " v.created_by, v.published_by, f.storage_provider, f.storage_key"
                + " FROM public.templates t JOIN public.template_versions v ON v.template_id=t.id"
                + " JOIN public.files f ON f.id=v.file_id");
    assertThat(row)
        .containsEntry("code", PeriodicFormBlankContract.DOCUMENT_TYPE)
        .containsEntry("is_default", true)
        .containsEntry("active", true)
        .containsEntry("version_no", 1)
        .containsEntry("status", "PUBLISHED")
        .containsEntry("render_mode", "MASTER_FORM")
        .containsEntry("paper_size", "A3")
        .containsEntry("orientation", "LANDSCAPE")
        .containsEntry("pages_per_record", 2)
        .containsEntry("contract_version", 1)
        .containsEntry("storage_provider", "CLASSPATH");
    assertThat(row.get("created_by")).isNull();
    assertThat(row.get("published_by")).isNull();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM public.template_versions", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void findsTheEffectiveVersionOnlyFromItsStartInstant() {
    seeder.seed();

    assertThat(catalog.findEffective(PeriodicFormBlankContract.DOCUMENT_TYPE, Instant.now()))
        .get()
        .satisfies(
            version -> {
              assertThat(version.versionNo()).isEqualTo(1);
              assertThat(version.storageProvider()).isEqualTo("CLASSPATH");
              assertThat(version.pagesPerRecord()).isEqualTo(2);
            });
    assertThat(
            catalog.findEffective(
                PeriodicFormBlankContract.DOCUMENT_TYPE, Instant.now().minus(Duration.ofDays(1))))
        .isEmpty();
    assertThat(catalog.findEffective("OTHER_TYPE", Instant.now())).isEmpty();
  }

  @Test
  void aDraftVersionIsNeverEffectiveAndStopsTheTypeFromBeingSeeded() {
    UUID template = UUID.randomUUID();
    UUID file = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO public.templates(id,code,name,template_type,active,is_default)"
            + " VALUES (?,'DRAFT_ONLY','Draft only',?,true,true)",
        template,
        PeriodicFormBlankContract.DOCUMENT_TYPE);
    jdbc.update(
        "INSERT INTO public.files(id,storage_provider,storage_key,original_filename,content_type,size_bytes,checksum)"
            + " VALUES (?,'LOCAL','draft.docx','draft.docx','application/octet-stream',1,'\\x00'::bytea)",
        file);
    jdbc.update(
        "INSERT INTO public.template_versions(id,template_id,version_no,file_id,paper_size,orientation,render_mode,status,contract_version)"
            + " VALUES (?,?,1,?,'A4','PORTRAIT','MASTER_FORM','DRAFT',1)",
        UUID.randomUUID(),
        template,
        file);

    assertThat(catalog.hasVersions(PeriodicFormBlankContract.DOCUMENT_TYPE)).isTrue();
    assertThat(catalog.findEffective(PeriodicFormBlankContract.DOCUMENT_TYPE, Instant.now()))
        .isEmpty();
    assertThat(seeder.seed()).isZero();
  }

  @Test
  void rendersTheSeededStarterWithRealDataIntoTheWordFileSentToTheConverter() throws Exception {
    seeder.seed();
    var converted = new AtomicReference<byte[]>();
    PdfConverter converter =
        docx -> {
          converted.set(docx);
          return "%PDF-1.7".getBytes(StandardCharsets.US_ASCII);
        };
    var renderer =
        new DocumentRenderingService(
            catalog,
            storage,
            engine,
            converter,
            new com.ngockhanh.clinic.document.infrastructure.rendering.Code128BarcodeGenerator(),
            Clock.systemUTC());

    var rendered =
        renderer.render(
            PeriodicFormBlankContract.DOCUMENT_TYPE,
            Map.of(
                "participant.fullNameUpper", "NGUYỄN VĂN AN",
                "participant.code", "KSK-1",
                "participant.barcode", "KSK-1"));

    assertThat(rendered.templateVersionNo()).isEqualTo(1);
    assertThat(documentXml(converted.get()))
        .contains("NGUYỄN VĂN AN")
        .contains("Số: KSK-1")
        .doesNotContain("{{");
    assertThat(partNames(converted.get())).contains("word/media/nkc-image-1.png");
    assertThatThrownBy(() -> renderer.render("OTHER_TYPE", Map.of()))
        .isInstanceOf(com.ngockhanh.clinic.shared.exception.ResourceNotFoundException.class);
  }

  private static java.util.List<String> partNames(byte[] docx) throws Exception {
    var names = new java.util.ArrayList<String>();
    try (var zip = new ZipInputStream(new ByteArrayInputStream(docx))) {
      for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry())
        names.add(entry.getName());
    }
    return names;
  }

  private static String documentXml(byte[] docx) throws Exception {
    try (var zip = new ZipInputStream(new ByteArrayInputStream(docx))) {
      for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry())
        if (entry.getName().equals("word/document.xml"))
          return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
    }
    throw new IllegalStateException("word/document.xml is missing");
  }
}

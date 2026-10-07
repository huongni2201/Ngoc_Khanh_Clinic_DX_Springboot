package com.ngockhanh.clinic.catalog.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.ngockhanh.clinic.catalog.application.query.ServiceCatalogQuery;
import com.ngockhanh.clinic.catalog.infrastructure.persistence.repository.MyBatisServiceCatalogQuery;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Runs the catalog list query against a real PostgreSQL database (skipped without Docker). */
// Close cached connections when this class finishes; its containers are class-scoped.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
    classes = MyBatisServiceCatalogQueryIntegrationTest.CatalogTestConfiguration.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MyBatisServiceCatalogQueryIntegrationTest {
  @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
  @org.springframework.boot.autoconfigure.EnableAutoConfiguration
  @org.mybatis.spring.annotation.MapperScan(
      basePackages = "com.ngockhanh.clinic.catalog.infrastructure.persistence.mapper")
  @org.springframework.context.annotation.Import(MyBatisServiceCatalogQuery.class)
  static class CatalogTestConfiguration {}

  @Container static final PostgreSQLContainer DB = new PostgreSQLContainer("postgres:18-alpine");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", DB::getJdbcUrl);
    r.add("spring.datasource.username", DB::getUsername);
    r.add("spring.datasource.password", DB::getPassword);
  }

  @Autowired JdbcTemplate jdbc;
  @Autowired ServiceCatalogQuery catalog;

  @BeforeEach
  void fixture() {
    jdbc.execute("TRUNCATE public.services,public.departments CASCADE");
    UUID department = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO public.departments(id,code,name,department_type) VALUES (?,'D1','Exam','CLINICAL')",
        department);
    insert(department, "S1", "Khám tổng quát", "CONSULTATION", "150000", true);
    insert(department, "S2", "Xét nghiệm máu", "LAB", "90000", true);
    insert(department, "S3", "Siêu âm", "ULTRASOUND", "180000", true);
    insert(department, "S4", "Dịch vụ ngừng dùng", "OTHER", "10000", false);
  }

  private void insert(
      UUID department, String code, String name, String type, String price, boolean active) {
    jdbc.update(
        "INSERT INTO public.services(id,code,name,service_type,performing_department_id,unit_price,active) VALUES (?,?,?,?,?,?::numeric,?)",
        UUID.randomUUID(),
        code,
        name,
        type,
        department,
        price,
        active);
  }

  @Test
  void listsOnlyActiveServicesWithTheirTypeAndPrice() {
    assertThat(catalog.countActive(null)).isEqualTo(3);
    var items = catalog.findActivePage(null, 0, 10, "code", "ASC");

    assertThat(items).extracting(item -> item.code()).containsExactly("S1", "S2", "S3");
    assertThat(items.getFirst().name()).isEqualTo("Khám tổng quát");
    assertThat(items.getFirst().serviceType()).isEqualTo("CONSULTATION");
    assertThat(items.getFirst().unitPrice()).isEqualByComparingTo("150000");
    assertThat(items).allMatch(item -> item.active());
  }

  @Test
  void sortsPagesAndSearchesWithLiteralWildcards() {
    assertThat(catalog.findActivePage(null, 0, 10, "unitPrice", "DESC"))
        .extracting(item -> item.code())
        .containsExactly("S3", "S1", "S2");
    assertThat(catalog.findActivePage(null, 1, 1, "code", "ASC"))
        .extracting(item -> item.code())
        .containsExactly("S2");
    assertThat(catalog.findActivePage("%s2%", 0, 10, "code", "ASC"))
        .extracting(item -> item.code())
        .containsExactly("S2");
    assertThat(catalog.countActive("%\\%%")).isZero();
    assertThat(catalog.countActive("%ngừng%")).isZero();
  }

  @Test
  void findByIdsStillReturnsInactiveServicesForDisplayNames() {
    var inactive =
        jdbc.queryForObject("SELECT id FROM public.services WHERE code='S4'", UUID.class);

    assertThat(catalog.findByIds(java.util.Set.of(inactive)))
        .singleElement()
        .satisfies(
            service -> {
              assertThat(service.code()).isEqualTo("S4");
              assertThat(service.active()).isFalse();
            });
  }
}

package com.ngockhanh.clinic.document.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.ngockhanh.clinic.document.application.contract.DocumentValuesSource;
import com.ngockhanh.clinic.document.application.port.NewFile;
import com.ngockhanh.clinic.document.application.port.RenderJobStore;
import com.ngockhanh.clinic.document.infrastructure.persistence.repository.MyBatisRenderJobStore;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import tools.jackson.databind.json.JsonMapper;

/**
 * Runs the print job queue against a real PostgreSQL database (skipped without Docker): claiming,
 * leases, the lease token guard, parts and their files, and expiry.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
    classes = RenderJobStoreIntegrationTest.StoreTestConfiguration.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RenderJobStoreIntegrationTest {
  @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
  @org.springframework.boot.autoconfigure.EnableAutoConfiguration
  @org.mybatis.spring.annotation.MapperScan(
      basePackages = "com.ngockhanh.clinic.document.infrastructure.persistence.mapper")
  @org.springframework.context.annotation.Import(MyBatisRenderJobStore.class)
  static class StoreTestConfiguration {
    @Bean
    JsonMapper jsonMapper() {
      return JsonMapper.builder().build();
    }
  }

  @Container static final PostgreSQLContainer DB = new PostgreSQLContainer("postgres:18-alpine");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", DB::getJdbcUrl);
    r.add("spring.datasource.username", DB::getUsername);
    r.add("spring.datasource.password", DB::getPassword);
  }

  private static final Instant START = Instant.parse("2026-10-13T08:00:00Z");
  private static final Duration LEASE = Duration.ofMinutes(5);

  @Autowired JdbcTemplate jdbc;
  @Autowired RenderJobStore store;
  private UUID account;

  @BeforeEach
  void freshJobsAndOneStaffAccount() {
    jdbc.execute(
        "TRUNCATE public.document_render_job_outputs, public.document_render_jobs, public.files,"
            + " public.accounts, public.staff_members CASCADE");
    UUID staff = UUID.randomUUID();
    account = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO public.staff_members(id,staff_code,full_name,status) VALUES (?,'S1','Test staff','ACTIVE')",
        staff);
    jdbc.update(
        "INSERT INTO public.accounts(id,account_type,role_id,username,password_hash,staff_member_id,status)"
            + " VALUES (?,'STAFF',(SELECT id FROM public.roles WHERE code = 'CLINIC_MANAGER'),'job-tester','x',?,'ACTIVE')",
        account,
        staff);
  }

  private UUID queue(List<UUID> ids, Instant at) {
    UUID id = UUID.randomUUID();
    assertThat(store.insert(id, "T", account, UUID.randomUUID(), Map.of("batchId", "b1"), ids, at)).isTrue();
    return id;
  }

  private static List<UUID> ids(int n) {
    return java.util.stream.IntStream.range(0, n).mapToObj(i -> UUID.randomUUID()).toList();
  }

  @Test
  void storesAJobWithItsIdentifiersInOrderAndRefusesARepeatedKey() {
    var ids = ids(3);
    UUID key = UUID.randomUUID();
    UUID id = UUID.randomUUID();

    assertThat(store.insert(id, "T", account, key, Map.of("batchId", "b1"), ids, START)).isTrue();
    assertThat(store.insert(UUID.randomUUID(), "T", account, key, Map.of(), ids, START)).isFalse();

    var job = store.findById(id).orElseThrow();
    assertThat(job.recordIds()).containsExactlyElementsOf(ids);
    assertThat(job.context()).isEqualTo(Map.of("batchId", "b1"));
    assertThat(job.status()).isEqualTo("QUEUED");
    assertThat(job.totalCount()).isEqualTo(3);
    assertThat(store.findByIdempotencyKey(account, key).orElseThrow().id()).isEqualTo(id);
    assertThat(store.countActive(account)).isEqualTo(1);
  }

  @Test
  void claimsTheOldestJobOnceAndGivesItBackOnlyWhenItsLeaseRanOut() {
    var older = queue(ids(1), START);
    var newer = queue(ids(1), START.plusSeconds(1));

    var first = store.claimNext(START, START.plus(LEASE), 3).orElseThrow();
    var second = store.claimNext(START, START.plus(LEASE), 3).orElseThrow();

    assertThat(first.id()).isEqualTo(older);
    assertThat(first.attempt()).isEqualTo(1);
    assertThat(second.id()).isEqualTo(newer);
    assertThat(store.claimNext(START.plusSeconds(10), START.plus(LEASE), 3)).isEmpty();

    var again = store.claimNext(START.plus(Duration.ofMinutes(6)), START.plus(Duration.ofMinutes(11)), 3);
    assertThat(again).isPresent();
    assertThat(again.get().attempt()).isEqualTo(2);
    assertThat(again.get().lockToken()).isNotEqualTo(first.lockToken());
  }

  @Test
  void aWorkerThatLostItsLeaseCannotWriteTheJobAnymore() {
    var id = queue(ids(2), START);
    var old = store.claimNext(START, START.plus(LEASE), 3).orElseThrow();
    var current = store.claimNext(START.plus(Duration.ofMinutes(6)), START.plus(Duration.ofMinutes(11)), 3).orElseThrow();

    assertThat(store.progress(id, old.lockToken(), 1, List.of(), START.plus(Duration.ofMinutes(20)))).isFalse();
    assertThat(store.recordTemplateVersion(id, old.lockToken(), null)).isFalse();
    assertThat(store.finish(id, old.lockToken(), true, null, 2, List.of(), START, START.plus(Duration.ofHours(24)))).isFalse();
    assertThat(store.progress(id, current.lockToken(), 1, List.of(), START.plus(Duration.ofMinutes(20)))).isTrue();
  }

  @Test
  void keepsProgressSkippedRecordsPartsAndTheirFilesUntilTheJobExpires() {
    var ids = ids(3);
    var id = queue(ids, START);
    var claimed = store.claimNext(START, START.plus(LEASE), 3).orElseThrow();
    var skipped = List.of(new DocumentValuesSource.Skipped(ids.get(1), "CANCELLED"));
    assertThat(store.progress(id, claimed.lockToken(), 2, skipped, START.plus(LEASE))).isTrue();
    byte[] checksum = new byte[32];
    assertThat(
            store.addPart(
                id,
                claimed.lockToken(),
                1,
                2,
                new NewFile(UUID.randomUUID(), "LOCAL", "render-jobs/2026/10/" + id + "-1.pdf", "part-1.pdf", "application/pdf", 1234, checksum),
                START.plus(LEASE)))
        .isTrue();
    Instant expires = START.plus(Duration.ofHours(24));
    assertThat(store.finish(id, claimed.lockToken(), true, null, 3, skipped, START, expires)).isTrue();

    var job = store.findById(id).orElseThrow();
    assertThat(job.status()).isEqualTo("SUCCEEDED");
    assertThat(job.processedCount()).isEqualTo(3);
    assertThat(job.skipped()).containsExactly(skipped.get(0));
    assertThat(job.expiresAt()).isEqualTo(expires);
    assertThat(store.countActive(account)).isZero();
    var parts = store.findParts(id);
    assertThat(parts).hasSize(1);
    assertThat(parts.get(0).recordCount()).isEqualTo(2);
    assertThat(parts.get(0).sizeBytes()).isEqualTo(1234);
    assertThat(parts.get(0).storageProvider()).isEqualTo("LOCAL");

    assertThat(store.findExpired(expires.minusSeconds(1), 10)).isEmpty();
    assertThat(store.findExpired(expires, 10)).containsExactly(id);
    store.deleteParts(id);
    store.markExpired(id);
    assertThat(store.findParts(id)).isEmpty();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM public.files", Integer.class)).isZero();
    assertThat(store.findById(id).orElseThrow().status()).isEqualTo("EXPIRED");
    assertThat(store.findExpired(expires.plusSeconds(1), 10)).isEmpty();
  }

  @Test
  void failsRunningJobsThatRanOutOfAttempts() {
    var id = queue(ids(1), START);
    for (int i = 0; i < 3; i++)
      store.claimNext(START.plus(Duration.ofMinutes(6L * i)), START.plus(Duration.ofMinutes(6L * i + 5)), 3).orElseThrow();

    assertThat(store.claimNext(START.plus(Duration.ofMinutes(20)), START.plus(Duration.ofMinutes(25)), 3)).isEmpty();
    assertThat(store.failExhausted(START.plus(Duration.ofMinutes(20)), 3, START.plus(Duration.ofHours(24)))).isEqualTo(1);

    var job = store.findById(id).orElseThrow();
    assertThat(job.status()).isEqualTo("FAILED");
    assertThat(job.errorCode()).isEqualTo("ATTEMPTS_EXHAUSTED");
  }
}

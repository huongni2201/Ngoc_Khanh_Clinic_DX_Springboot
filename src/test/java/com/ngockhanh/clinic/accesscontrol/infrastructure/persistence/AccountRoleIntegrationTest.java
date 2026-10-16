package com.ngockhanh.clinic.accesscontrol.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ngockhanh.clinic.accesscontrol.application.command.ChangeAccountRoleCommand;
import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.accesscontrol.application.response.AccountRoleResponse;
import com.ngockhanh.clinic.accesscontrol.application.usecase.ChangeAccountRoleUseCase;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.FieldConflictException;
import com.ngockhanh.clinic.shared.exception.UnprocessableEntityException;
import com.ngockhanh.clinic.shared.infrastructure.time.ClockConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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

/**
 * Proves on PostgreSQL 18 that changing the role of an account replaces {@code accounts.role_id}
 * with a version check, writes its audit event and never leaves the system without an account able
 * to administer it, even when two Administrators lower each other at the same time (SRS UC-010).
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = AccountRoleIntegrationTest.AccountRoleTestConfiguration.class)
class AccountRoleIntegrationTest {
  @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
  @org.springframework.boot.autoconfigure.EnableAutoConfiguration
  @org.mybatis.spring.annotation.MapperScan(
      basePackages = {
        "com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.mapper",
        "com.ngockhanh.clinic.audit.infrastructure.persistence.mapper"
      })
  @org.springframework.context.annotation.Import({
    ClockConfiguration.class,
    ChangeAccountRoleUseCase.class,
    com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.repository
        .MyBatisStaffAccountRepository.class,
    com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.repository.MyBatisRoleRepository
        .class,
    com.ngockhanh.clinic.audit.infrastructure.persistence.repository.MyBatisAuditWriter.class
  })
  static class AccountRoleTestConfiguration {}

  @Container static final PostgreSQLContainer DB = new PostgreSQLContainer("postgres:18-alpine");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", DB::getJdbcUrl);
    r.add("spring.datasource.username", DB::getUsername);
    r.add("spring.datasource.password", DB::getPassword);
  }

  @Autowired ChangeAccountRoleUseCase changeRole;
  @Autowired JdbcTemplate jdbc;

  private UUID administrator;
  private UserPrincipal actor;

  @BeforeEach
  void oneAdministratorAccount() {
    jdbc.execute("TRUNCATE public.audit_events, public.accounts, public.staff_members CASCADE");
    jdbc.update("UPDATE public.roles SET active = true");
    administrator = account("NV-0001", "admin1", "ADMINISTRATOR", "ACTIVE", "ACTIVE");
    actor = actorFor(administrator);
  }

  @Test
  void replacesTheRoleOnceAndAuditsIt() {
    UUID receptionist = account("NV-0002", "lan1", "RECEPTIONIST", "ACTIVE", "ACTIVE");

    AccountRoleResponse changed = change(receptionist, "NURSE", 0L, actor);

    assertThat(changed.rowVersion()).isEqualTo(1L);
    assertThat(roleOf(receptionist)).isEqualTo("NURSE");
    assertThat(
            jdbc.queryForObject(
                "SELECT metadata -> 'before' ->> 'roleCode' || ' > ' || (metadata -> 'after' ->> 'roleCode')"
                    + " FROM public.audit_events WHERE action = 'CHANGE_ACCOUNT_ROLE' AND resource_id = ?",
                String.class,
                receptionist))
        .isEqualTo("RECEPTIONIST > NURSE");
    assertThatThrownBy(() -> change(receptionist, "CLINIC_MANAGER", 0L, actor))
        .as("a second change based on the same read")
        .isInstanceOf(ConcurrentUpdateException.class);
    assertThat(roleOf(receptionist)).isEqualTo("NURSE");
  }

  @Test
  void rejectsARoleDeactivatedMeanwhile() {
    UUID receptionist = account("NV-0002", "lan1", "RECEPTIONIST", "ACTIVE", "ACTIVE");
    jdbc.update("UPDATE public.roles SET active = false WHERE code = 'NURSE'");

    assertThatThrownBy(() -> change(receptionist, "NURSE", 0L, actor))
        .isInstanceOf(UnprocessableEntityException.class);
    assertThat(roleOf(receptionist)).isEqualTo("RECEPTIONIST");
  }

  @Test
  void keepsTheLastAccountAbleToAdministerTheSystem() {
    account("NV-0002", "admin2", "ADMINISTRATOR", "LOCKED", "ACTIVE");
    account("NV-0003", "admin3", "ADMINISTRATOR", "ACTIVE", "SUSPENDED");

    assertThatThrownBy(() -> change(administrator, "NURSE", 0L, actor))
        .as("locked accounts and suspended staff cannot administer")
        .isInstanceOfSatisfying(
            FieldConflictException.class,
            conflict -> assertThat(conflict.code()).isEqualTo("LastAdministrator"));
    assertThat(roleOf(administrator)).isEqualTo("ADMINISTRATOR");

    account("NV-0004", "admin4", "ADMINISTRATOR", "ACTIVE", "ACTIVE");
    change(administrator, "NURSE", 0L, actor);
    assertThat(roleOf(administrator)).isEqualTo("NURSE");
  }

  @Test
  void twoAdministratorsLoweringEachOtherLeaveOneOfThem() throws Exception {
    UUID second = account("NV-0002", "admin2", "ADMINISTRATOR", "ACTIVE", "ACTIVE");
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    List<Future<Boolean>> results = new ArrayList<>();
    try {
      for (UUID target : List.of(administrator, second)) {
        UserPrincipal other = actorFor(target.equals(administrator) ? second : administrator);
        results.add(
            pool.submit(
                () -> {
                  start.await();
                  try {
                    change(target, "NURSE", 0L, other);
                    return true;
                  } catch (FieldConflictException rejected) {
                    return false;
                  }
                }));
      }
      start.countDown();
      List<Boolean> outcomes = new ArrayList<>();
      for (Future<Boolean> result : results) outcomes.add(result.get());
      assertThat(outcomes).containsExactlyInAnyOrder(true, false);
    } finally {
      pool.shutdownNow();
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM public.accounts a JOIN public.roles r ON r.id = a.role_id"
                    + " WHERE r.code = 'ADMINISTRATOR'",
                Long.class))
        .isEqualTo(1L);
  }

  private AccountRoleResponse change(
      UUID accountId, String roleCode, Long rowVersion, UserPrincipal caller) {
    return changeRole.execute(
        accountId,
        ChangeAccountRoleCommand.builder().roleCode(roleCode).rowVersion(rowVersion).build(),
        caller);
  }

  private UUID account(
      String staffCode, String username, String roleCode, String status, String staffStatus) {
    UUID staffId = UUID.randomUUID();
    UUID accountId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO public.staff_members (id, staff_code, full_name, status) VALUES (?, ?, ?, ?)",
        staffId,
        staffCode,
        username,
        staffStatus);
    jdbc.update(
        "INSERT INTO public.accounts (id, account_type, role_id, username, password_hash, staff_member_id, status)"
            + " SELECT ?, 'STAFF', id, ?, '{bcrypt}x', ?, ? FROM public.roles WHERE code = ?",
        accountId,
        username,
        staffId,
        status,
        roleCode);
    return accountId;
  }

  private String roleOf(UUID accountId) {
    return jdbc.queryForObject(
        "SELECT r.code FROM public.accounts a JOIN public.roles r ON r.id = a.role_id WHERE a.id = ?",
        String.class,
        accountId);
  }

  private static UserPrincipal actorFor(UUID accountId) {
    return UserPrincipal.builder()
        .userId(accountId)
        .staffId(UUID.randomUUID())
        .username("admin")
        .principalType("STAFF")
        .roleAssignments(
            List.of(
                new UserPrincipal.Assignment(
                    UUID.randomUUID(),
                    "ADMINISTRATOR",
                    List.of(ChangeAccountRoleUseCase.ASSIGN_PERMISSION))))
        .build();
  }
}

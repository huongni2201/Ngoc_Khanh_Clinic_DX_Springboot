package com.ngockhanh.clinic.accesscontrol.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

import com.ngockhanh.clinic.accesscontrol.application.command.CreateStaffMemberCommand;
import com.ngockhanh.clinic.accesscontrol.application.query.ListStaffMembersQuery;
import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.accesscontrol.application.query.StaffMemberSummary;
import com.ngockhanh.clinic.accesscontrol.application.service.StaffAdministrationPolicy;
import com.ngockhanh.clinic.accesscontrol.application.usecase.CreateStaffMemberUseCase;
import com.ngockhanh.clinic.accesscontrol.application.usecase.ListAssignableRolesUseCase;
import com.ngockhanh.clinic.accesscontrol.application.usecase.ListStaffMembersUseCase;
import com.ngockhanh.clinic.accesscontrol.domain.repository.UserAccountRepository;
import com.ngockhanh.clinic.accesscontrol.infrastructure.security.UserPasswordEncoder;
import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.shared.exception.UnprocessableEntityException;
import com.ngockhanh.clinic.shared.infrastructure.time.ClockConfiguration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Proves on PostgreSQL 18 that creating a staff member allocates codes and usernames, stores a
 * sign-in capable account with its grants, and rolls everything back when the audit fails.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
    classes = StaffMemberProvisioningIntegrationTest.StaffAdministrationTestConfiguration.class)
class StaffMemberProvisioningIntegrationTest {
  @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
  @org.springframework.boot.autoconfigure.EnableAutoConfiguration
  @org.mybatis.spring.annotation.MapperScan(
      basePackages = {
        "com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.mapper",
        "com.ngockhanh.clinic.audit.infrastructure.persistence.mapper"
      })
  @org.springframework.context.annotation.Import({
    ClockConfiguration.class,
    StaffAdministrationPolicy.class,
    CreateStaffMemberUseCase.class,
    ListStaffMembersUseCase.class,
    ListAssignableRolesUseCase.class,
    com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.repository
        .MyBatisStaffMemberRepository.class,
    com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.repository
        .MyBatisStaffAccountRepository.class,
    com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.repository.MyBatisRoleRepository
        .class,
    com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.repository
        .MyBatisStaffMemberDirectory.class,
    com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.repository
        .MyBatisUserAccountRepository.class,
    com.ngockhanh.clinic.audit.infrastructure.persistence.repository.MyBatisAuditWriter.class
  })
  static class StaffAdministrationTestConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() {
      return new UserPasswordEncoder();
    }
  }

  @Container static final PostgreSQLContainer DB = new PostgreSQLContainer("postgres:18-alpine");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", DB::getJdbcUrl);
    r.add("spring.datasource.username", DB::getUsername);
    r.add("spring.datasource.password", DB::getPassword);
  }

  @Autowired CreateStaffMemberUseCase create;
  @Autowired ListStaffMembersUseCase list;
  @Autowired ListAssignableRolesUseCase listRoles;
  @Autowired UserAccountRepository signInAccounts;
  @Autowired PasswordEncoder passwords;
  @Autowired JdbcTemplate jdbc;
  @MockitoSpyBean AuditWriter audit;

  private UserPrincipal admin;

  @BeforeEach
  void onlyTheAdministratorExists() {
    jdbc.execute("TRUNCATE public.audit_events, public.accounts, public.staff_members CASCADE");
    UUID staffId = UUID.randomUUID();
    UUID accountId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO public.staff_members (id, staff_code, full_name, status) VALUES (?, 'ADMIN-1', 'Admin', 'ACTIVE')",
        staffId);
    jdbc.update(
        "INSERT INTO public.accounts (id, account_type, role_id, username, password_hash, staff_member_id, status)"
            + " SELECT ?, 'STAFF', id, 'admin', '{bcrypt}x', ?, 'ACTIVE' FROM public.roles WHERE code = 'ADMINISTRATOR'",
        accountId,
        staffId);
    admin =
        UserPrincipal.builder()
            .userId(accountId)
            .staffId(staffId)
            .username("admin")
            .principalType("STAFF")
            .roleAssignments(
                List.of(
                    new UserPrincipal.Assignment(
                        UUID.randomUUID(),
                        "ADMINISTRATOR",
                        List.of(
                            StaffAdministrationPolicy.VIEW_PERMISSION,
                            StaffAdministrationPolicy.ROLE_VIEW_PERMISSION,
                            StaffAdministrationPolicy.CREATE_PERMISSION,
                            StaffAdministrationPolicy.ROLE_ASSIGN_PERMISSION))))
            .build();
  }

  @Test
  void allocatesNextCodesAndUsernamesAndStoresASignInCapableAccount() {
    StaffMemberSummary first = create.execute(command("Nguyễn Văn An", "RECEPTIONIST"), admin);
    StaffMemberSummary second =
        create.execute(command("Nguyễn Văn An", "GENERAL_PRACTITIONER"), admin);
    StaffMemberSummary other = create.execute(command("Đặng Thị Đào", "CLINIC_MANAGER"), admin);

    assertThat(List.of(first.staffCode(), second.staffCode(), other.staffCode()))
        .containsExactly("NV-0001", "NV-0002", "NV-0003");
    assertThat(List.of(first.account().username(), second.account().username(), other.account().username()))
        .containsExactly("annv1", "annv2", "daodt1");

    var account = signInAccounts.findByUsername("annv2").orElseThrow();
    assertThat(account.eligible()).isTrue();
    assertThat(passwords.matches("Tam@12345", account.passwordHash())).isTrue();
    assertThat(account.roles())
        .extracting(role -> role.roleCode())
        .containsExactly("GENERAL_PRACTITIONER");
    assertThat(
            jdbc.queryForList(
                "SELECT r.code FROM public.accounts a JOIN public.roles r ON r.id = a.role_id WHERE a.username = 'annv2'",
                String.class))
        .containsExactly("GENERAL_PRACTITIONER");
    assertThat(
            jdbc.queryForList(
                "SELECT action FROM public.audit_events ORDER BY occurred_at, action", String.class))
        .containsOnly("CREATE_ACCOUNT", "CREATE_STAFF_MEMBER")
        .hasSize(6);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM public.audit_events WHERE metadata::text LIKE '%Tam@12345%' OR metadata::text LIKE '%bcrypt%'",
                Integer.class))
        .isZero();
  }

  @Test
  void listsStaffMembersWithAccountsRolesAndSearch() {
    create.execute(command("Nguyễn Văn An", "GENERAL_PRACTITIONER"), admin);
    create.execute(command("Đặng Thị Đào", "CLINIC_MANAGER"), admin);

    var page =
        list.execute(ListStaffMembersQuery.builder().searchKey("annv").build(), admin);
    assertThat(page.totalElements()).isEqualTo(1);
    var row = page.items().getFirst();
    assertThat(row.fullName()).isEqualTo("Nguyễn Văn An");
    assertThat(row.account().roleCode()).isEqualTo("GENERAL_PRACTITIONER");

    var byName =
        list.execute(
            ListStaffMembersQuery.builder().sortKey("staffCode").sortBy("DESC").build(), admin);
    assertThat(byName.items())
        .extracting(StaffMemberSummary::staffCode)
        .containsExactly("NV-0002", "NV-0001", "ADMIN-1");
    assertThat(listRoles.execute(admin))
        .extracting(role -> role.code())
        .contains("ADMINISTRATOR", "RECEPTIONIST")
        .doesNotContain("PATIENT")
        .isSorted();
  }

  @Test
  void rollsBackTheStaffMemberAndAccountWhenTheAuditFails() {
    doThrow(new IllegalStateException("audit down"))
        .when(audit)
        .record(any(), eq("CREATE_ACCOUNT"), any(), any(), any(), any());

    assertThatThrownBy(() -> create.execute(command("Nguyễn Văn An", "RECEPTIONIST"), admin))
        .isInstanceOf(IllegalStateException.class);

    assertThat(jdbc.queryForObject("SELECT count(*) FROM public.staff_members", Integer.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM public.accounts", Integer.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM public.audit_events", Integer.class)).isZero();
  }

  @Test
  void rejectsThePatientRoleAndAnUnknownRoleWithoutWriting() {
    assertThatThrownBy(() -> create.execute(command("Nguyễn Văn An", "PATIENT"), admin))
        .isInstanceOf(UnprocessableEntityException.class);
    assertThatThrownBy(() -> create.execute(command("Nguyễn Văn An", "NO_SUCH_ROLE"), admin))
        .isInstanceOf(UnprocessableEntityException.class);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM public.staff_members", Integer.class)).isEqualTo(1);
  }

  @Test
  void concurrentCreationsWithTheSameNameGetDistinctCodesAndUsernames() throws Exception {
    Integer callers = 4;
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(callers);
    try {
      Callable<StaffMemberSummary> task =
          () -> {
            start.await();
            return create.execute(command("Nguyễn Văn An", "RECEPTIONIST"), admin);
          };
      List<Future<StaffMemberSummary>> results =
          java.util.stream.Stream.generate(() -> pool.submit(task)).limit(callers).toList();
      start.countDown();
      List<StaffMemberSummary> created = new java.util.ArrayList<>();
      for (Future<StaffMemberSummary> result : results) created.add(result.get());

      assertThat(created)
          .extracting(response -> response.account().username())
          .containsExactlyInAnyOrder("annv1", "annv2", "annv3", "annv4");
      assertThat(created)
          .extracting(StaffMemberSummary::staffCode)
          .containsExactlyInAnyOrder("NV-0001", "NV-0002", "NV-0003", "NV-0004");
    } finally {
      pool.shutdownNow();
    }
  }

  private static CreateStaffMemberCommand command(String fullName, String roleCode) {
    return CreateStaffMemberCommand.builder()
        .fullName(fullName)
        .initialPassword("Tam@12345")
        .roleCode(roleCode)
        .build();
  }
}

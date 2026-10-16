package com.ngockhanh.clinic.accesscontrol.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

import com.ngockhanh.clinic.accesscontrol.application.command.CreateRoleCommand;
import com.ngockhanh.clinic.accesscontrol.application.command.UpdateRoleCommand;
import com.ngockhanh.clinic.accesscontrol.application.command.UpdateRolePermissionsCommand;
import com.ngockhanh.clinic.accesscontrol.application.query.ListRolesQuery;
import com.ngockhanh.clinic.accesscontrol.application.query.PermissionCatalogItem;
import com.ngockhanh.clinic.accesscontrol.application.query.RoleSummary;
import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.accesscontrol.application.usecase.CreateRoleUseCase;
import com.ngockhanh.clinic.accesscontrol.application.usecase.GetRolePermissionsUseCase;
import com.ngockhanh.clinic.accesscontrol.application.usecase.ListPermissionCatalogUseCase;
import com.ngockhanh.clinic.accesscontrol.application.usecase.ListRolesUseCase;
import com.ngockhanh.clinic.accesscontrol.application.usecase.UpdateRolePermissionsUseCase;
import com.ngockhanh.clinic.accesscontrol.application.usecase.UpdateRoleUseCase;
import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import com.ngockhanh.clinic.shared.exception.ConcurrentUpdateException;
import com.ngockhanh.clinic.shared.exception.FieldConflictException;
import com.ngockhanh.clinic.shared.infrastructure.time.ClockConfiguration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Proves on PostgreSQL 18 that role administration stores roles and grants with the V005 schema:
 * permission copies, usage counts, case-insensitive names, row versions and audit rollback.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
    classes = RoleAdministrationIntegrationTest.RoleAdministrationTestConfiguration.class)
class RoleAdministrationIntegrationTest {
  @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
  @org.springframework.boot.autoconfigure.EnableAutoConfiguration
  @org.mybatis.spring.annotation.MapperScan(
      basePackages = {
        "com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.mapper",
        "com.ngockhanh.clinic.audit.infrastructure.persistence.mapper"
      })
  @org.springframework.context.annotation.Import({
    ClockConfiguration.class,
    ListRolesUseCase.class,
    CreateRoleUseCase.class,
    UpdateRoleUseCase.class,
    ListPermissionCatalogUseCase.class,
    GetRolePermissionsUseCase.class,
    UpdateRolePermissionsUseCase.class,
    com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.repository.MyBatisRoleRepository
        .class,
    com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.repository.MyBatisRoleDirectory
        .class,
    com.ngockhanh.clinic.audit.infrastructure.persistence.repository.MyBatisAuditWriter.class
  })
  static class RoleAdministrationTestConfiguration {}

  private static final String SEEDED_ROLES =
      "('PATIENT','RECEPTIONIST','GENERAL_PRACTITIONER','DIAGNOSTIC_DOCTOR','NURSE','CLINIC_MANAGER','ADMINISTRATOR')";

  @Container static final PostgreSQLContainer DB = new PostgreSQLContainer("postgres:18-alpine");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", DB::getJdbcUrl);
    r.add("spring.datasource.username", DB::getUsername);
    r.add("spring.datasource.password", DB::getPassword);
  }

  @Autowired ListRolesUseCase list;
  @Autowired CreateRoleUseCase create;
  @Autowired UpdateRoleUseCase update;
  @Autowired ListPermissionCatalogUseCase catalog;
  @Autowired GetRolePermissionsUseCase getPermissions;
  @Autowired UpdateRolePermissionsUseCase updatePermissions;
  @Autowired JdbcTemplate jdbc;
  @MockitoSpyBean AuditWriter audit;

  private UserPrincipal admin;

  @BeforeEach
  void onlySeededRolesAndTheAdministratorExist() {
    jdbc.execute("TRUNCATE public.audit_events, public.accounts, public.staff_members CASCADE");
    jdbc.update(
        "DELETE FROM public.role_permissions WHERE role_id IN (SELECT id FROM public.roles WHERE code NOT IN "
            + SEEDED_ROLES
            + ")");
    jdbc.update("DELETE FROM public.roles WHERE code NOT IN " + SEEDED_ROLES);
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
                            ListRolesUseCase.MANAGE_PERMISSION,
                            CreateRoleUseCase.CREATE_PERMISSION,
                            UpdateRoleUseCase.UPDATE_PERMISSION,
                            ListPermissionCatalogUseCase.VIEW_PERMISSION,
                            GetRolePermissionsUseCase.VIEW_PERMISSION,
                            UpdateRolePermissionsUseCase.ASSIGN_PERMISSION))))
            .build();
  }

  @Test
  void createsARoleWithCopiedPermissionsAndListsItWithCounts() {
    Long receptionistGrants =
        jdbc.queryForObject(
            "SELECT count(*) FROM public.role_permissions rp JOIN public.roles r ON r.id = rp.role_id WHERE r.code = 'RECEPTIONIST'",
            Long.class);
    UUID receptionist =
        jdbc.queryForObject("SELECT id FROM public.roles WHERE code = 'RECEPTIONIST'", UUID.class);

    RoleSummary created =
        create.execute(
            CreateRoleCommand.builder()
                .code("SENIOR_RECEPTIONIST")
                .name("Senior receptionist")
                .copyFromRoleId(receptionist)
                .build(),
            admin);

    assertThat(created.permissionCount()).isEqualTo(receptionistGrants).isPositive();
    assertThat(created.userCount()).isZero();
    assertThat(created.description()).isNull();
    assertThat(created.rowVersion()).isZero();
    var found = list.execute(ListRolesQuery.builder().searchKey("senior").build(), admin);
    assertThat(found.items()).extracting(RoleSummary::code).containsExactly("SENIOR_RECEPTIONIST");
    assertThat(
            list.execute(ListRolesQuery.builder().searchKey("SENIOR_RECEP").build(), admin).items())
        .as("the search matches the name only, not the code")
        .isEmpty();
    var administrators =
        list.execute(
            ListRolesQuery.builder().searchKey("ADMINISTRATOR").status("ACTIVE").build(), admin);
    assertThat(administrators.items())
        .singleElement()
        .extracting(RoleSummary::userCount)
        .isEqualTo(1L);
    assertThat(list.execute(ListRolesQuery.builder().status("INACTIVE").build(), admin).items())
        .isEmpty();
    assertThat(jdbc.queryForList("SELECT action FROM public.audit_events", String.class))
        .containsExactly("CREATE_ROLE");
  }

  @Test
  void rejectsADuplicateNameIgnoringCaseAndADuplicateCode() {
    assertThatThrownBy(
            () ->
                create.execute(
                    CreateRoleCommand.builder().code("FRONT_DESK").name("RECEPTIONIST").build(),
                    admin))
        .isInstanceOfSatisfying(
            FieldConflictException.class,
            conflict -> assertThat(conflict.field()).isEqualTo("name"));
    assertThatThrownBy(
            () ->
                create.execute(
                    CreateRoleCommand.builder().code("NURSE").name("Another nurse").build(), admin))
        .isInstanceOfSatisfying(
            FieldConflictException.class,
            conflict -> assertThat(conflict.field()).isEqualTo("code"));
    assertThat(jdbc.queryForObject("SELECT count(*) FROM public.roles", Integer.class))
        .isEqualTo(7);
  }

  @Test
  void updatesRolesAndPermissionsOnlyFromTheCurrentRowVersion() {
    RoleSummary role =
        create.execute(
            CreateRoleCommand.builder().code("LAB_TECH").name("Lab tech").build(), admin);

    RoleSummary renamed =
        update.execute(
            role.id(),
            UpdateRoleCommand.builder()
                .name("Lab technician")
                .description("Runs lab tests")
                .active(true)
                .rowVersion(0L)
                .build(),
            admin);
    assertThat(renamed.rowVersion()).isEqualTo(1L);
    assertThat(renamed.name()).isEqualTo("Lab technician");
    assertThatThrownBy(
            () ->
                update.execute(
                    role.id(),
                    UpdateRoleCommand.builder().name("Stale").active(true).rowVersion(0L).build(),
                    admin))
        .isInstanceOf(ConcurrentUpdateException.class);

    var saved =
        updatePermissions.execute(
            role.id(),
            UpdateRolePermissionsCommand.builder()
                .rowVersion(1L)
                .grant(List.of("PATIENT_VIEW", "PAYMENT_REFUND"))
                .build(),
            admin);
    assertThat(saved.rowVersion()).isEqualTo(2L);
    assertThat(saved.permissionCodes()).containsExactly("PATIENT_VIEW", "PAYMENT_REFUND");
    var removed =
        updatePermissions.execute(
            role.id(),
            UpdateRolePermissionsCommand.builder()
                .rowVersion(2L)
                .revoke(List.of("PAYMENT_REFUND"))
                .build(),
            admin);
    assertThat(getPermissions.execute(role.id(), admin).permissionCodes())
        .containsExactly("PATIENT_VIEW");
    assertThat(removed.rowVersion()).isEqualTo(3L);
    assertThatThrownBy(
            () ->
                updatePermissions.execute(
                    role.id(),
                    UpdateRolePermissionsCommand.builder()
                        .rowVersion(2L)
                        .grant(List.of("PAYMENT_VIEW"))
                        .build(),
                    admin))
        .isInstanceOf(ConcurrentUpdateException.class);
    assertThat(
            jdbc.queryForList(
                "SELECT action FROM public.audit_events ORDER BY occurred_at, action",
                String.class))
        .containsExactlyInAnyOrder(
            "CREATE_ROLE", "UPDATE_ROLE", "UPDATE_ROLE_PERMISSIONS", "UPDATE_ROLE_PERMISSIONS");
  }

  @Test
  void rollsBackGrantsAndVersionWhenTheAuditFails() {
    RoleSummary role =
        create.execute(
            CreateRoleCommand.builder().code("LAB_TECH").name("Lab tech").build(), admin);
    doThrow(new IllegalStateException("audit down"))
        .when(audit)
        .record(any(), eq("UPDATE_ROLE_PERMISSIONS"), any(), any(), any(), any());

    assertThatThrownBy(
            () ->
                updatePermissions.execute(
                    role.id(),
                    UpdateRolePermissionsCommand.builder()
                        .rowVersion(0L)
                        .grant(List.of("PATIENT_VIEW"))
                        .build(),
                    admin))
        .isInstanceOf(IllegalStateException.class);

    var unchanged = getPermissions.execute(role.id(), admin);
    assertThat(unchanged.permissionCodes()).isEmpty();
    assertThat(unchanged.rowVersion()).isZero();
  }

  @Test
  void exposesTheCatalogueWithModulesUseCasesAndSensitivity() {
    List<PermissionCatalogItem> permissions = catalog.execute(admin);

    assertThat(permissions).allSatisfy(item -> assertThat(item.module()).isNotBlank());
    assertThat(permissions)
        .filteredOn(item -> item.code().equals("PAYMENT_VIEW"))
        .singleElement()
        .satisfies(
            item -> {
              assertThat(item.module()).isEqualTo("Invoice and Payment");
              assertThat(item.useCases()).containsExactly("UC-041", "UC-042");
              assertThat(item.sensitive()).isFalse();
            });
    assertThat(permissions)
        .filteredOn(item -> item.code().equals("PAYMENT_REFUND"))
        .singleElement()
        .extracting(PermissionCatalogItem::sensitive)
        .isEqualTo(true);
    assertThat(permissions)
        .filteredOn(item -> item.code().equals("PARTICIPANT_CREATE"))
        .singleElement()
        .extracting(PermissionCatalogItem::module)
        .isEqualTo("Participant");
  }
}

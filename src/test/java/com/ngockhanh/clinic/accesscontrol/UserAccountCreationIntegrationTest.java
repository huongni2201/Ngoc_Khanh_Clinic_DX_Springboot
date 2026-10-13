package com.ngockhanh.clinic.accesscontrol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.ngockhanh.clinic.accesscontrol.application.usecase.CreateUserAccountUseCase;
import com.ngockhanh.clinic.audit.application.port.out.AuditWriter;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * UC-005 Create User Account end to end: HTTP, permission filter, PostgreSQL 18 constraints and
 * triggers, audit in the same transaction, and sign-in with the new account.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
    properties = {
      "clinic.auth.cookie-secure=false",
      "clinic.auth.allowed-origins=http://localhost:3000"
    })
@AutoConfigureMockMvc
class UserAccountCreationIntegrationTest {
  private static final String COOKIE = "NKC_SESSION";
  private static final String ORIGIN = "http://localhost:3000";
  private static final String ADMIN_PASSWORD = "Mật khẩu-01";
  private static final String TEMPORARY_PASSWORD = "Tam#2026x";

  @Container static final PostgreSQLContainer DB = new PostgreSQLContainer("postgres:18-alpine");

  @Container
  static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", DB::getJdbcUrl);
    registry.add("spring.datasource.username", DB::getUsername);
    registry.add("spring.datasource.password", DB::getPassword);
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
  }

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired PasswordEncoder passwords;
  @MockitoSpyBean AuditWriter audit;

  UUID adminId;
  Cookie admin;
  String suffix;

  @BeforeEach
  void signedInAdministrator() throws Exception {
    suffix = UUID.randomUUID().toString().substring(0, 8);
    UUID staffId = UUID.randomUUID();
    adminId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO staff_members(id,staff_code,full_name,status) VALUES (?,?,?,'ACTIVE')",
        staffId,
        "ADM-" + suffix,
        "Synthetic administrator");
    jdbc.update(
        "INSERT INTO accounts(id,account_type,username,password_hash,staff_member_id,status)"
            + " VALUES (?,'STAFF',?,?,?,'ACTIVE')",
        adminId,
        "admin-" + suffix,
        passwords.encode(ADMIN_PASSWORD),
        staffId);
    jdbc.update(
        "INSERT INTO account_roles(account_id,role_id,granted_by)"
            + " SELECT ?, id, ? FROM roles WHERE code = 'ADMINISTRATOR'",
        adminId,
        adminId);
    admin = signIn("admin-" + suffix, ADMIN_PASSWORD);
  }

  @AfterEach
  void restoreAudit() {
    reset(audit);
  }

  private Cookie signIn(String username, String password) throws Exception {
    return mvc.perform(
            post("/api/v1/auth/login")
                .header("Origin", ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getCookie(COOKIE);
  }

  private UUID staffMember(String phone, String status) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO staff_members(id,staff_code,full_name,phone,email,primary_department_id,status)"
            + " VALUES (?,?,?,?,?,(SELECT id FROM departments WHERE code='KHAM_BENH'),?)",
        id,
        "NV-" + suffix + "-" + id.toString().substring(0, 4),
        "Nguyễn Văn " + suffix,
        phone,
        "staff-" + suffix + "@example.invalid",
        status);
    return id;
  }

  private UUID patient(String phone) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO patients(id,patient_code,full_name,date_of_birth,sex,identification_number,phone,status)"
            + " VALUES (?,?,?,DATE '1988-03-14','FEMALE',?,?,'ACTIVE')",
        id,
        "BN-" + suffix,
        "Lê Thị " + suffix,
        "ID-" + suffix,
        phone);
    return id;
  }

  private UUID role(String code) {
    return jdbc.queryForObject("SELECT id FROM roles WHERE code = ?", UUID.class, code);
  }

  private ResultActions create(String type, UUID ownerId, String username, List<UUID> roles)
      throws Exception {
    String roleIds =
        roles.stream().map(id -> "\"" + id + "\"").reduce((a, b) -> a + "," + b).orElse("");
    return mvc.perform(
        post("/api/v1/user-accounts")
            .header("Origin", ORIGIN)
            .cookie(admin)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"accountType":"%s","ownerId":"%s","username":"%s",
                 "temporaryPassword":"%s","roleIds":[%s]}
                """
                    .formatted(type, ownerId, username, TEMPORARY_PASSWORD, roleIds)));
  }

  @Test
  void createsAStaffAccountThatCanSignInWithTheTemporaryPassword() throws Exception {
    UUID owner = staffMember("0912 345 678", "ACTIVE");
    String username = "nguyen.van." + suffix;

    mvc.perform(
            get("/api/v1/user-accounts/owner-candidates")
                .param("accountType", "STAFF")
                .param("keyword", suffix)
                .cookie(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].ownerId").value(owner.toString()))
        .andExpect(jsonPath("$.data.items[0].departmentName").value("Khoa Khám Bệnh"))
        .andExpect(jsonPath("$.data.items[0].smsPhoneNumber").value("0912345678"));
    mvc.perform(
            get("/api/v1/user-accounts/username-availability")
                .param("username", username)
                .cookie(admin))
        .andExpect(jsonPath("$.data.available").value(true));
    String roles =
        mvc.perform(get("/api/v1/user-accounts/assignable-roles").cookie(admin))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<String> codes = JsonPath.read(roles, "$.data[*].code");
    assertThat(codes).contains("NURSE", "GENERAL_PRACTITIONER").doesNotContain("PATIENT");

    String body =
        create("STAFF", owner, username, List.of(role("NURSE")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.status").value("ACTIVE"))
            .andExpect(jsonPath("$.data.roleCodes[0]").value("NURSE"))
            .andExpect(jsonPath("$.data.smsDelivered").value(false))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(body).doesNotContain(TEMPORARY_PASSWORD);
    UUID accountId = UUID.fromString(JsonPath.read(body, "$.data.accountId"));

    assertThat(
            jdbc.queryForObject(
                "SELECT staff_member_id FROM accounts WHERE id = ? AND status = 'ACTIVE'",
                UUID.class,
                accountId))
        .isEqualTo(owner);
    assertThat(
            jdbc.queryForObject(
                "SELECT granted_by FROM account_roles WHERE account_id = ?", UUID.class, accountId))
        .isEqualTo(adminId);
    String metadata =
        jdbc.queryForObject(
            "SELECT metadata::text FROM audit_events"
                + " WHERE action = 'CREATE_USER_ACCOUNT' AND resource_id = ? AND actor_account_id = ?",
            String.class,
            accountId,
            adminId);
    assertThat(metadata)
        .contains("NURSE")
        .doesNotContain(username)
        .doesNotContain(TEMPORARY_PASSWORD);

    mvc.perform(
            get("/api/v1/user-accounts/owner-candidates")
                .param("accountType", "STAFF")
                .param("keyword", suffix)
                .cookie(admin))
        .andExpect(jsonPath("$.data.items.length()").value(0));
    Cookie created = signIn(username, TEMPORARY_PASSWORD);
    mvc.perform(get("/api/v1/auth/me").cookie(created))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.roleAssignments[0].roleCode").value("NURSE"));
  }

  @Test
  void createsAPatientAccountWithThePatientRole() throws Exception {
    UUID owner = patient("0987111222");

    mvc.perform(
            get("/api/v1/user-accounts/owner-candidates")
                .param("accountType", "PATIENT")
                .param("keyword", "ID-" + suffix)
                .cookie(admin))
        .andExpect(jsonPath("$.data.items[0].ownerId").value(owner.toString()))
        .andExpect(jsonPath("$.data.items[0].dateOfBirth").value("1988-03-14"));
    create("PATIENT", owner, "le.thi." + suffix, List.of())
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.roleCodes[0]").value("PATIENT"));

    assertThat(
            jdbc.queryForObject(
                "SELECT r.code FROM account_roles ar JOIN roles r ON r.id = ar.role_id"
                    + " JOIN accounts a ON a.id = ar.account_id WHERE a.patient_id = ?",
                String.class,
                owner))
        .isEqualTo("PATIENT");
  }

  @Test
  void rejectsConflictsWithTheirContractMessages() throws Exception {
    UUID nurse = role("NURSE");
    UUID owner = staffMember("0912345678", "ACTIVE");
    create("STAFF", owner, "first." + suffix, List.of(nurse)).andExpect(status().isCreated());

    create("STAFF", staffMember("0912345679", "ACTIVE"), "first." + suffix, List.of(nurse))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value(CreateUserAccountUseCase.USERNAME_TAKEN));
    create("STAFF", owner, "second." + suffix, List.of(nurse))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value(CreateUserAccountUseCase.OWNER_LINKED));
    create("STAFF", staffMember("024 3123 4567", "ACTIVE"), "landline." + suffix, List.of(nurse))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value(CreateUserAccountUseCase.OWNER_WITHOUT_MOBILE));
    create("STAFF", staffMember("0912345670", "INACTIVE"), "inactive." + suffix, List.of(nurse))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value(CreateUserAccountUseCase.OWNER_INACTIVE));
    create(
            "STAFF",
            staffMember("0912345671", "ACTIVE"),
            "patientrole." + suffix,
            List.of(role("PATIENT")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value(CreateUserAccountUseCase.ROLE_NOT_ASSIGNABLE));
    create("STAFF", UUID.randomUUID(), "missing." + suffix, List.of(nurse))
        .andExpect(status().isNotFound());
  }

  @Test
  void aSimplePasswordIsAcceptedAndABlankOneIsRejected() throws Exception {
    String username = "simple." + suffix;
    createWithPassword(staffMember("0912345672", "ACTIVE"), username, "abc")
        .andExpect(status().isCreated());
    signIn(username, "abc");

    createWithPassword(staffMember("0912345675", "ACTIVE"), "blank." + suffix, " ")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.data[0].field").value("temporaryPassword"));
  }

  private ResultActions createWithPassword(UUID owner, String username, String password)
      throws Exception {
    return mvc.perform(
        post("/api/v1/user-accounts")
            .header("Origin", ORIGIN)
            .cookie(admin)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"accountType":"STAFF","ownerId":"%s","username":"%s",
                 "temporaryPassword":"%s","roleIds":["%s"]}
                """
                    .formatted(owner, username, password, role("NURSE"))));
  }

  @Test
  void anAuditFailureRollsBackTheAccountAndItsRoles() throws Exception {
    UUID owner = staffMember("0912345673", "ACTIVE");
    doThrow(new IllegalStateException("audit unavailable"))
        .when(audit)
        .record(any(), eq("CREATE_USER_ACCOUNT"), any(), any(), any(), any());

    create("STAFF", owner, "rollback." + suffix, List.of(role("NURSE")))
        .andExpect(status().isInternalServerError());

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM accounts WHERE staff_member_id = ?", Integer.class, owner))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM account_roles ar JOIN accounts a ON a.id = ar.account_id"
                    + " WHERE a.username = ?",
                Integer.class,
                "rollback." + suffix))
        .isZero();
  }

  @Test
  void ownerSearchMatchesLikeWildcardsLiterally() throws Exception {
    staffMember("0912345674", "ACTIVE");

    mvc.perform(
            get("/api/v1/user-accounts/owner-candidates")
                .param("accountType", "STAFF")
                .param("keyword", "%%")
                .cookie(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items.length()").value(0));
  }
}

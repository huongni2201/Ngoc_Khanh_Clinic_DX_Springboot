package com.ngockhanh.clinic.identity;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ngockhanh.clinic.identity.application.port.SessionRevocation;
import com.ngockhanh.clinic.identity.infrastructure.security.UserPasswordEncoder;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class StaffAuthIntegrationTest {
  @Container static final PostgreSQLContainer PG = new PostgreSQLContainer("postgres:18-alpine");

  @Container
  static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", PG::getJdbcUrl);
    r.add("spring.datasource.username", PG::getUsername);
    r.add("spring.datasource.password", PG::getPassword);
    r.add("spring.data.redis.host", REDIS::getHost);
    r.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
  }

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired UserPasswordEncoder passwords;
  @Autowired JsonMapper json;
  @Autowired SessionRevocation revocation;
  @Autowired org.springframework.data.redis.core.StringRedisTemplate redis;
  String username;
  UUID userId, staffId, roleId;

  @BeforeEach
  void staff() {
    redis.delete(redis.keys("nkc:auth:limit:*"));
    userId = UUID.randomUUID();
    staffId = UUID.randomUUID();
    roleId = UUID.randomUUID();
    username = "staff-" + userId;
    jdbc.update(
        "INSERT INTO staff_members(id,staff_code,full_name,status) VALUES(?,?,?,?)",
        staffId,
        staffId.toString().substring(0, 20),
        "Test staff",
        "ACTIVE");
    jdbc.update(
        "INSERT INTO accounts(id,account_type,staff_member_id,status,username,password_hash) VALUES(?,'STAFF',?,'ACTIVE',?,?)",
        userId,
        staffId,
        username,
        passwords.encode("test-password"));
    jdbc.update(
        "INSERT INTO roles(id,code,name,description) VALUES(?,?,?,'Test role')",
        roleId,
        roleId.toString(),
        "Doctor");
    jdbc.update(
        "INSERT INTO account_roles(account_id,role_id,granted_by) VALUES(?,?,?)",
        userId,
        roleId,
        userId);
  }

  record Csrf(Cookie cookie, String header, String token) {}

  Csrf csrf() throws Exception {
    var response =
        mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn().getResponse();
    var data = json.readTree(response.getContentAsString()).get("data");
    return new Csrf(
        response.getCookie("XSRF-TOKEN"),
        data.get("headerName").asText(),
        data.get("token").asText());
  }

  ResultActions login(String name, String password, Cookie... previous) throws Exception {
    var csrf = csrf();
    var request =
        post("/api/v1/auth/login")
            .servletPath("/api/v1/auth/login")
            .contentType("application/json")
            .content(
                json.writeValueAsString(java.util.Map.of("username", name, "password", password)))
            .cookie(csrf.cookie())
            .header(csrf.header(), csrf.token());
    if (previous.length > 0) request.cookie(previous);
    return mvc.perform(request);
  }

  ResultActions me(Cookie session) throws Exception {
    return mvc.perform(get("/api/v1/auth/me").servletPath("/api/v1/auth/me").cookie(session));
  }

  @Test
  void loginIssuesOnlyOpaqueCookieAndRestoresPrincipal() throws Exception {
    var response =
        login(username, "test-password")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accountId").value(userId.toString()))
            .andExpect(jsonPath("$.data.roleAssignments[0].roleId").value(roleId.toString()))
            .andExpect(jsonPath("$.data.password").doesNotExist())
            .andExpect(jsonPath("$.data.jwt").doesNotExist())
            .andExpect(jsonPath("$.data.sessionId").doesNotExist())
            .andReturn()
            .getResponse();
    Cookie session = response.getCookie("NKC_SESSION");
    assertThat(session).isNotNull();
    assertThat(session.isHttpOnly()).isTrue();
    assertThat(session.getValue()).hasSize(43).doesNotContain(".");
    me(session)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.staffMemberId").value(staffId.toString()));
    assertThat(
            jdbc.queryForObject("SELECT row_version FROM accounts WHERE id=?", Long.class, userId))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_events WHERE actor_account_id=? AND action='ACCOUNT_LOGIN'",
                Integer.class,
                userId))
        .isEqualTo(1);
  }

  @Test
  void invalidCredentialsAndInactiveAccountsAreRejectedButMissingRolesCanAuthenticate()
      throws Exception {
    login(username, "wrong")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.result").value("NG"));
    login("missing-user", "wrong").andExpect(status().isUnauthorized());
    jdbc.update("UPDATE staff_members SET status='SUSPENDED' WHERE id=?", staffId);
    login(username, "test-password").andExpect(status().isUnauthorized());
    jdbc.update("UPDATE staff_members SET status='ACTIVE' WHERE id=?", staffId);
    jdbc.update("UPDATE accounts SET status='DISABLED' WHERE id=?", userId);
    login(username, "test-password").andExpect(status().isUnauthorized());
    jdbc.update("UPDATE accounts SET status='ACTIVE' WHERE id=?", userId);
    jdbc.update("UPDATE roles SET active=false WHERE id=?", roleId);
    login(username, "test-password")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.roleAssignments").isEmpty());
    jdbc.update("DELETE FROM account_roles WHERE account_id=?", userId);
    login(username, "test-password")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.roleAssignments").isEmpty());
  }

  @Test
  void csrfIsRequiredAndOldSessionCookieDoesNotBlockLogin() throws Exception {
    mvc.perform(
            post("/api/v1/auth/login")
                .contentType("application/json")
                .content("{\"username\":\"someone\",\"password\":\"something\"}"))
        .andExpect(status().isForbidden());
    login(username, "test-password", new Cookie("NKC_SESSION", "expired"))
        .andExpect(status().isOk());
  }

  @Test
  void multipleSessionsLogoutAndRevocationAreIndependent() throws Exception {
    Cookie first =
        login(username, "test-password")
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getCookie("NKC_SESSION");
    Cookie second =
        login(username, "test-password")
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getCookie("NKC_SESSION");
    var csrf = csrf();
    mvc.perform(
            post("/api/v1/auth/logout")
                .servletPath("/api/v1/auth/logout")
                .cookie(first, csrf.cookie())
                .header(csrf.header(), csrf.token()))
        .andExpect(status().isNoContent());
    me(first).andExpect(status().isUnauthorized());
    me(second).andExpect(status().isOk());
    revocation.revokeAllSessions(userId);
    me(second).andExpect(status().isUnauthorized());
  }

  @Test
  void loginRotatesTheBrowserSession() throws Exception {
    Cookie first =
        login(username, "test-password")
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getCookie("NKC_SESSION");
    Cookie second =
        login(username, "test-password", first)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getCookie("NKC_SESSION");
    me(first).andExpect(status().isUnauthorized());
    me(second).andExpect(status().isOk());
  }

  @Test
  void unsupportedCredentialHashIsRejectedAndPatientCanAuthenticateWithoutStaff() throws Exception {
    jdbc.update("UPDATE accounts SET password_hash='invalid-hash' WHERE id=?", userId);
    login(username, "test-password").andExpect(status().isUnauthorized());
    UUID patient = UUID.randomUUID();
    jdbc.update(
        """
                INSERT INTO patients(id,patient_code,identification_number,full_name,date_of_birth,sex,status)
                VALUES(?,?,?,'Patient','1990-01-01','MALE','ACTIVE')
                """,
        patient,
        patient.toString().substring(0, 20),
        patient.toString().substring(0, 20));
    jdbc.update(
        "UPDATE accounts SET account_type='PATIENT',staff_member_id=NULL,patient_id=?,password_hash=? WHERE id=?",
        patient,
        passwords.encode("test-password"),
        userId);
    var response =
        login(username, "test-password")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accountType").value("PATIENT"))
            .andExpect(jsonPath("$.data.patientId").value(patient.toString()))
            .andExpect(jsonPath("$.data.staffMemberId").isEmpty())
            .andReturn()
            .getResponse();
    Cookie first = response.getCookie("NKC_SESSION");
    me(first)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.patientId").value(patient.toString()));
    mvc.perform(get("/api/v1/organizations").servletPath("/api/v1/organizations").cookie(first))
        .andExpect(status().isForbidden());
    assertThat(
            jdbc.queryForObject("SELECT row_version FROM accounts WHERE id=?", Long.class, userId))
        .isZero();
    jdbc.update("DELETE FROM account_roles WHERE account_id=?", userId);
    Cookie second =
        login(username, "test-password")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.roleAssignments").isEmpty())
            .andReturn()
            .getResponse()
            .getCookie("NKC_SESSION");
    me(second).andExpect(status().isOk());
    var csrf = csrf();
    mvc.perform(
            post("/api/v1/auth/logout")
                .servletPath("/api/v1/auth/logout")
                .cookie(first, csrf.cookie())
                .header(csrf.header(), csrf.token()))
        .andExpect(status().isNoContent());
    me(first).andExpect(status().isUnauthorized());
    me(second).andExpect(status().isOk());
    csrf = csrf();
    mvc.perform(
            post("/api/v1/auth/logout-all")
                .servletPath("/api/v1/auth/logout-all")
                .cookie(second, csrf.cookie())
                .header(csrf.header(), csrf.token()))
        .andExpect(status().isNoContent());
    me(second).andExpect(status().isUnauthorized());
  }

  @Test
  void corsAllowsOnlyConfiguredOrigin() throws Exception {
    mvc.perform(
            options("/api/v1/auth/me")
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "GET"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    mvc.perform(
            options("/api/v1/auth/me")
                .header("Origin", "https://untrusted.example")
                .header("Access-Control-Request-Method", "GET"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value(403));
  }

  @Test
  void usernameIsTrimmedAndCaseSensitiveAndPasswordIsNotTrimmed() throws Exception {
    login("  " + username + "  ", "test-password").andExpect(status().isOk());
    login(username.toUpperCase(), "test-password").andExpect(status().isUnauthorized());
    login(username, " test-password ").andExpect(status().isUnauthorized());
    login(username, "é".repeat(37)).andExpect(status().isBadRequest());
  }

  @Test
  void failedUsernameAttemptsReturnRetryAfter() throws Exception {
    for (int i = 0; i < 10; i++) login(username, "wrong").andExpect(status().isUnauthorized());
    login(username, "test-password")
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(jsonPath("$.code").value(429));
  }

  @Test
  void logoutAllRevokesAllDevicesAndExpiredLogoutStillClearsCookie() throws Exception {
    Cookie first =
        login(username, "test-password").andReturn().getResponse().getCookie("NKC_SESSION");
    Cookie second =
        login(username, "test-password").andReturn().getResponse().getCookie("NKC_SESSION");
    var csrf = csrf();
    mvc.perform(
            post("/api/v1/auth/logout-all")
                .servletPath("/api/v1/auth/logout-all")
                .cookie(first, csrf.cookie())
                .header(csrf.header(), csrf.token()))
        .andExpect(status().isNoContent())
        .andExpect(cookie().maxAge("NKC_SESSION", 0));
    me(first).andExpect(status().isUnauthorized());
    me(second).andExpect(status().isUnauthorized());
    csrf = csrf();
    mvc.perform(
            post("/api/v1/auth/logout")
                .servletPath("/api/v1/auth/logout")
                .cookie(first, csrf.cookie())
                .header(csrf.header(), csrf.token()))
        .andExpect(status().isNoContent())
        .andExpect(cookie().maxAge("NKC_SESSION", 0));
  }

  @Test
  void databaseAuditFailureCompensatesRedisWithoutCookieOrAudit() throws Exception {
    jdbc.execute(
        """
                CREATE FUNCTION reject_test_login_audit() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN IF NEW.action = 'ACCOUNT_LOGIN' THEN RAISE EXCEPTION 'test audit failure'; END IF;
                RETURN NEW; END $$
                """);
    jdbc.execute(
        "CREATE TRIGGER reject_test_login BEFORE INSERT ON audit_events FOR EACH ROW EXECUTE FUNCTION reject_test_login_audit()");
    try {
      login(username, "test-password")
          .andExpect(status().isInternalServerError())
          .andExpect(cookie().doesNotExist("NKC_SESSION"));
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM audit_events WHERE actor_account_id=? AND action='ACCOUNT_LOGIN'",
                  Integer.class,
                  userId))
          .isZero();
      assertThat(redis.opsForZSet().size("nkc:auth:user:" + userId + ":sessions")).isZero();
    } finally {
      jdbc.execute("DROP TRIGGER reject_test_login ON audit_events");
      jdbc.execute("DROP FUNCTION reject_test_login_audit()");
    }
  }

  @Test
  void multiplePermissionsDoNotDuplicateAssignmentsAndDatabaseChangesDoNotRefreshSnapshot()
      throws Exception {
    for (String permission : new String[] {"READ", "WRITE"}) {
      UUID id = UUID.randomUUID();
      jdbc.update(
          "INSERT INTO permissions(id,code,name,description) VALUES(?,?,'Test permission','Test permission')",
          id,
          permission + id);
      jdbc.update("INSERT INTO role_permissions(role_id,permission_id) VALUES(?,?)", roleId, id);
    }
    UUID secondRole = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO roles(id,code,name,description) VALUES(?,?,'Other role','Other role')",
        secondRole,
        secondRole.toString());
    jdbc.update(
        "INSERT INTO account_roles(account_id,role_id,granted_by) VALUES(?,?,?)",
        userId,
        secondRole,
        userId);
    var response =
        login(username, "test-password")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.roleAssignments.length()").value(2))
            .andReturn()
            .getResponse();
    var roles = json.readTree(response.getContentAsString()).get("data").get("roleAssignments");
    int permissionCount = 0;
    for (var role : roles) permissionCount += role.get("permissions").size();
    assertThat(permissionCount).isEqualTo(2);
    jdbc.update("UPDATE roles SET active=false WHERE id=?", roleId);
    me(response.getCookie("NKC_SESSION"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.roleAssignments.length()").value(2));
  }
}

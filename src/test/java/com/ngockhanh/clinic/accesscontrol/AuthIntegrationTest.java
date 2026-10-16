package com.ngockhanh.clinic.accesscontrol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ngockhanh.clinic.accesscontrol.application.port.out.SessionStore;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.mapper.UserLoginMyBatisMapper;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.record.PermissionRecord;
import jakarta.servlet.http.Cookie;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

// Close cached connections when this class finishes; its containers are class-scoped.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
    properties = {
      "clinic.auth.cookie-secure=false",
      "clinic.auth.allowed-origins=http://localhost:3000"
    })
@AutoConfigureMockMvc
class AuthIntegrationTest {
  private static final String COOKIE = "NKC_SESSION";
  private static final String ORIGIN = "http://localhost:3000";
  private static final String PASSWORD = "Mật khẩu-01";

  /** Staff and role administration routes (V004, V005) belong to the Administrator only. */
  private static final Set<String> ADMINISTRATION_PERMISSIONS =
      Set.of(
          "STAFF_MEMBER_VIEW",
          "USER_ACCOUNT_CREATE",
          "ROLE_VIEW",
          "ROLE_MANAGE",
          "ROLE_CREATE",
          "ROLE_UPDATE",
          "PERMISSION_VIEW",
          "ROLE_PERMISSION_VIEW",
          "ROLE_PERMISSION_ASSIGN");

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
  @Autowired StringRedisTemplate redis;
  @Autowired SessionStore sessions;
  @Autowired ApplicationContext context;
  @Autowired UserLoginMyBatisMapper permissions;

  private static final String NO_PERMISSION_ROLE = "SYNTHETIC_EMPTY";

  UUID accountId;
  String username;

  @BeforeEach
  void staffAccount() {
    UUID staffId = UUID.randomUUID();
    accountId = UUID.randomUUID();
    username = "staff-" + accountId;
    jdbc.update(
        "INSERT INTO staff_members(id,staff_code,full_name,status) VALUES (?,?,?,'ACTIVE')",
        staffId,
        staffId.toString().substring(0, 20),
        "Synthetic staff");
    jdbc.update(
        "INSERT INTO roles(code,name,description) VALUES (?,'Synthetic role','Staff role without permissions, test only') ON CONFLICT (code) DO NOTHING",
        NO_PERMISSION_ROLE);
    jdbc.update(
        "INSERT INTO accounts(id,account_type,role_id,username,password_hash,staff_member_id,status) SELECT ?,'STAFF',id,?,?,?,'ACTIVE' FROM roles WHERE code = ?",
        accountId,
        username,
        passwords.encode(PASSWORD),
        staffId,
        NO_PERMISSION_ROLE);
  }

  private ResultActions login(String name, String password, Cookie... previous) throws Exception {
    var request =
        post("/api/v1/auth/login")
            .header("Origin", ORIGIN)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + name + "\",\"password\":\"" + password + "\"}");
    if (previous.length > 0) request.cookie(previous);
    return mvc.perform(request);
  }

  /** Moves the account to a seeded role; an account holds exactly one role. */
  private void assignRole(String roleCode) {
    jdbc.update(
        "UPDATE accounts SET role_id = (SELECT id FROM roles WHERE code = ?) WHERE id = ?",
        roleCode,
        accountId);
  }

  /** Calls a stored endpoint with an Origin and an empty body for writes. */
  private ResultActions call(PermissionRecord endpoint, Cookie session) throws Exception {
    var method = HttpMethod.valueOf(endpoint.httpMethod());
    var call =
        request(method, endpoint.endpoint().replaceAll("\\{[^}]+}", UUID.randomUUID().toString()));
    if (method != HttpMethod.GET)
      call.header("Origin", ORIGIN).contentType(MediaType.APPLICATION_JSON).content("{}");
    return mvc.perform(call.cookie(session));
  }

  private Cookie sessionCookie(ResultActions result) {
    return result.andReturn().getResponse().getCookie(COOKIE);
  }

  private Integer audits(String action) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM audit_events WHERE actor_account_id = ? AND action = ?",
        Integer.class,
        accountId,
        action);
  }

  @Test
  void productionContextHasNoDefaultUserOrLoginPage() throws Exception {
    assertThat(context.getBeansOfType(UserDetailsService.class)).isEmpty();
    mvc.perform(get("/login")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/auth/me").header("Authorization", "Basic dXNlcjpwYXNzd29yZA=="))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void signInAuthenticatesLaterRequestsAndSignOutEndsTheSession() throws Exception {
    assignRole("CLINIC_MANAGER");
    Cookie session = sessionCookie(login(username, PASSWORD).andExpect(status().isOk()));
    assertThat(session).isNotNull();
    assertThat(session.isHttpOnly()).isTrue();

    mvc.perform(get("/api/v1/auth/me").cookie(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.userId").value(accountId.toString()));
    mvc.perform(get("/api/v1/organizations/" + UUID.randomUUID()).cookie(session))
        .andExpect(status().isNotFound());
    assertThat(redis.opsForSet().size("nkc:auth:account-sessions:" + accountId)).isEqualTo(1L);

    mvc.perform(post("/api/v1/auth/logout").header("Origin", ORIGIN).cookie(session))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/auth/me").cookie(session)).andExpect(status().isUnauthorized());

    assertThat(audits("ACCOUNT_LOGIN")).isEqualTo(1);
    assertThat(audits("ACCOUNT_LOGOUT")).isEqualTo(1);
  }

  @Test
  void staffEndpointsRequireThePermissionCapturedAtSignIn() throws Exception {
    var endpoints = permissions.findEndpointPermissions();
    assertThat(endpoints).hasSize(53);
    Cookie withoutPermission = sessionCookie(login(username, PASSWORD).andExpect(status().isOk()));
    for (PermissionRecord endpoint : endpoints) {
      call(endpoint, withoutPermission).andExpect(status().isForbidden());
    }

    // An account holds one role: business routes belong to the Clinic Manager, staff
    // and role administration routes (V004, V005) to the Administrator.
    assignRole("CLINIC_MANAGER");
    for (PermissionRecord endpoint : endpoints) {
      call(endpoint, withoutPermission).andExpect(status().isForbidden());
    }
    Cookie manager = sessionCookie(login(username, PASSWORD).andExpect(status().isOk()));
    for (PermissionRecord endpoint : endpoints) {
      if (ADMINISTRATION_PERMISSIONS.contains(endpoint.code())) continue;
      int status = call(endpoint, manager).andReturn().getResponse().getStatus();
      assertThat(status).as(endpoint.httpMethod() + " " + endpoint.endpoint()).isNotIn(401, 403);
    }

    assignRole("ADMINISTRATOR");
    Cookie administrator = sessionCookie(login(username, PASSWORD).andExpect(status().isOk()));
    for (PermissionRecord endpoint : endpoints) {
      if (!ADMINISTRATION_PERMISSIONS.contains(endpoint.code())) continue;
      int status = call(endpoint, administrator).andReturn().getResponse().getStatus();
      assertThat(status).as(endpoint.httpMethod() + " " + endpoint.endpoint()).isNotIn(401, 403);
    }
  }

  @Test
  void everyEndpointPermissionIsSeededForItsOwningRole() {
    for (PermissionRecord endpoint : permissions.findEndpointPermissions()) {
      assertThat(
              jdbc.queryForList(
                  "SELECT r.code FROM role_permissions rp JOIN roles r ON r.id = rp.role_id"
                      + " JOIN permissions p ON p.id = rp.permission_id WHERE p.code = ?",
                  String.class,
                  endpoint.code()))
          .as(endpoint.code())
          .contains(
              ADMINISTRATION_PERMISSIONS.contains(endpoint.code())
                  ? "ADMINISTRATOR"
                  : "CLINIC_MANAGER");
    }
  }

  @Test
  void signingInAgainEndsThePreviousBrowserSession() throws Exception {
    Cookie first = sessionCookie(login(username, PASSWORD).andExpect(status().isOk()));
    Cookie second = sessionCookie(login(username, PASSWORD, first).andExpect(status().isOk()));

    mvc.perform(get("/api/v1/auth/me").cookie(first)).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/auth/me").cookie(second)).andExpect(status().isOk());
  }

  @Test
  void credentialsAreComparedExactlyAndFailuresOfExistingAccountsAreAudited() throws Exception {
    login(" " + username, PASSWORD).andExpect(status().isUnauthorized());
    login(username.toUpperCase(), PASSWORD).andExpect(status().isUnauthorized());
    login(username, PASSWORD + " ").andExpect(status().isUnauthorized());
    jdbc.update("UPDATE accounts SET status='DISABLED' WHERE id=?", accountId);
    login(username, PASSWORD).andExpect(status().isUnauthorized());

    assertThat(audits("ACCOUNT_LOGIN_FAILED")).isEqualTo(2);
  }

  @Test
  void revokeAllEndsEverySessionOfTheAccount() throws Exception {
    Cookie first = sessionCookie(login(username, PASSWORD));
    Cookie second = sessionCookie(login(username, PASSWORD));

    sessions.revokeAll(accountId);

    mvc.perform(get("/api/v1/auth/me").cookie(first)).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/auth/me").cookie(second)).andExpect(status().isUnauthorized());
  }

  @Test
  void patientCanSignInButCannotUseStaffRoutes() throws Exception {
    UUID patientId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO patients(id,patient_code,identification_number,full_name,date_of_birth,sex,status) VALUES (?,?,?,'Synthetic patient','1990-01-01','MALE','ACTIVE')",
        patientId,
        patientId.toString().substring(0, 20),
        patientId.toString().substring(0, 20));
    jdbc.update(
        "UPDATE accounts SET account_type='PATIENT', role_id=(SELECT id FROM roles WHERE code='PATIENT'), staff_member_id=NULL, patient_id=? WHERE id=?",
        patientId,
        accountId);

    Cookie session = sessionCookie(login(username, PASSWORD).andExpect(status().isOk()));

    mvc.perform(get("/api/v1/auth/me").cookie(session))
        .andExpect(jsonPath("$.data.patientId").value(patientId.toString()));
    mvc.perform(get("/api/v1/organizations/" + UUID.randomUUID()).cookie(session))
        .andExpect(status().isForbidden());
  }
}

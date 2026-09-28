package com.ngockhanh.clinic.identity;

import com.ngockhanh.clinic.identity.infrastructure.security.StaffPasswordEncoder;
import com.ngockhanh.clinic.identity.application.port.access.SessionRevocation;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class StaffAuthIntegrationTest {
    @Container static final PostgreSQLContainer PG = new PostgreSQLContainer("postgres:18-alpine");
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", PG::getJdbcUrl);
        r.add("spring.datasource.username", PG::getUsername);
        r.add("spring.datasource.password", PG::getPassword);
        r.add("spring.data.redis.host", REDIS::getHost);
        r.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired StaffPasswordEncoder passwords;
    @Autowired JsonMapper json;
    @Autowired SessionRevocation revocation;
    @Autowired org.springframework.data.redis.core.StringRedisTemplate redis;
    String username;
    UUID userId, staffId, assignmentId, roleId;

    @BeforeEach void staff() {
        redis.delete(redis.keys("nkc:auth:limit:*"));
        userId = UUID.randomUUID(); staffId = UUID.randomUUID(); roleId = UUID.randomUUID(); assignmentId = UUID.randomUUID();
        username = "staff-" + userId;
        jdbc.update("INSERT INTO staff(id,staff_code,full_name,staff_type) VALUES(?,?,?,?)", staffId, staffId.toString().substring(0,20), "Test staff", "DOCTOR");
        jdbc.update("INSERT INTO users(id,principal_type,staff_id,status,created_at,username,password) VALUES(?,'STAFF',?,'ACTIVE',CURRENT_TIMESTAMP,?,?)",
                userId, staffId, username, passwords.encode("test-password"));
        jdbc.update("INSERT INTO roles(id,role_code,role_name) VALUES(?,?,?)", roleId, roleId.toString(), "Doctor");
        jdbc.update("INSERT INTO user_roles(id,user_id,role_id,valid_from) VALUES(?,?,?,CURRENT_TIMESTAMP - interval '1 hour')",
                assignmentId, userId, roleId);
    }

    record Csrf(Cookie cookie, String header, String token) {}
    Csrf csrf() throws Exception {
        var response = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn().getResponse();
        var data = json.readTree(response.getContentAsString()).get("data");
        return new Csrf(response.getCookie("XSRF-TOKEN"), data.get("headerName").asText(), data.get("token").asText());
    }
    ResultActions login(String name, String password, Cookie... previous) throws Exception {
        var csrf = csrf();
        var request = post("/api/v1/auth/staff/login").servletPath("/api/v1/auth/staff/login")
                .contentType("application/json").content(json.writeValueAsString(java.util.Map.of("username", name, "password", password)))
                .cookie(csrf.cookie()).header(csrf.header(), csrf.token());
        if (previous.length > 0) request.cookie(previous);
        return mvc.perform(request);
    }
    ResultActions me(Cookie session) throws Exception {
        return mvc.perform(get("/api/v1/auth/me").servletPath("/api/v1/auth/me").cookie(session));
    }

    @Test void loginIssuesOnlyOpaqueCookieAndRestoresPrincipal() throws Exception {
        var response = login(username, "test-password").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(userId.toString()))
                .andExpect(jsonPath("$.data.roleAssignments[0].assignmentId").value(assignmentId.toString()))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.jwt").doesNotExist())
                .andExpect(jsonPath("$.data.sessionId").doesNotExist()).andReturn().getResponse();
        Cookie session = response.getCookie("NKC_SESSION");
        assertThat(session).isNotNull();
        assertThat(session.isHttpOnly()).isTrue();
        assertThat(session.getValue()).hasSize(43).doesNotContain(".");
        me(session).andExpect(status().isOk()).andExpect(jsonPath("$.data.staffId").value(staffId.toString()));
        assertThat(jdbc.queryForObject("SELECT last_login_at IS NOT NULL FROM users WHERE id=?", Boolean.class, userId)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE actor_user_id=? AND action='STAFF_LOGIN'", Integer.class, userId)).isEqualTo(1);
    }

    @Test void invalidCredentialsInactiveStaffAndMissingRolesAreRejected() throws Exception {
        login(username, "wrong").andExpect(status().isUnauthorized());
        login("missing-user", "wrong").andExpect(status().isUnauthorized());
        jdbc.update("UPDATE staff SET is_active=false WHERE id=?", staffId);
        login(username, "test-password").andExpect(status().isUnauthorized());
        jdbc.update("UPDATE staff SET is_active=true WHERE id=?", staffId);
        jdbc.update("UPDATE users SET status='INACTIVE' WHERE id=?", userId);
        login(username, "test-password").andExpect(status().isUnauthorized());
        jdbc.update("UPDATE users SET status='ACTIVE' WHERE id=?", userId);
        jdbc.update("UPDATE user_roles SET valid_from=CURRENT_TIMESTAMP + interval '1 hour' WHERE id=?", assignmentId);
        login(username, "test-password").andExpect(status().isUnauthorized());
        jdbc.update("UPDATE user_roles SET valid_from=CURRENT_TIMESTAMP - interval '1 hour', valid_to=CURRENT_TIMESTAMP - interval '1 minute' WHERE id=?", assignmentId);
        login(username, "test-password").andExpect(status().isUnauthorized());
        jdbc.update("UPDATE user_roles SET valid_to=NULL WHERE id=?", assignmentId);
        jdbc.update("UPDATE roles SET is_active=false WHERE id=?", roleId);
        login(username, "test-password").andExpect(status().isUnauthorized());
        jdbc.update("DELETE FROM user_roles WHERE user_id=?", userId);
        login(username, "test-password").andExpect(status().isUnauthorized());
    }

    @Test void csrfIsRequiredAndOldSessionCookieDoesNotBlockLogin() throws Exception {
        mvc.perform(post("/api/v1/auth/staff/login").contentType("application/json")
                .content("{\"username\":\"someone\",\"password\":\"something\"}"))
                .andExpect(status().isForbidden());
        login(username, "test-password", new Cookie("NKC_SESSION", "expired"))
                .andExpect(status().isOk());
    }

    @Test void multipleSessionsLogoutAndRevocationAreIndependent() throws Exception {
        Cookie first = login(username, "test-password").andExpect(status().isOk()).andReturn().getResponse().getCookie("NKC_SESSION");
        Cookie second = login(username, "test-password").andExpect(status().isOk()).andReturn().getResponse().getCookie("NKC_SESSION");
        var csrf = csrf();
        mvc.perform(post("/api/v1/auth/logout").servletPath("/api/v1/auth/logout").cookie(first, csrf.cookie())
                .header(csrf.header(), csrf.token())).andExpect(status().isNoContent());
        me(first).andExpect(status().isUnauthorized());
        me(second).andExpect(status().isOk());
        revocation.revokeAllSessions(userId);
        me(second).andExpect(status().isUnauthorized());
    }

    @Test void loginRotatesTheBrowserSession() throws Exception {
        Cookie first = login(username, "test-password").andExpect(status().isOk()).andReturn().getResponse().getCookie("NKC_SESSION");
        Cookie second = login(username, "test-password", first).andExpect(status().isOk()).andReturn().getResponse().getCookie("NKC_SESSION");
        me(first).andExpect(status().isUnauthorized());
        me(second).andExpect(status().isOk());
    }

    @Test void credentialNullAndPatientPrincipalCannotLogin() throws Exception {
        jdbc.update("UPDATE users SET password=NULL WHERE id=?", userId);
        login(username, "test-password").andExpect(status().isUnauthorized());
        UUID patient = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO patients(id,patient_code,identification_number,full_name,full_name_normalized,date_of_birth,sex,created_at,updated_at)
            VALUES(?,?,?,'Patient','patient','1990-01-01','MALE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            """, patient, patient.toString().substring(0,20), patient.toString().substring(0,20));
        jdbc.update("UPDATE users SET principal_type='PATIENT',staff_id=NULL,patient_id=?,password=? WHERE id=?",
                patient, passwords.encode("test-password"), userId);
        login(username, "test-password").andExpect(status().isUnauthorized());
    }

    @Test void corsAllowsOnlyConfiguredOrigin() throws Exception {
        mvc.perform(options("/api/v1/auth/me").header("Origin","http://localhost:3000")
                .header("Access-Control-Request-Method","GET")).andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Credentials","true"));
        mvc.perform(options("/api/v1/auth/me").header("Origin","https://untrusted.example")
                .header("Access-Control-Request-Method","GET")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test void usernameIsTrimmedAndCaseSensitiveAndPasswordIsNotTrimmed() throws Exception {
        login("  " + username + "  ", "test-password").andExpect(status().isOk());
        login(username.toUpperCase(), "test-password").andExpect(status().isUnauthorized());
        login(username, " test-password ").andExpect(status().isUnauthorized());
        login(username, "é".repeat(37)).andExpect(status().isBadRequest());
    }

    @Test void failedUsernameAttemptsReturnRetryAfter() throws Exception {
        for (int i = 0; i < 10; i++) login(username, "wrong").andExpect(status().isUnauthorized());
        login(username, "test-password").andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After")).andExpect(jsonPath("$.code").value(429));
    }

    @Test void logoutAllRevokesAllDevicesAndExpiredLogoutStillClearsCookie() throws Exception {
        Cookie first = login(username, "test-password").andReturn().getResponse().getCookie("NKC_SESSION");
        Cookie second = login(username, "test-password").andReturn().getResponse().getCookie("NKC_SESSION");
        var csrf = csrf();
        mvc.perform(post("/api/v1/auth/logout-all").servletPath("/api/v1/auth/logout-all")
                        .cookie(first, csrf.cookie()).header(csrf.header(), csrf.token()))
                .andExpect(status().isNoContent()).andExpect(cookie().maxAge("NKC_SESSION", 0));
        me(first).andExpect(status().isUnauthorized());
        me(second).andExpect(status().isUnauthorized());
        csrf = csrf();
        mvc.perform(post("/api/v1/auth/logout").servletPath("/api/v1/auth/logout")
                        .cookie(first, csrf.cookie()).header(csrf.header(), csrf.token()))
                .andExpect(status().isNoContent()).andExpect(cookie().maxAge("NKC_SESSION", 0));
    }

    @Test void databaseAuditFailureRollsBackLastLoginAndCompensatesRedisWithoutCookie() throws Exception {
        jdbc.execute("""
            CREATE FUNCTION reject_test_login_audit() RETURNS trigger LANGUAGE plpgsql AS $$
            BEGIN IF NEW.action = 'STAFF_LOGIN' THEN RAISE EXCEPTION 'test audit failure'; END IF;
            RETURN NEW; END $$
            """);
        jdbc.execute("CREATE TRIGGER reject_test_login BEFORE INSERT ON audit_logs FOR EACH ROW EXECUTE FUNCTION reject_test_login_audit()");
        try {
            login(username, "test-password").andExpect(status().isInternalServerError())
                    .andExpect(cookie().doesNotExist("NKC_SESSION"));
            assertThat(jdbc.queryForObject("SELECT last_login_at FROM users WHERE id=?", java.sql.Timestamp.class, userId)).isNull();
            assertThat(redis.opsForZSet().size("nkc:auth:user:" + userId + ":sessions")).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER reject_test_login ON audit_logs");
            jdbc.execute("DROP FUNCTION reject_test_login_audit()");
        }
    }

    @Test void multiplePermissionsDoNotDuplicateAssignmentsAndDatabaseChangesDoNotRefreshSnapshot() throws Exception {
        for (String permission : new String[]{"READ", "WRITE"}) {
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO permissions(id,permission_code,module) VALUES(?,?,'identity')", id, permission + id);
            jdbc.update("INSERT INTO role_permissions(id,role_id,permission_id) VALUES(?,?,?)", UUID.randomUUID(), roleId, id);
        }
        UUID secondRole = UUID.randomUUID();
        jdbc.update("INSERT INTO roles(id,role_code,role_name) VALUES(?,?,'Other role')", secondRole, secondRole.toString());
        jdbc.update("INSERT INTO user_roles(id,user_id,role_id,valid_from) VALUES(?,?,?,CURRENT_TIMESTAMP - interval '1 hour')",
                UUID.randomUUID(), userId, secondRole);
        var response = login(username, "test-password").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roleAssignments.length()").value(2)).andReturn().getResponse();
        var roles = json.readTree(response.getContentAsString()).get("data").get("roleAssignments");
        int permissionCount = 0;
        for (var role : roles) permissionCount += role.get("permissions").size();
        assertThat(permissionCount).isEqualTo(2);
        jdbc.update("UPDATE roles SET is_active=false WHERE id=?", roleId);
        me(response.getCookie("NKC_SESSION")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roleAssignments.length()").value(2));
    }
}

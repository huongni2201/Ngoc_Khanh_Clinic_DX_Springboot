package com.ngockhanh.clinic.catalog.infrastructure.persistence;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.accesscontrol.application.usecase.AuthenticateSessionUseCase;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.mapper.UserLoginMyBatisMapper;
import com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.record.PermissionRecord;
import com.ngockhanh.clinic.catalog.application.command.*;
import com.ngockhanh.clinic.catalog.application.usecase.*;
import com.ngockhanh.clinic.shared.exception.*;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Verifies catalog SQL, mutation/audit transactions, real session filters and concurrency. */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
    properties = {
      "clinic.auth.cookie-secure=false",
      "clinic.auth.allowed-origins=http://localhost:3000",
      "nkc.document.jobs.enabled=false",
      "nkc.document.seed-starter-templates=false"
    })
@AutoConfigureMockMvc
class CatalogManagementIntegrationTest {
  @Container static final PostgreSQLContainer DB = new PostgreSQLContainer("postgres:18-alpine");

  @Container
  static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", DB::getJdbcUrl);
    r.add("spring.datasource.username", DB::getUsername);
    r.add("spring.datasource.password", DB::getPassword);
    r.add("spring.data.redis.host", REDIS::getHost);
    r.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
  }

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired PasswordEncoder encoder;
  @Autowired JsonMapper json;
  @Autowired AuthenticateSessionUseCase authenticate;
  @Autowired UserLoginMyBatisMapper permissions;
  @Autowired ApplicationContext context;
  UUID actor;
  String username;
  Cookie manager;
  UserPrincipal principal;
  static final String ORIGIN = "http://localhost:3000";
  static final String PASSWORD = "Synthetic-test-password-01";

  @BeforeEach
  void loginManager() throws Exception {
    UUID staff = UUID.randomUUID();
    actor = UUID.randomUUID();
    username = "catalog-" + actor;
    jdbc.update(
        "INSERT INTO staff_members(id,staff_code,full_name,status) VALUES (?,?,?,'ACTIVE')",
        staff,
        staff.toString().substring(0, 20),
        "Synthetic staff");
    jdbc.update(
        "INSERT INTO accounts(id,account_type,role_id,username,password_hash,staff_member_id,status) SELECT ?,'STAFF',id,?,?,?,'ACTIVE' FROM roles WHERE code='CLINIC_MANAGER'",
        actor,
        username,
        encoder.encode(PASSWORD),
        staff);
    grant("CLINIC_MANAGER");
    manager = login(username);
    principal = authenticate.execute(manager.getValue());
  }

  void grant(String role) {
    jdbc.update(
        "UPDATE accounts SET role_id=(SELECT id FROM roles WHERE code=?) WHERE id=?", role, actor);
  }

  Cookie login(String name) throws Exception {
    return mvc.perform(
            post("/api/v1/auth/login")
                .header("Origin", ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", name, "password", PASSWORD))))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getCookie("NKC_SESSION");
  }

  String code() {
    return ("T" + UUID.randomUUID().toString().replace("-", "")).toUpperCase(Locale.ROOT);
  }

  UUID department() {
    return jdbc.queryForObject(
        "INSERT INTO departments(code,name,department_type) VALUES (?,'Synthetic khoa','CLINICAL') RETURNING id",
        UUID.class,
        code());
  }

  UUID specialty() {
    return jdbc.queryForObject(
        "INSERT INTO specialties(code,name) VALUES (?,'Synthetic chuyên khoa') RETURNING id",
        UUID.class,
        code());
  }

  JsonNode data(ResultActions result) throws Exception {
    return json.readTree(result.andReturn().getResponse().getContentAsString()).get("data");
  }

  ResultActions write(String method, String url, Object body) throws Exception {
    return mvc.perform(
        request(HttpMethod.valueOf(method), url)
            .cookie(manager)
            .header("Origin", ORIGIN)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(body)));
  }

  Map<String, Object> payload(String resource, String uniqueCode) {
    Map<String, Object> p = new LinkedHashMap<>();
    p.put("code", uniqueCode);
    if (resource.equals("medicines")) {
      p.put("genericName", "Synthetic thuốc");
      p.put("brandName", null);
      p.put("strength", "500mg");
      p.put("dosageForm", "TABLET");
      p.put("unit", "VIEN");
    } else p.put("name", "Synthetic danh mục");
    switch (resource) {
      case "departments" -> p.put("departmentType", "CLINICAL");
      case "rooms" -> {
        p.put("departmentId", department());
        p.put("roomType", "EXAMINATION");
      }
      case "services" -> {
        p.put("serviceType", "CONSULTATION");
        p.put("performingDepartmentId", department());
        p.put("specialtyId", specialty());
        p.put("unitPrice", new BigDecimal("150000"));
        p.put("preparationInstruction", "Original instruction");
      }
    }
    return p;
  }

  @ParameterizedTest
  @ValueSource(strings = {"departments", "rooms", "specialties", "services", "medicines"})
  void executesAllSixOperationsWithPersistedMetadataAndNoOp(String resource) throws Exception {
    String unique = code(), base = "/api/v1/catalog/management/" + resource;
    Map<String, Object> create = payload(resource, unique);
    JsonNode created = data(write("POST", base, create).andExpect(status().isCreated()));
    UUID id = UUID.fromString(created.get("id").asString());
    assertThat(id.version()).isEqualTo(7);
    assertThat(created.get("createdAt")).isNotNull();
    assertThat(created.get("updatedAt")).isNotNull();
    assertThat(data(mvc.perform(get(base + "/" + id).cookie(manager)).andExpect(status().isOk())))
        .isEqualTo(created);
    mvc.perform(get(base).cookie(manager).param("searchKey", unique).param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.items[0].rowVersion").value(0));
    var update = new LinkedHashMap<>(create);
    update.remove("code");
    update.remove("departmentId");
    update.remove("performingDepartmentId");
    update.put("rowVersion", 0);
    if (resource.equals("medicines")) update.put("brandName", "Updated brand");
    else update.put("name", "Updated synthetic");
    if (resource.equals("services")) {
      update.put("specialtyId", specialty());
      update.put("preparationInstruction", null);
    }
    JsonNode changed =
        data(
            write("PUT", base + "/" + id, update)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rowVersion").value(1)));
    assertThat(data(mvc.perform(get(base + "/" + id).cookie(manager)).andExpect(status().isOk())))
        .isEqualTo(changed);
    write("PUT", base + "/" + id, update).andExpect(status().isConflict());
    String metadata =
        jdbc.queryForObject(
            "SELECT metadata::text FROM audit_events WHERE resource_id=? AND action LIKE 'UPDATE_%'",
            String.class, id);
    if (resource.equals("services")) {
      JsonNode audit = json.readTree(metadata);
      assertThat(audit.get("after").get("preparationInstruction").isNull()).isTrue();
      assertThat(audit.get("before").get("specialtyId"))
          .isNotEqualTo(audit.get("after").get("specialtyId"));
    }
    if (resource.equals("medicines")) assertThat(metadata).contains("Updated brand", "brandName");
    write("DELETE", base + "/" + id + "?rowVersion=1", Map.of()).andExpect(status().isNoContent());
    int audits =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_events WHERE resource_id=?", Integer.class, id);
    write("DELETE", base + "/" + id + "?rowVersion=2", Map.of()).andExpect(status().isNoContent());
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_events WHERE resource_id=?", Integer.class, id))
        .isEqualTo(audits);
    mvc.perform(get(base + "/" + id).cookie(manager))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.active").value(false))
        .andExpect(jsonPath("$.data.rowVersion").value(2));
    write("POST", base, create).andExpect(status().isConflict());
    JsonNode reactivated =
        data(
            write("POST", base + "/" + id + "/reactivate", Map.of("rowVersion", 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rowVersion").value(3)));
    assertThat(data(mvc.perform(get(base + "/" + id).cookie(manager)).andExpect(status().isOk())))
        .isEqualTo(reactivated);
    write("POST", base + "/" + id + "/reactivate", Map.of("rowVersion", 3))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.rowVersion").value(3));
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_events WHERE resource_id=? AND actor_account_id=?",
                Integer.class,
                id,
                actor))
        .isEqualTo(4);
  }

  MockHttpServletRequestBuilder endpointRequest(
      PermissionRecord endpoint, Cookie session, String origin) {
    var req =
        request(
            HttpMethod.valueOf(endpoint.httpMethod()),
            endpoint.endpoint().replace("{id}", UUID.randomUUID().toString()));
    if (session != null) req.cookie(session);
    if (!endpoint.httpMethod().equals("GET")) {
      if (origin != null) req.header("Origin", origin);
      req.contentType(MediaType.APPLICATION_JSON).content("{}");
    }
    return req;
  }

  @Test
  void allThirtyRoutesEnforceSessionPermissionAndOrigin() throws Exception {
    var routes =
        permissions.findEndpointPermissions().stream()
            .filter(p -> p.code().startsWith("CATALOG_"))
            .toList();
    assertThat(routes).hasSize(30);
    jdbc.update(
        "INSERT INTO roles(code,name,description) VALUES ('CATALOG_NO_PERMISSIONS','Synthetic no permissions','Synthetic test role') ON CONFLICT (code) DO NOTHING");
    grant("CATALOG_NO_PERMISSIONS");
    Cookie noRole = login(username);
    for (var route : routes) {
      mvc.perform(endpointRequest(route, null, ORIGIN)).andExpect(status().isUnauthorized());
      mvc.perform(endpointRequest(route, noRole, ORIGIN)).andExpect(status().isForbidden());
    }
    grant("CLINIC_MANAGER");
    manager = login(username);
    for (var route : routes) {
      int allowed =
          mvc.perform(endpointRequest(route, manager, ORIGIN))
              .andReturn()
              .getResponse()
              .getStatus();
      assertThat(allowed).as(route.code()).isIn(200, 400, 404);
      if (!route.httpMethod().equals("GET")) {
        mvc.perform(endpointRequest(route, manager, "https://untrusted.test"))
            .andExpect(status().isForbidden());
        mvc.perform(endpointRequest(route, manager, null)).andExpect(status().isForbidden());
      }
    }
  }

  @Test
  void newlyProvisionedClinicManagerCanManageAllFiveCatalogs() throws Exception {
    grant("ADMINISTRATOR");
    manager = login(username);
    mvc.perform(get("/api/v1/roles").cookie(manager))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.data[*].code").value(org.hamcrest.Matchers.hasItem("CLINIC_MANAGER")));
    JsonNode created =
        data(
            write(
                    "POST",
                    "/api/v1/staff-members",
                    Map.of(
                        "fullName",
                        "Quản Trị Danh Mục",
                        "initialPassword",
                        PASSWORD,
                        "roleCode",
                        "CLINIC_MANAGER"))
                .andExpect(status().isCreated()));
    actor = UUID.fromString(created.get("account").get("id").asString());
    username = created.get("account").get("username").asString();
    manager = login(username);
    principal = authenticate.execute(manager.getValue());
    assertThat(principal.roleAssignments())
        .singleElement()
        .satisfies(
            role -> {
              assertThat(role.roleCode()).isEqualTo("CLINIC_MANAGER");
              assertThat(role.permissions().stream().filter(p -> p.startsWith("CATALOG_")))
                  .hasSize(30);
            });
    for (String resource :
        List.of("departments", "rooms", "specialties", "services", "medicines")) {
      executesAllSixOperationsWithPersistedMetadataAndNoOp(resource);
    }
  }

  @Test
  void administratorDoesNotInheritClinicManagerCatalogPermissions() throws Exception {
    grant("ADMINISTRATOR");
    Cookie administrator = login(username);
    for (var endpoint :
        permissions.findEndpointPermissions().stream()
            .filter(permission -> permission.code().startsWith("CATALOG_"))
            .toList()) {
      mvc.perform(endpointRequest(endpoint, administrator, ORIGIN))
          .andExpect(status().isForbidden());
    }
  }

  @Test
  void oneActionPermissionDoesNotAuthorizeAnotherActionOrResource() throws Exception {
    String role = code();
    jdbc.update(
        "INSERT INTO roles(code,name,description) VALUES (?,'Synthetic role','Test')", role);
    jdbc.update(
        "INSERT INTO role_permissions(role_id,permission_id) SELECT r.id,p.id FROM roles r CROSS JOIN permissions p WHERE r.code=? AND p.code='CATALOG_DEPARTMENT_VIEW'",
        role);
    grant(role);
    Cookie single = login(username);
    mvc.perform(get("/api/v1/catalog/management/departments").cookie(single))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/catalog/management/departments/" + department()).cookie(single))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/catalog/management/rooms").cookie(single))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/catalog/management/departments")
                .cookie(single)
                .header("Origin", ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void rejectsInvalidQueriesAndKeepsLiteralWildcardSearch() throws Exception {
    var d = payload("departments", code());
    d.put("name", "Literal % _ \\ name");
    write("POST", "/api/v1/catalog/management/departments", d).andExpect(status().isCreated());
    mvc.perform(
            get("/api/v1/catalog/management/departments")
                .cookie(manager)
                .param("searchKey", "% _ \\"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1));
    for (String field : List.of("departmentType", "sortKey", "sortBy", "active"))
      mvc.perform(
              get("/api/v1/catalog/management/departments").cookie(manager).param(field, "INVALID"))
          .andExpect(status().isBadRequest());
    mvc.perform(
            get("/api/v1/catalog/management/services")
                .cookie(manager)
                .param("serviceType", "INVALID"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            get("/api/v1/catalog/management/departments")
                .cookie(manager)
                .param("departmentType", " clinical "))
        .andExpect(status().isOk());
    for (String resource :
        List.of("departments", "rooms", "specialties", "services", "medicines")) {
      write(
              "DELETE",
              "/api/v1/catalog/management/" + resource + "/" + UUID.randomUUID() + "?rowVersion=-1",
              Map.of())
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.data[0].field").value("rowVersion"));
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"departments", "rooms", "specialties", "services", "medicines"})
  void invalidHttpInputsAndMissingResourcesReturnClientErrorsWithoutWrites(String resource)
      throws Exception {
    String base = "/api/v1/catalog/management/" + resource;
    String unique = code();
    UUID missing = UUID.randomUUID();
    mvc.perform(get(base + "/not-a-uuid").cookie(manager))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(400));
    mvc.perform(get(base + "/" + missing).cookie(manager))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(404));
    for (var invalid :
        Map.of(
                "page", "0",
                "size", "101",
                "searchKey", "x".repeat(101),
                "sortKey", "code; DROP TABLE departments",
                "sortBy", "INVALID",
                "active", "INVALID")
            .entrySet()) {
      mvc.perform(get(base).cookie(manager).param(invalid.getKey(), invalid.getValue()))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value(400));
    }
    var invalidCode = payload(resource, unique);
    invalidCode.put("code", "INVALID CODE");
    write("POST", base, invalidCode).andExpect(status().isBadRequest());
    mvc.perform(
            post(base)
                .cookie(manager)
                .header("Origin", ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"))
        .andExpect(status().isBadRequest());
    var update = payload(resource, unique);
    update.remove("code");
    update.remove("departmentId");
    update.remove("performingDepartmentId");
    write("PUT", base + "/" + missing, update).andExpect(status().isBadRequest());
    update.put("rowVersion", 0);
    write("PUT", base + "/" + missing, update).andExpect(status().isNotFound());
    write("DELETE", base + "/" + missing, Map.of()).andExpect(status().isBadRequest());
    write("DELETE", base + "/" + missing + "?rowVersion=0", Map.of())
        .andExpect(status().isNotFound());
    write("POST", base + "/" + missing + "/reactivate", Map.of())
        .andExpect(status().isBadRequest());
    write("POST", base + "/" + missing + "/reactivate", Map.of("rowVersion", 0))
        .andExpect(status().isNotFound());
    mvc.perform(get(base).cookie(manager).param("searchKey", unique))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(0));
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_events WHERE actor_account_id=? AND action <> 'ACCOUNT_LOGIN'",
                Integer.class,
                actor))
        .isZero();
  }

  @ParameterizedTest
  @ValueSource(strings = {"departments", "rooms", "specialties", "services", "medicines"})
  void canonicalDuplicatePaginationAndStatusFiltersAgreeWithPersistedRows(String resource)
      throws Exception {
    String base = "/api/v1/catalog/management/" + resource, prefix = code();
    var firstPayload = payload(resource, " " + (prefix + "-A").toLowerCase(Locale.ROOT) + " ");
    JsonNode first = data(write("POST", base, firstPayload).andExpect(status().isCreated()));
    assertThat(first.get("code").asString()).isEqualTo(prefix + "-A");
    firstPayload.put("code", prefix + "-A");
    write("POST", base, firstPayload).andExpect(status().isConflict());
    var secondPayload = payload(resource, prefix + "-B");
    JsonNode second = data(write("POST", base, secondPayload).andExpect(status().isCreated()));
    for (int page = 1; page <= 2; page++) {
      mvc.perform(
              get(base)
                  .cookie(manager)
                  .param("searchKey", prefix)
                  .param("page", Integer.toString(page))
                  .param("size", "1")
                  .param("sortKey", "code")
                  .param("sortBy", "ASC"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.totalElements").value(2))
          .andExpect(jsonPath("$.data.totalPages").value(2))
          .andExpect(jsonPath("$.data.items.length()").value(1))
          .andExpect(
              jsonPath("$.data.items[0].id")
                  .value((page == 1 ? first : second).get("id").asString()));
    }
    mvc.perform(
            get(base)
                .cookie(manager)
                .param("searchKey", prefix)
                .param("page", "3")
                .param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(2))
        .andExpect(jsonPath("$.data.items.length()").value(0));
    write("DELETE", base + "/" + first.get("id").asString() + "?rowVersion=0", Map.of())
        .andExpect(status().isNoContent());
    for (boolean active : List.of(true, false)) {
      mvc.perform(
              get(base)
                  .cookie(manager)
                  .param("searchKey", prefix)
                  .param("active", Boolean.toString(active)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.totalElements").value(1))
          .andExpect(
              jsonPath("$.data.items[0].id").value((active ? second : first).get("id").asString()));
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM " + resource + " WHERE code LIKE ?",
                Integer.class,
                prefix + "%"))
        .isEqualTo(2);
  }

  @Test
  void servicePriceBoundariesAreValidatedThroughHttpBeforeDatabaseWrites() throws Exception {
    String base = "/api/v1/catalog/management/services", unique = code();
    var service = payload("services", unique);
    for (String invalidPrice : List.of("-0.01", "0.001", "1000000000000", "1E+12")) {
      service.put("unitPrice", new BigDecimal(invalidPrice));
      write("POST", base, service)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.data[0].field").value("unitPrice"));
    }
    service.put("preparationInstruction", "x".repeat(4001));
    service.put("unitPrice", new BigDecimal("999999999999.99"));
    write("POST", base, service).andExpect(status().isBadRequest());
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM services WHERE code=?", Integer.class, unique))
        .isZero();
    service.put("preparationInstruction", null);
    JsonNode created = data(write("POST", base, service).andExpect(status().isCreated()));
    assertThat(
            jdbc.queryForObject(
                "SELECT unit_price FROM services WHERE id=?",
                BigDecimal.class,
                UUID.fromString(created.get("id").asString())))
        .isEqualByComparingTo("999999999999.99");
  }

  @Test
  void dependencyGuardsReturn409ThroughHttpAndPreserveParentState() throws Exception {
    String departments = "/api/v1/catalog/management/departments";
    String specialties = "/api/v1/catalog/management/specialties";
    String rooms = "/api/v1/catalog/management/rooms";
    String services = "/api/v1/catalog/management/services";
    UUID dept = department(), spec = specialty();
    var room = payload("rooms", code());
    room.put("departmentId", dept);
    JsonNode createdRoom = data(write("POST", rooms, room).andExpect(status().isCreated()));
    write("DELETE", departments + "/" + dept + "?rowVersion=0", Map.of())
        .andExpect(status().isConflict());
    var service = payload("services", code());
    service.put("performingDepartmentId", dept);
    service.put("specialtyId", spec);
    JsonNode createdService =
        data(write("POST", services, service).andExpect(status().isCreated()));
    write("DELETE", specialties + "/" + spec + "?rowVersion=0", Map.of())
        .andExpect(status().isConflict());
    write("DELETE", rooms + "/" + createdRoom.get("id").asString() + "?rowVersion=0", Map.of())
        .andExpect(status().isNoContent());
    write("DELETE", departments + "/" + dept + "?rowVersion=0", Map.of())
        .andExpect(status().isConflict());
    write(
            "DELETE",
            services + "/" + createdService.get("id").asString() + "?rowVersion=0",
            Map.of())
        .andExpect(status().isNoContent());
    write("DELETE", departments + "/" + dept + "?rowVersion=0", Map.of())
        .andExpect(status().isNoContent());
    write("DELETE", specialties + "/" + spec + "?rowVersion=0", Map.of())
        .andExpect(status().isNoContent());
    write("POST", rooms, room).andExpect(status().isConflict());
    write("POST", services, service).andExpect(status().isConflict());
    write(
            "POST",
            rooms + "/" + createdRoom.get("id").asString() + "/reactivate",
            Map.of("rowVersion", 1))
        .andExpect(status().isConflict());
    write(
            "POST",
            services + "/" + createdService.get("id").asString() + "/reactivate",
            Map.of("rowVersion", 1))
        .andExpect(status().isConflict());
    for (String path : List.of(departments + "/" + dept, specialties + "/" + spec)) {
      mvc.perform(get(path).cookie(manager))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.active").value(false))
          .andExpect(jsonPath("$.data.rowVersion").value(1));
    }
  }

  @Test
  void editsInactiveServiceWithUnchangedInactiveSpecialtyAndRejectsReactivation() {
    UUID dept = department(), spec = specialty();
    var s =
        context
            .getBean(CreateManagedServiceUseCase.class)
            .execute(
                new CreateServiceCommand(
                    code(),
                    "Synthetic",
                    "CONSULTATION",
                    dept,
                    spec,
                    new BigDecimal("150000"),
                    null),
                principal);
    context
        .getBean(DeactivateManagedServiceUseCase.class)
        .execute(new DeactivateServiceCommand(s.id(), 0), principal);
    context
        .getBean(DeactivateSpecialtyUseCase.class)
        .execute(new DeactivateSpecialtyCommand(spec, 0), principal);
    var edited =
        context
            .getBean(UpdateManagedServiceUseCase.class)
            .execute(
                new UpdateServiceCommand(
                    s.id(), "Edited inactive", "CONSULTATION", spec, s.unitPrice(), "Edit", 1),
                principal);
    assertThat(edited.rowVersion()).isEqualTo(2);
    assertThat(edited.active()).isFalse();
    assertThatThrownBy(
            () ->
                context
                    .getBean(ReactivateManagedServiceUseCase.class)
                    .execute(new ReactivateServiceCommand(s.id(), 2), principal))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void auditFailureRollsBackBusinessWrite() {
    var fake =
        UserPrincipal.builder()
            .userId(UUID.randomUUID())
            .staffId(principal.staffId())
            .principalType("STAFF")
            .roleAssignments(principal.roleAssignments())
            .build();
    String unique = code();
    assertThatThrownBy(
            () ->
                context
                    .getBean(CreateDepartmentUseCase.class)
                    .execute(new CreateDepartmentCommand(unique, "Rollback", "CLINICAL"), fake))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM departments WHERE code=?", Integer.class, unique))
        .isZero();
  }

  @Test
  void editingLegacyCodePreservesStoredSpellingAndResponseAgreement() throws Exception {
    String stored = " legacy-" + UUID.randomUUID().toString().substring(0, 8) + " ";
    UUID id =
        jdbc.queryForObject(
            "INSERT INTO departments(code,name,department_type) VALUES (?,'Legacy','CLINICAL') RETURNING id",
            UUID.class,
            stored);
    JsonNode changed =
        data(
            write(
                    "PUT",
                    "/api/v1/catalog/management/departments/" + id,
                    Map.of("name", "Edited", "departmentType", "CLINICAL", "rowVersion", 0))
                .andExpect(status().isOk()));
    assertThat(changed.get("code").asString()).isEqualTo(stored);
    assertThat(
            data(
                mvc.perform(get("/api/v1/catalog/management/departments/" + id).cookie(manager))
                    .andExpect(status().isOk())))
        .isEqualTo(changed);
    assertThat(jdbc.queryForObject("SELECT code FROM departments WHERE id=?", String.class, id))
        .isEqualTo(stored);
  }

  List<Object> race(Callable<?> first, Callable<?> second) throws Exception {
    var barrier = new CyclicBarrier(2);
    try (var pool = Executors.newFixedThreadPool(2)) {
      List<Future<Object>> futures = new ArrayList<>();
      for (var work : List.of(first, second))
        futures.add(
            pool.submit(
                () -> {
                  barrier.await(10, TimeUnit.SECONDS);
                  try {
                    return work.call();
                  } catch (RuntimeException e) {
                    return e;
                  }
                }));
      List<Object> results = new ArrayList<>();
      for (var f : futures) results.add(f.get(20, TimeUnit.SECONDS));
      return results;
    }
  }

  @Test
  void concurrentCreatesYieldOneRowAndOneAudit() throws Exception {
    String unique = code();
    var usecase = context.getBean(CreateDepartmentUseCase.class);
    var cmd = new CreateDepartmentCommand(unique, "Race", "CLINICAL");
    var results =
        race(() -> usecase.execute(cmd, principal), () -> usecase.execute(cmd, principal));
    assertThat(
            results.stream()
                .filter(org.springframework.dao.DuplicateKeyException.class::isInstance)
                .count())
        .isEqualTo(1);
    UUID id = jdbc.queryForObject("SELECT id FROM departments WHERE code=?", UUID.class, unique);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_events WHERE resource_id=?", Integer.class, id))
        .isEqualTo(1);
  }

  @Test
  void concurrentUpdatesRejectLostUpdate() throws Exception {
    UUID id = department();
    var usecase = context.getBean(UpdateDepartmentUseCase.class);
    var results =
        race(
            () -> usecase.execute(new UpdateDepartmentCommand(id, "One", "CLINICAL", 0), principal),
            () ->
                usecase.execute(new UpdateDepartmentCommand(id, "Two", "CLINICAL", 0), principal));
    assertThat(results.stream().filter(ConcurrentUpdateException.class::isInstance).count())
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject("SELECT row_version FROM departments WHERE id=?", Long.class, id))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_events WHERE resource_id=?", Integer.class, id))
        .isEqualTo(1);
  }

  @Test
  void childCreationAndParentDeactivationCannotLeaveActiveChildUnderInactiveParent()
      throws Exception {
    UUID dept = department();
    String unique = code();
    var results =
        race(
            () ->
                context
                    .getBean(CreateRoomUseCase.class)
                    .execute(new CreateRoomCommand(unique, "Race", dept, "EXAMINATION"), principal),
            () -> {
              context
                  .getBean(DeactivateDepartmentUseCase.class)
                  .execute(new DeactivateDepartmentCommand(dept, 0), principal);
              return "deactivated";
            });
    assertThat(results.stream().filter(ConflictException.class::isInstance).count()).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM rooms r JOIN departments d ON d.id=r.department_id WHERE r.department_id=? AND r.active AND NOT d.active",
                Integer.class,
                dept))
        .isZero();
  }

  @Test
  void reactivationAndSpecialtyDeactivationKeepParentInvariant() throws Exception {
    UUID dept = department(), spec = specialty();
    var service =
        context
            .getBean(CreateManagedServiceUseCase.class)
            .execute(
                new CreateServiceCommand(
                    code(), "Race", "CONSULTATION", dept, spec, new BigDecimal("150000"), null),
                principal);
    context
        .getBean(DeactivateManagedServiceUseCase.class)
        .execute(new DeactivateServiceCommand(service.id(), 0), principal);
    var results =
        race(
            () ->
                context
                    .getBean(ReactivateManagedServiceUseCase.class)
                    .execute(new ReactivateServiceCommand(service.id(), 1), principal),
            () -> {
              context
                  .getBean(DeactivateSpecialtyUseCase.class)
                  .execute(new DeactivateSpecialtyCommand(spec, 0), principal);
              return "deactivated";
            });
    assertThat(results.stream().filter(ConflictException.class::isInstance).count()).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM services s JOIN specialties sp ON sp.id=s.specialty_id WHERE s.id=? AND s.active AND NOT sp.active",
                Integer.class,
                service.id()))
        .isZero();
  }

  @Test
  void swappingSpecialtiesAcrossDepartmentsUsesStableLockOrder() throws Exception {
    UUID a = specialty(), b = specialty();
    var create = context.getBean(CreateManagedServiceUseCase.class);
    var update = context.getBean(UpdateManagedServiceUseCase.class);
    var first =
        create.execute(
            new CreateServiceCommand(
                code(), "First", "CONSULTATION", department(), a, new BigDecimal("1"), null),
            principal);
    var second =
        create.execute(
            new CreateServiceCommand(
                code(), "Second", "CONSULTATION", department(), b, new BigDecimal("1"), null),
            principal);
    var results =
        race(
            () ->
                update.execute(
                    new UpdateServiceCommand(
                        first.id(), "First", "CONSULTATION", b, first.unitPrice(), null, 0),
                    principal),
            () ->
                update.execute(
                    new UpdateServiceCommand(
                        second.id(), "Second", "CONSULTATION", a, second.unitPrice(), null, 0),
                    principal));
    assertThat(results).noneMatch(Throwable.class::isInstance);
    assertThat(
            jdbc.queryForObject(
                "SELECT specialty_id FROM services WHERE id=?", UUID.class, first.id()))
        .isEqualTo(b);
    assertThat(
            jdbc.queryForObject(
                "SELECT specialty_id FROM services WHERE id=?", UUID.class, second.id()))
        .isEqualTo(a);
  }
}

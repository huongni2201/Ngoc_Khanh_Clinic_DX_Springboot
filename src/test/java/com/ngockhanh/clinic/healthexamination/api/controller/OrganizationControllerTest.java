package com.ngockhanh.clinic.healthexamination.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.healthexamination.api.request.UpdateOrganizationRequest;
import com.ngockhanh.clinic.healthexamination.application.command.CreateOrganizationCommand;
import com.ngockhanh.clinic.healthexamination.application.command.UpdateOrganizationCommand;
import com.ngockhanh.clinic.healthexamination.application.response.OrganizationResponse;
import com.ngockhanh.clinic.healthexamination.application.usecase.CreateOrganizationUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.GetOrganizationUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.UpdateOrganizationUseCase;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import com.ngockhanh.clinic.shared.web.ApiResponseWriter;
import com.ngockhanh.clinic.shared.web.GlobalExceptionHandler;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

class OrganizationControllerTest {

  private CreateOrganizationUseCase createOrganizationUseCase;
  private GetOrganizationUseCase getOrganizationUseCase;
  private UpdateOrganizationUseCase updateOrganizationUseCase;
  private OrganizationController controller;

  @BeforeEach
  void setUp() {
    createOrganizationUseCase = mock(CreateOrganizationUseCase.class);
    getOrganizationUseCase = mock(GetOrganizationUseCase.class);
    updateOrganizationUseCase = mock(UpdateOrganizationUseCase.class);
    controller =
        new OrganizationController(
            createOrganizationUseCase, getOrganizationUseCase, updateOrganizationUseCase);
  }

  @Test
  void createBuildsCommandAndReturnsCreatedResponse() throws Exception {
    UUID orgId = UUID.randomUUID();
    UUID actorId = UUID.randomUUID();
    OrganizationResponse expectedResponse =
        OrganizationResponse.builder()
            .id(orgId)
            .name("Clinic Corp")
            .taxCode("TAX-01")
            .address("123 Street")
            .contactFullName("Nguyen Van A")
            .contactPhone("0901234567")
            .contactPosition("Manager")
            .status("ACTIVE")
            .build();

    UserPrincipal principal =
        UserPrincipal.builder()
            .userId(actorId)
            .staffId(UUID.randomUUID())
            .patientId(null)
            .username("staff")
            .principalType("STAFF")
            .roleAssignments(List.of())
            .idleExpiresAt(Instant.now())
            .absoluteExpiresAt(Instant.now().plusSeconds(3600))
            .build();
    when(createOrganizationUseCase.execute(any(CreateOrganizationCommand.class), eq(actorId)))
        .thenReturn(expectedResponse);

    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(principal, "test", List.of()));
    try {
      mvc()
          .perform(
              post("/api/v1/organizations")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"code":"C1","name":"Clinic Corp","organizationType":"COMPANY","taxCode":"TAX-01","phone":"0901","email":"org@example.test","address":"123 Street","contactFullName":"Nguyen Van A","contactPosition":"Manager","contactPhone":"0901234567","contactEmail":"contact@example.test"}
                      """))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.code").value(HttpStatus.CREATED.value()))
          .andExpect(jsonPath("$.message").value("Organization created"))
          .andExpect(jsonPath("$.data.id").value(orgId.toString()));
    } finally {
      SecurityContextHolder.clearContext();
    }

    ArgumentCaptor<CreateOrganizationCommand> captor =
        ArgumentCaptor.forClass(CreateOrganizationCommand.class);
    verify(createOrganizationUseCase).execute(captor.capture(), eq(actorId));
    CreateOrganizationCommand captured = captor.getValue();
    assertThat(captured.name()).isEqualTo("Clinic Corp");
    assertThat(captured.taxCode()).isEqualTo("TAX-01");
    assertThat(captured.address()).isEqualTo("123 Street");
    assertThat(captured.contactFullName()).isEqualTo("Nguyen Van A");
    assertThat(captured.contactPhone()).isEqualTo("0901234567");
    assertThat(captured.contactPosition()).isEqualTo("Manager");
  }

  @Test
  void invalidCreateTransportIsRejectedBeforeTheUseCase() throws Exception {
    UserPrincipal principal =
        UserPrincipal.builder()
            .userId(UUID.randomUUID())
            .staffId(UUID.randomUUID())
            .patientId(null)
            .username("staff")
            .principalType("STAFF")
            .roleAssignments(List.of())
            .idleExpiresAt(Instant.now())
            .absoluteExpiresAt(Instant.now().plusSeconds(3600))
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(principal, "test", List.of()));
    try {
      mvc()
          .perform(
              post("/api/v1/organizations").contentType(MediaType.APPLICATION_JSON).content("{}"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.result").value("NG"));
    } finally {
      SecurityContextHolder.clearContext();
    }
    org.mockito.Mockito.verifyNoInteractions(createOrganizationUseCase);
  }

  @Test
  void getBindsOrganizationIdAndReturnsEnvelope() throws Exception {
    UUID orgId = UUID.randomUUID();
    OrganizationResponse expectedResponse =
        OrganizationResponse.builder().id(orgId).name("Clinic Corp").status("ACTIVE").build();

    when(getOrganizationUseCase.execute(orgId)).thenReturn(expectedResponse);

    mvc()
        .perform(get("/api/v1/organizations/{organizationId}", orgId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(HttpStatus.OK.value()))
        .andExpect(jsonPath("$.data.id").value(orgId.toString()))
        .andExpect(jsonPath("$.data.name").value("Clinic Corp"));
    verify(getOrganizationUseCase).execute(orgId);
  }

  @Test
  void missingOrganizationUsesCentralNotFoundResponse() throws Exception {
    UUID orgId = UUID.randomUUID();
    when(getOrganizationUseCase.execute(orgId))
        .thenThrow(new ResourceNotFoundException("Organization"));

    mvc()
        .perform(get("/api/v1/organizations/{organizationId}", orgId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.result").value("NG"))
        .andExpect(jsonPath("$.code").value(HttpStatus.NOT_FOUND.value()));
  }

  @Test
  void updateBindsPrincipalAndReturnsUpdatedResponse() throws Exception {
    UUID orgId = UUID.randomUUID();
    UpdateOrganizationRequest request =
        UpdateOrganizationRequest.builder()
            .code("C1")
            .organizationType("COMPANY")
            .phone("0901")
            .email("org@example.test")
            .contactEmail("contact@example.test")
            .rowVersion(3L)
            .name("Clinic Corp New")
            .taxCode("TAX-02")
            .address("456 Avenue")
            .contactFullName("Tran Van B")
            .contactPhone("0987654321")
            .contactPosition("Director")
            .build();

    OrganizationResponse expectedResponse =
        OrganizationResponse.builder()
            .id(orgId)
            .name("Clinic Corp New")
            .taxCode("TAX-02")
            .address("456 Avenue")
            .contactFullName("Tran Van B")
            .contactPhone("0987654321")
            .contactPosition("Director")
            .status("ACTIVE")
            .build();

    UUID actorId = UUID.randomUUID();
    UserPrincipal principal = principal(actorId);
    when(updateOrganizationUseCase.execute(
            eq(orgId), any(UpdateOrganizationCommand.class), eq(actorId)))
        .thenReturn(expectedResponse);

    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(principal, "test", List.of()));
    try {
      mvc()
          .perform(
              put("/api/v1/organizations/{organizationId}", orgId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"code":"C1","name":"Clinic Corp New","organizationType":"COMPANY","taxCode":"TAX-02","phone":"0901","email":"org@example.test","address":"456 Avenue","contactFullName":"Tran Van B","contactPosition":"Director","contactPhone":"0987654321","contactEmail":"contact@example.test","rowVersion":3}
                      """))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(HttpStatus.OK.value()))
          .andExpect(jsonPath("$.message").value("Organization updated"))
          .andExpect(jsonPath("$.data.id").value(orgId.toString()));
    } finally {
      SecurityContextHolder.clearContext();
    }

    ArgumentCaptor<UpdateOrganizationCommand> captor =
        ArgumentCaptor.forClass(UpdateOrganizationCommand.class);
    verify(updateOrganizationUseCase).execute(eq(orgId), captor.capture(), eq(actorId));
    UpdateOrganizationCommand captured = captor.getValue();
    assertThat(captured.name()).isEqualTo("Clinic Corp New");
    assertThat(captured.rowVersion()).isEqualTo(3L);
    assertThat(captured.taxCode()).isEqualTo("TAX-02");
    assertThat(captured.address()).isEqualTo("456 Avenue");
    assertThat(captured.contactFullName()).isEqualTo("Tran Van B");
    assertThat(captured.contactPhone()).isEqualTo("0987654321");
    assertThat(captured.contactPosition()).isEqualTo("Director");
  }

  @Test
  void updateRequiresExpectedRowVersion() throws Exception {
    UUID orgId = UUID.randomUUID();
    UserPrincipal principal = principal(UUID.randomUUID());
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(principal, "test", List.of()));
    try {
      mvc()
          .perform(
              put("/api/v1/organizations/{organizationId}", orgId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"code":"C1","name":"Clinic Corp New","organizationType":"COMPANY","phone":"0901","email":"org@example.test","address":"456 Avenue","contactFullName":"Tran Van B","contactPhone":"0987654321","contactEmail":"contact@example.test"}
                      """))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.result").value("NG"));
    } finally {
      SecurityContextHolder.clearContext();
    }
    org.mockito.Mockito.verifyNoInteractions(updateOrganizationUseCase);
  }

  private UserPrincipal principal(UUID userId) {
    return UserPrincipal.builder()
        .userId(userId)
        .staffId(UUID.randomUUID())
        .patientId(null)
        .username("staff")
        .principalType("STAFF")
        .roleAssignments(List.of())
        .idleExpiresAt(Instant.now())
        .absoluteExpiresAt(Instant.now().plusSeconds(3600))
        .build();
  }

  private MockMvc mvc() {
    return MockMvcBuilders.standaloneSetup(controller)
        .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
        .setControllerAdvice(
            new GlobalExceptionHandler(new ApiResponseWriter(JsonMapper.builder().build())))
        .build();
  }
}

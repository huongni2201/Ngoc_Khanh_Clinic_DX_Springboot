package com.ngockhanh.clinic.healthexamination.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ngockhanh.clinic.healthexamination.api.request.OrganizationListRequest;
import com.ngockhanh.clinic.healthexamination.api.request.OrganizationRequest;
import com.ngockhanh.clinic.healthexamination.application.command.CreateOrganizationCommand;
import com.ngockhanh.clinic.healthexamination.application.command.UpdateOrganizationCommand;
import com.ngockhanh.clinic.healthexamination.application.query.OrganizationListQuery;
import com.ngockhanh.clinic.healthexamination.application.response.OrganizationResponse;
import com.ngockhanh.clinic.healthexamination.application.usecase.CreateOrganizationUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.DeactivateOrganizationUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.GetOrganizationUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.ListOrganizationsUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.UpdateOrganizationUseCase;
import com.ngockhanh.clinic.shared.web.ApiResponse;
import com.ngockhanh.clinic.shared.web.PageResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class OrganizationControllerTest {

  private CreateOrganizationUseCase createOrganizationUseCase;
  private GetOrganizationUseCase getOrganizationUseCase;
  private ListOrganizationsUseCase listOrganizationsUseCase;
  private UpdateOrganizationUseCase updateOrganizationUseCase;
  private DeactivateOrganizationUseCase deactivateOrganizationUseCase;
  private OrganizationController controller;

  @BeforeEach
  void setUp() {
    createOrganizationUseCase = mock(CreateOrganizationUseCase.class);
    getOrganizationUseCase = mock(GetOrganizationUseCase.class);
    listOrganizationsUseCase = mock(ListOrganizationsUseCase.class);
    updateOrganizationUseCase = mock(UpdateOrganizationUseCase.class);
    deactivateOrganizationUseCase = mock(DeactivateOrganizationUseCase.class);
    controller =
        new OrganizationController(
            createOrganizationUseCase,
            getOrganizationUseCase,
            listOrganizationsUseCase,
            updateOrganizationUseCase,
            deactivateOrganizationUseCase);
  }

  @Test
  void listBuildsQueryAndReturnsPagedOrganizations() {
    OrganizationListRequest request =
        OrganizationListRequest.builder()
            .page(2)
            .size(5)
            .searchKey("Clinic")
            .status("ACTIVE")
            .sortKey("name")
            .sortBy("DESC")
            .build();
    OrganizationResponse item =
        OrganizationResponse.builder()
            .id(UUID.randomUUID())
            .name("Clinic North")
            .status("ACTIVE")
            .build();
    PageResponse<OrganizationResponse> page = new PageResponse<>(List.of(item), 2, 5, 11, 3);
    when(listOrganizationsUseCase.execute(any(OrganizationListQuery.class))).thenReturn(page);

    ResponseEntity<ApiResponse<PageResponse<OrganizationResponse>>> response =
        controller.list(request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().message()).isEqualTo("Organizations retrieved");
    assertThat(response.getBody().data()).isEqualTo(page);
    ArgumentCaptor<OrganizationListQuery> captor =
        ArgumentCaptor.forClass(OrganizationListQuery.class);
    verify(listOrganizationsUseCase).execute(captor.capture());
    assertThat(captor.getValue())
        .isEqualTo(new OrganizationListQuery(2, 5, "Clinic", "ACTIVE", "DESC", "name"));
  }

  @Test
  void createBuildsCommandAndReturnsCreatedResponse() {
    OrganizationRequest request =
        OrganizationRequest.builder()
            .name("Clinic Corp")
            .taxCode("TAX-01")
            .address("123 Street")
            .contactName("Nguyen Van A")
            .contactPhone("0901234567")
            .contactJobTitle("Manager")
            .note("Sample note")
            .build();

    UUID orgId = UUID.randomUUID();
    OrganizationResponse expectedResponse =
        OrganizationResponse.builder()
            .id(orgId)
            .name("Clinic Corp")
            .taxCode("TAX-01")
            .address("123 Street")
            .contactName("Nguyen Van A")
            .contactPhone("0901234567")
            .contactJobTitle("Manager")
            .note("Sample note")
            .status("ACTIVE")
            .build();

    when(createOrganizationUseCase.execute(any(CreateOrganizationCommand.class)))
        .thenReturn(expectedResponse);

    ResponseEntity<ApiResponse<OrganizationResponse>> responseEntity = controller.create(request);

    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(responseEntity.getBody()).isNotNull();
    assertThat(responseEntity.getBody().code()).isEqualTo(HttpStatus.CREATED.value());
    assertThat(responseEntity.getBody().message()).isEqualTo("Organization created");
    assertThat(responseEntity.getBody().data()).isEqualTo(expectedResponse);

    ArgumentCaptor<CreateOrganizationCommand> captor =
        ArgumentCaptor.forClass(CreateOrganizationCommand.class);
    verify(createOrganizationUseCase).execute(captor.capture());
    CreateOrganizationCommand captured = captor.getValue();
    assertThat(captured.name()).isEqualTo("Clinic Corp");
    assertThat(captured.taxCode()).isEqualTo("TAX-01");
    assertThat(captured.address()).isEqualTo("123 Street");
    assertThat(captured.contactName()).isEqualTo("Nguyen Van A");
    assertThat(captured.contactPhone()).isEqualTo("0901234567");
    assertThat(captured.contactJobTitle()).isEqualTo("Manager");
    assertThat(captured.note()).isEqualTo("Sample note");
  }

  @Test
  void getReturnsOrganizationResponse() {
    UUID orgId = UUID.randomUUID();
    OrganizationResponse expectedResponse =
        OrganizationResponse.builder().id(orgId).name("Clinic Corp").status("ACTIVE").build();

    when(getOrganizationUseCase.execute(orgId)).thenReturn(expectedResponse);

    ResponseEntity<ApiResponse<OrganizationResponse>> responseEntity = controller.get(orgId);

    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(responseEntity.getBody()).isNotNull();
    assertThat(responseEntity.getBody().code()).isEqualTo(HttpStatus.OK.value());
    assertThat(responseEntity.getBody().data()).isEqualTo(expectedResponse);
    verify(getOrganizationUseCase).execute(orgId);
  }

  @Test
  void updateBuildsCommandAndReturnsUpdatedResponse() {
    UUID orgId = UUID.randomUUID();
    OrganizationRequest request =
        OrganizationRequest.builder()
            .name("Clinic Corp New")
            .taxCode("TAX-02")
            .address("456 Avenue")
            .contactName("Tran Van B")
            .contactPhone("0987654321")
            .contactJobTitle("Director")
            .note("Updated note")
            .build();

    OrganizationResponse expectedResponse =
        OrganizationResponse.builder()
            .id(orgId)
            .name("Clinic Corp New")
            .taxCode("TAX-02")
            .address("456 Avenue")
            .contactName("Tran Van B")
            .contactPhone("0987654321")
            .contactJobTitle("Director")
            .note("Updated note")
            .status("ACTIVE")
            .build();

    when(updateOrganizationUseCase.execute(eq(orgId), any(UpdateOrganizationCommand.class)))
        .thenReturn(expectedResponse);

    ResponseEntity<ApiResponse<OrganizationResponse>> responseEntity =
        controller.update(orgId, request);

    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(responseEntity.getBody()).isNotNull();
    assertThat(responseEntity.getBody().code()).isEqualTo(HttpStatus.OK.value());
    assertThat(responseEntity.getBody().message()).isEqualTo("Organization updated");
    assertThat(responseEntity.getBody().data()).isEqualTo(expectedResponse);

    ArgumentCaptor<UpdateOrganizationCommand> captor =
        ArgumentCaptor.forClass(UpdateOrganizationCommand.class);
    verify(updateOrganizationUseCase).execute(eq(orgId), captor.capture());
    UpdateOrganizationCommand captured = captor.getValue();
    assertThat(captured.name()).isEqualTo("Clinic Corp New");
    assertThat(captured.taxCode()).isEqualTo("TAX-02");
    assertThat(captured.address()).isEqualTo("456 Avenue");
    assertThat(captured.contactName()).isEqualTo("Tran Van B");
    assertThat(captured.contactPhone()).isEqualTo("0987654321");
    assertThat(captured.contactJobTitle()).isEqualTo("Director");
    assertThat(captured.note()).isEqualTo("Updated note");
  }

  @Test
  void deleteDeactivatesOrganizationAndReturnsOk() {
    UUID orgId = UUID.randomUUID();

    ResponseEntity<ApiResponse<Void>> responseEntity = controller.delete(orgId);

    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(responseEntity.getBody()).isNotNull();
    assertThat(responseEntity.getBody().code()).isEqualTo(HttpStatus.OK.value());
    assertThat(responseEntity.getBody().message()).isEqualTo("Organization deactivated");
    verify(deactivateOrganizationUseCase).execute(orgId);
  }
}

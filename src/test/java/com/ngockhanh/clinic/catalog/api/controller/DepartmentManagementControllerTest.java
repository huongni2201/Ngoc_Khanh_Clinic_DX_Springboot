package com.ngockhanh.clinic.catalog.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ngockhanh.clinic.catalog.application.command.CreateDepartmentCommand;
import com.ngockhanh.clinic.catalog.application.command.DeactivateDepartmentCommand;
import com.ngockhanh.clinic.catalog.application.command.ReactivateDepartmentCommand;
import com.ngockhanh.clinic.catalog.application.command.UpdateDepartmentCommand;
import com.ngockhanh.clinic.catalog.application.query.DepartmentListQuery;
import com.ngockhanh.clinic.catalog.application.response.DepartmentItemResponse;
import com.ngockhanh.clinic.catalog.application.response.DepartmentResponse;
import com.ngockhanh.clinic.catalog.application.usecase.CreateDepartmentUseCase;
import com.ngockhanh.clinic.catalog.application.usecase.DeactivateDepartmentUseCase;
import com.ngockhanh.clinic.catalog.application.usecase.GetDepartmentUseCase;
import com.ngockhanh.clinic.catalog.application.usecase.ListDepartmentsUseCase;
import com.ngockhanh.clinic.catalog.application.usecase.ReactivateDepartmentUseCase;
import com.ngockhanh.clinic.catalog.application.usecase.UpdateDepartmentUseCase;
import com.ngockhanh.clinic.shared.web.GlobalExceptionHandler;
import com.ngockhanh.clinic.shared.web.PageResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DepartmentManagementControllerTest {
  private final ListDepartmentsUseCase listUseCase = mock(ListDepartmentsUseCase.class);
  private final GetDepartmentUseCase getUseCase = mock(GetDepartmentUseCase.class);
  private final CreateDepartmentUseCase createUseCase = mock(CreateDepartmentUseCase.class);
  private final UpdateDepartmentUseCase updateUseCase = mock(UpdateDepartmentUseCase.class);
  private final DeactivateDepartmentUseCase deactivateUseCase =
      mock(DeactivateDepartmentUseCase.class);
  private final ReactivateDepartmentUseCase reactivateUseCase =
      mock(ReactivateDepartmentUseCase.class);

  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcBuilders.standaloneSetup(
                new DepartmentManagementController(
                    listUseCase,
                    getUseCase,
                    createUseCase,
                    updateUseCase,
                    deactivateUseCase,
                    reactivateUseCase))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void listReturnsPageOfDepartments() throws Exception {
    UUID id = UUID.randomUUID();
    when(listUseCase.execute(any(DepartmentListQuery.class), any()))
        .thenReturn(
            PageResponse.<DepartmentItemResponse>builder()
                .items(
                    List.of(
                        new DepartmentItemResponse(
                            id,
                            "KHAM",
                            "Khoa Khám Bệnh",
                            "CLINICAL",
                            true,
                            0L,
                            Instant.now(),
                            Instant.now())))
                .page(1)
                .size(10)
                .totalElements(1)
                .totalPages(1)
                .build());

    mvc.perform(
            get("/api/v1/catalog/management/departments")
                .param("page", "1")
                .param("size", "10")
                .param("searchKey", "kham")
                .param("departmentType", "CLINICAL")
                .param("active", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(200))
        .andExpect(jsonPath("$.data.items[0].id").value(id.toString()))
        .andExpect(jsonPath("$.data.items[0].code").value("KHAM"))
        .andExpect(jsonPath("$.data.items[0].name").value("Khoa Khám Bệnh"))
        .andExpect(jsonPath("$.data.items[0].active").value(true));

    var queryCaptor = ArgumentCaptor.forClass(DepartmentListQuery.class);
    verify(listUseCase).execute(queryCaptor.capture(), any());
    DepartmentListQuery captured = queryCaptor.getValue();
    assertThat(captured.page()).isEqualTo(1);
    assertThat(captured.size()).isEqualTo(10);
    assertThat(captured.searchKey()).isEqualTo("kham");
    assertThat(captured.departmentType()).isEqualTo("CLINICAL");
    assertThat(captured.active()).isTrue();
  }

  @Test
  void getReturnsDepartmentDetail() throws Exception {
    UUID id = UUID.randomUUID();
    when(getUseCase.execute(eq(id), any()))
        .thenReturn(
            new DepartmentResponse(
                id, "KHAM", "Khoa Khám Bệnh", "CLINICAL", true, 0L, Instant.now(), Instant.now()));

    mvc.perform(get("/api/v1/catalog/management/departments/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(200))
        .andExpect(jsonPath("$.data.id").value(id.toString()))
        .andExpect(jsonPath("$.data.code").value("KHAM"))
        .andExpect(jsonPath("$.data.departmentType").value("CLINICAL"));
  }

  @Test
  void createReturns201WithLocation() throws Exception {
    UUID id = UUID.randomUUID();
    when(createUseCase.execute(any(CreateDepartmentCommand.class), any()))
        .thenReturn(
            new DepartmentResponse(
                id,
                "CDHA",
                "Khoa Chẩn Đoán",
                "DIAGNOSTIC",
                true,
                0L,
                Instant.now(),
                Instant.now()));

    mvc.perform(
            post("/api/v1/catalog/management/departments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "code": "CDHA",
                      "name": "Khoa Chẩn Đoán",
                      "departmentType": "DIAGNOSTIC"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/v1/catalog/management/departments/" + id))
        .andExpect(jsonPath("$.code").value(201))
        .andExpect(jsonPath("$.data.id").value(id.toString()))
        .andExpect(jsonPath("$.data.code").value("CDHA"));
  }

  @Test
  void updateReturns200WithUpdatedDepartment() throws Exception {
    UUID id = UUID.randomUUID();
    when(updateUseCase.execute(any(UpdateDepartmentCommand.class), any()))
        .thenReturn(
            new DepartmentResponse(
                id,
                "KHAM",
                "Khoa Khám Cấp Cứu",
                "CLINICAL",
                true,
                1L,
                Instant.now(),
                Instant.now()));

    mvc.perform(
            put("/api/v1/catalog/management/departments/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Khoa Khám Cấp Cứu",
                      "departmentType": "CLINICAL",
                      "rowVersion": 0
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(200))
        .andExpect(jsonPath("$.data.name").value("Khoa Khám Cấp Cứu"))
        .andExpect(jsonPath("$.data.rowVersion").value(1));
  }

  @Test
  void deactivateReturns204NoContent() throws Exception {
    UUID id = UUID.randomUUID();

    mvc.perform(delete("/api/v1/catalog/management/departments/{id}", id).param("rowVersion", "0"))
        .andExpect(status().isNoContent());

    var cmdCaptor = ArgumentCaptor.forClass(DeactivateDepartmentCommand.class);
    verify(deactivateUseCase).execute(cmdCaptor.capture(), any());
    assertThat(cmdCaptor.getValue().id()).isEqualTo(id);
    assertThat(cmdCaptor.getValue().rowVersion()).isZero();
  }

  @Test
  void reactivateReturns200WithReactivatedDepartment() throws Exception {
    UUID id = UUID.randomUUID();
    when(reactivateUseCase.execute(any(ReactivateDepartmentCommand.class), any()))
        .thenReturn(
            new DepartmentResponse(
                id, "KHAM", "Khoa Khám Bệnh", "CLINICAL", true, 1L, Instant.now(), Instant.now()));

    mvc.perform(
            post("/api/v1/catalog/management/departments/{id}/reactivate", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "rowVersion": 0
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(200))
        .andExpect(jsonPath("$.data.active").value(true))
        .andExpect(jsonPath("$.data.rowVersion").value(1));
  }

  @Test
  void invalidPayloadIsRejectedWith400() throws Exception {
    mvc.perform(
            post("/api/v1/catalog/management/departments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "code": "",
                      "name": "",
                      "departmentType": ""
                    }
                    """))
        .andExpect(status().isBadRequest());
  }
}

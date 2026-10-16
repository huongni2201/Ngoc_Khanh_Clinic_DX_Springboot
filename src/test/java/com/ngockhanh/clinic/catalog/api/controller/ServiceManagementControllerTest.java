package com.ngockhanh.clinic.catalog.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

import com.ngockhanh.clinic.catalog.application.command.CreateServiceCommand;
import com.ngockhanh.clinic.catalog.application.command.DeactivateServiceCommand;
import com.ngockhanh.clinic.catalog.application.command.ReactivateServiceCommand;
import com.ngockhanh.clinic.catalog.application.command.UpdateServiceCommand;
import com.ngockhanh.clinic.catalog.application.query.ManagedServiceListQuery;
import com.ngockhanh.clinic.catalog.application.response.ManagedServiceItemResponse;
import com.ngockhanh.clinic.catalog.application.response.ManagedServiceResponse;
import com.ngockhanh.clinic.catalog.application.usecase.CreateManagedServiceUseCase;
import com.ngockhanh.clinic.catalog.application.usecase.DeactivateManagedServiceUseCase;
import com.ngockhanh.clinic.catalog.application.usecase.GetManagedServiceUseCase;
import com.ngockhanh.clinic.catalog.application.usecase.ListManagedServicesUseCase;
import com.ngockhanh.clinic.catalog.application.usecase.ReactivateManagedServiceUseCase;
import com.ngockhanh.clinic.catalog.application.usecase.UpdateManagedServiceUseCase;
import com.ngockhanh.clinic.shared.web.GlobalExceptionHandler;
import com.ngockhanh.clinic.shared.web.PageResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ServiceManagementControllerTest {
  private final ListManagedServicesUseCase listUseCase = mock(ListManagedServicesUseCase.class);
  private final GetManagedServiceUseCase getUseCase = mock(GetManagedServiceUseCase.class);
  private final CreateManagedServiceUseCase createUseCase = mock(CreateManagedServiceUseCase.class);
  private final UpdateManagedServiceUseCase updateUseCase = mock(UpdateManagedServiceUseCase.class);
  private final DeactivateManagedServiceUseCase deactivateUseCase =
      mock(DeactivateManagedServiceUseCase.class);
  private final ReactivateManagedServiceUseCase reactivateUseCase =
      mock(ReactivateManagedServiceUseCase.class);

  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcBuilders.standaloneSetup(
                new ServiceManagementController(
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
  void listReturnsPageOfServices() throws Exception {
    UUID id = UUID.randomUUID();
    UUID deptId = UUID.randomUUID();
    when(listUseCase.execute(any(ManagedServiceListQuery.class), any()))
        .thenReturn(
            PageResponse.<ManagedServiceItemResponse>builder()
                .items(
                    List.of(
                        new ManagedServiceItemResponse(
                            id,
                            "XRAY-01",
                            "Chụp X-Quang Ngực",
                            "XRAY",
                            deptId,
                            "Khoa Chẩn Đoán",
                            null,
                            null,
                            new BigDecimal("200000.00"),
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
            get("/api/v1/catalog/management/services")
                .param("page", "1")
                .param("size", "10")
                .param("serviceType", "XRAY"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(200))
        .andExpect(jsonPath("$.data.items[0].code").value("XRAY-01"))
        .andExpect(jsonPath("$.data.items[0].serviceType").value("XRAY"));

    var captor = ArgumentCaptor.forClass(ManagedServiceListQuery.class);
    verify(listUseCase).execute(captor.capture(), any());
    assertThat(captor.getValue().serviceType()).isEqualTo("XRAY");
  }

  @Test
  void createReturns201WithLocation() throws Exception {
    UUID id = UUID.randomUUID();
    UUID deptId = UUID.randomUUID();
    when(createUseCase.execute(any(CreateServiceCommand.class), any()))
        .thenReturn(
            new ManagedServiceResponse(
                id,
                "XRAY-01",
                "Chụp X-Quang Ngực",
                "XRAY",
                deptId,
                "Khoa Chẩn Đoán",
                null,
                null,
                new BigDecimal("200000.00"),
                "Tháo trang sức",
                true,
                0L,
                Instant.now(),
                Instant.now()));

    mvc.perform(
            post("/api/v1/catalog/management/services")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "code": "XRAY-01",
                      "name": "Chụp X-Quang Ngực",
                      "serviceType": "XRAY",
                      "performingDepartmentId": "%s",
                      "unitPrice": 200000.00,
                      "preparationInstruction": "Tháo trang sức"
                    }
                    """
                        .formatted(deptId)))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/v1/catalog/management/services/" + id))
        .andExpect(jsonPath("$.code").value(201))
        .andExpect(jsonPath("$.data.unitPrice").value(200000.00));
  }

  @Test
  void updateReturns200() throws Exception {
    UUID id = UUID.randomUUID();
    UUID deptId = UUID.randomUUID();
    when(updateUseCase.execute(any(UpdateServiceCommand.class), any()))
        .thenReturn(
            new ManagedServiceResponse(
                id,
                "XRAY-01",
                "Chụp X-Quang Mới",
                "XRAY",
                deptId,
                "Khoa Chẩn Đoán",
                null,
                null,
                new BigDecimal("250000.00"),
                null,
                true,
                1L,
                Instant.now(),
                Instant.now()));

    mvc.perform(
            put("/api/v1/catalog/management/services/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Chụp X-Quang Mới",
                      "serviceType": "XRAY",
                      "unitPrice": 250000.00,
                      "rowVersion": 0
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("Chụp X-Quang Mới"))
        .andExpect(jsonPath("$.data.rowVersion").value(1));
  }

  @Test
  void deactivateReturns204() throws Exception {
    UUID id = UUID.randomUUID();

    mvc.perform(delete("/api/v1/catalog/management/services/{id}", id).param("rowVersion", "0"))
        .andExpect(status().isNoContent());

    var captor = ArgumentCaptor.forClass(DeactivateServiceCommand.class);
    verify(deactivateUseCase).execute(captor.capture(), any());
    assertThat(captor.getValue().id()).isEqualTo(id);
    assertThat(captor.getValue().rowVersion()).isZero();
  }

  @Test
  void reactivateReturns200() throws Exception {
    UUID id = UUID.randomUUID();
    UUID deptId = UUID.randomUUID();
    when(reactivateUseCase.execute(any(ReactivateServiceCommand.class), any()))
        .thenReturn(
            new ManagedServiceResponse(
                id,
                "XRAY-01",
                "Chụp X-Quang",
                "XRAY",
                deptId,
                "Khoa Chẩn Đoán",
                null,
                null,
                new BigDecimal("200000.00"),
                null,
                true,
                1L,
                Instant.now(),
                Instant.now()));

    mvc.perform(
            post("/api/v1/catalog/management/services/{id}/reactivate", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "rowVersion": 0 }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.active").value(true));
  }
}

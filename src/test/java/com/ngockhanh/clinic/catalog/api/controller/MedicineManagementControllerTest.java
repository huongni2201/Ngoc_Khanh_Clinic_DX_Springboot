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

import com.ngockhanh.clinic.catalog.application.command.CreateMedicineCommand;
import com.ngockhanh.clinic.catalog.application.command.DeactivateMedicineCommand;
import com.ngockhanh.clinic.catalog.application.command.ReactivateMedicineCommand;
import com.ngockhanh.clinic.catalog.application.command.UpdateMedicineCommand;
import com.ngockhanh.clinic.catalog.application.query.MedicineListQuery;
import com.ngockhanh.clinic.catalog.application.response.MedicineItemResponse;
import com.ngockhanh.clinic.catalog.application.response.MedicineResponse;
import com.ngockhanh.clinic.catalog.application.usecase.CreateMedicineUseCase;
import com.ngockhanh.clinic.catalog.application.usecase.DeactivateMedicineUseCase;
import com.ngockhanh.clinic.catalog.application.usecase.GetMedicineUseCase;
import com.ngockhanh.clinic.catalog.application.usecase.ListMedicinesUseCase;
import com.ngockhanh.clinic.catalog.application.usecase.ReactivateMedicineUseCase;
import com.ngockhanh.clinic.catalog.application.usecase.UpdateMedicineUseCase;
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

class MedicineManagementControllerTest {
  private final ListMedicinesUseCase listUseCase = mock(ListMedicinesUseCase.class);
  private final GetMedicineUseCase getUseCase = mock(GetMedicineUseCase.class);
  private final CreateMedicineUseCase createUseCase = mock(CreateMedicineUseCase.class);
  private final UpdateMedicineUseCase updateUseCase = mock(UpdateMedicineUseCase.class);
  private final DeactivateMedicineUseCase deactivateUseCase = mock(DeactivateMedicineUseCase.class);
  private final ReactivateMedicineUseCase reactivateUseCase = mock(ReactivateMedicineUseCase.class);

  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcBuilders.standaloneSetup(
                new MedicineManagementController(
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
  void listReturnsPageOfMedicines() throws Exception {
    UUID id = UUID.randomUUID();
    when(listUseCase.execute(any(MedicineListQuery.class), any()))
        .thenReturn(
            PageResponse.<MedicineItemResponse>builder()
                .items(
                    List.of(
                        new MedicineItemResponse(
                            id,
                            "PARA-500",
                            "Paracetamol",
                            "Panadol",
                            "500mg",
                            "Viên nén",
                            "Viên",
                            true,
                            0L,
                            Instant.now(),
                            Instant.now())))
                .page(1)
                .size(10)
                .totalElements(1)
                .totalPages(1)
                .build());

    mvc.perform(get("/api/v1/catalog/management/medicines"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(200))
        .andExpect(jsonPath("$.data.items[0].code").value("PARA-500"))
        .andExpect(jsonPath("$.data.items[0].genericName").value("Paracetamol"));
  }

  @Test
  void createReturns201WithLocation() throws Exception {
    UUID id = UUID.randomUUID();
    when(createUseCase.execute(any(CreateMedicineCommand.class), any()))
        .thenReturn(
            new MedicineResponse(
                id,
                "AMOX-500",
                "Amoxicillin",
                "Clamoxyl",
                "500mg",
                "Viên nang",
                "Viên",
                true,
                0L,
                Instant.now(),
                Instant.now()));

    mvc.perform(
            post("/api/v1/catalog/management/medicines")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "code": "AMOX-500",
                      "genericName": "Amoxicillin",
                      "brandName": "Clamoxyl",
                      "strength": "500mg",
                      "dosageForm": "Viên nang",
                      "unit": "Viên"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/v1/catalog/management/medicines/" + id))
        .andExpect(jsonPath("$.code").value(201))
        .andExpect(jsonPath("$.data.code").value("AMOX-500"));
  }

  @Test
  void updateReturns200() throws Exception {
    UUID id = UUID.randomUUID();
    when(updateUseCase.execute(any(UpdateMedicineCommand.class), any()))
        .thenReturn(
            new MedicineResponse(
                id,
                "PARA-500",
                "Paracetamol",
                "Hapacol",
                "500mg",
                "Viên sủi",
                "Viên",
                true,
                1L,
                Instant.now(),
                Instant.now()));

    mvc.perform(
            put("/api/v1/catalog/management/medicines/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "genericName": "Paracetamol",
                      "brandName": "Hapacol",
                      "strength": "500mg",
                      "dosageForm": "Viên sủi",
                      "unit": "Viên",
                      "rowVersion": 0
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.brandName").value("Hapacol"))
        .andExpect(jsonPath("$.data.rowVersion").value(1));
  }

  @Test
  void deactivateReturns204() throws Exception {
    UUID id = UUID.randomUUID();

    mvc.perform(delete("/api/v1/catalog/management/medicines/{id}", id).param("rowVersion", "0"))
        .andExpect(status().isNoContent());

    var captor = ArgumentCaptor.forClass(DeactivateMedicineCommand.class);
    verify(deactivateUseCase).execute(captor.capture(), any());
    assertThat(captor.getValue().id()).isEqualTo(id);
    assertThat(captor.getValue().rowVersion()).isZero();
  }

  @Test
  void reactivateReturns200() throws Exception {
    UUID id = UUID.randomUUID();
    when(reactivateUseCase.execute(any(ReactivateMedicineCommand.class), any()))
        .thenReturn(
            new MedicineResponse(
                id,
                "PARA-500",
                "Paracetamol",
                "Panadol",
                "500mg",
                "Viên nén",
                "Viên",
                true,
                1L,
                Instant.now(),
                Instant.now()));

    mvc.perform(
            post("/api/v1/catalog/management/medicines/{id}/reactivate", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "rowVersion": 0 }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.active").value(true));
  }
}

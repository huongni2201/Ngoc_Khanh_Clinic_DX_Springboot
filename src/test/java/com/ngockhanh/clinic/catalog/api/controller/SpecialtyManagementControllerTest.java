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

import com.ngockhanh.clinic.catalog.application.command.CreateSpecialtyCommand;
import com.ngockhanh.clinic.catalog.application.command.DeactivateSpecialtyCommand;
import com.ngockhanh.clinic.catalog.application.command.ReactivateSpecialtyCommand;
import com.ngockhanh.clinic.catalog.application.command.UpdateSpecialtyCommand;
import com.ngockhanh.clinic.catalog.application.query.SpecialtyListQuery;
import com.ngockhanh.clinic.catalog.application.response.SpecialtyItemResponse;
import com.ngockhanh.clinic.catalog.application.response.SpecialtyResponse;
import com.ngockhanh.clinic.catalog.application.usecase.CreateSpecialtyUseCase;
import com.ngockhanh.clinic.catalog.application.usecase.DeactivateSpecialtyUseCase;
import com.ngockhanh.clinic.catalog.application.usecase.GetSpecialtyUseCase;
import com.ngockhanh.clinic.catalog.application.usecase.ListSpecialtiesUseCase;
import com.ngockhanh.clinic.catalog.application.usecase.ReactivateSpecialtyUseCase;
import com.ngockhanh.clinic.catalog.application.usecase.UpdateSpecialtyUseCase;
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

class SpecialtyManagementControllerTest {
  private final ListSpecialtiesUseCase listUseCase = mock(ListSpecialtiesUseCase.class);
  private final GetSpecialtyUseCase getUseCase = mock(GetSpecialtyUseCase.class);
  private final CreateSpecialtyUseCase createUseCase = mock(CreateSpecialtyUseCase.class);
  private final UpdateSpecialtyUseCase updateUseCase = mock(UpdateSpecialtyUseCase.class);
  private final DeactivateSpecialtyUseCase deactivateUseCase =
      mock(DeactivateSpecialtyUseCase.class);
  private final ReactivateSpecialtyUseCase reactivateUseCase =
      mock(ReactivateSpecialtyUseCase.class);

  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcBuilders.standaloneSetup(
                new SpecialtyManagementController(
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
  void listReturnsPageOfSpecialties() throws Exception {
    UUID id = UUID.randomUUID();
    when(listUseCase.execute(any(SpecialtyListQuery.class), any()))
        .thenReturn(
            PageResponse.<SpecialtyItemResponse>builder()
                .items(
                    List.of(
                        new SpecialtyItemResponse(
                            id, "NOI", "Nội Khoa", true, 0L, Instant.now(), Instant.now())))
                .page(1)
                .size(10)
                .totalElements(1)
                .totalPages(1)
                .build());

    mvc.perform(get("/api/v1/catalog/management/specialties"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(200))
        .andExpect(jsonPath("$.data.items[0].code").value("NOI"));
  }

  @Test
  void createReturns201WithLocation() throws Exception {
    UUID id = UUID.randomUUID();
    when(createUseCase.execute(any(CreateSpecialtyCommand.class), any()))
        .thenReturn(
            new SpecialtyResponse(
                id, "NGOAI", "Ngoại Khoa", true, 0L, Instant.now(), Instant.now()));

    mvc.perform(
            post("/api/v1/catalog/management/specialties")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "code": "NGOAI",
                      "name": "Ngoại Khoa"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/v1/catalog/management/specialties/" + id))
        .andExpect(jsonPath("$.code").value(201))
        .andExpect(jsonPath("$.data.code").value("NGOAI"));
  }

  @Test
  void updateReturns200() throws Exception {
    UUID id = UUID.randomUUID();
    when(updateUseCase.execute(any(UpdateSpecialtyCommand.class), any()))
        .thenReturn(
            new SpecialtyResponse(
                id, "NOI", "Nội Tổng Hợp", true, 1L, Instant.now(), Instant.now()));

    mvc.perform(
            put("/api/v1/catalog/management/specialties/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Nội Tổng Hợp",
                      "rowVersion": 0
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("Nội Tổng Hợp"))
        .andExpect(jsonPath("$.data.rowVersion").value(1));
  }

  @Test
  void deactivateReturns204() throws Exception {
    UUID id = UUID.randomUUID();

    mvc.perform(delete("/api/v1/catalog/management/specialties/{id}", id).param("rowVersion", "0"))
        .andExpect(status().isNoContent());

    var captor = ArgumentCaptor.forClass(DeactivateSpecialtyCommand.class);
    verify(deactivateUseCase).execute(captor.capture(), any());
    assertThat(captor.getValue().id()).isEqualTo(id);
    assertThat(captor.getValue().rowVersion()).isZero();
  }

  @Test
  void reactivateReturns200() throws Exception {
    UUID id = UUID.randomUUID();
    when(reactivateUseCase.execute(any(ReactivateSpecialtyCommand.class), any()))
        .thenReturn(
            new SpecialtyResponse(id, "NOI", "Nội Khoa", true, 1L, Instant.now(), Instant.now()));

    mvc.perform(
            post("/api/v1/catalog/management/specialties/{id}/reactivate", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "rowVersion": 0 }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.active").value(true));
  }
}

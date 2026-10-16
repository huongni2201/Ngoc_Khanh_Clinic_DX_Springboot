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

import com.ngockhanh.clinic.catalog.application.command.CreateRoomCommand;
import com.ngockhanh.clinic.catalog.application.command.DeactivateRoomCommand;
import com.ngockhanh.clinic.catalog.application.command.ReactivateRoomCommand;
import com.ngockhanh.clinic.catalog.application.command.UpdateRoomCommand;
import com.ngockhanh.clinic.catalog.application.query.RoomListQuery;
import com.ngockhanh.clinic.catalog.application.response.RoomItemResponse;
import com.ngockhanh.clinic.catalog.application.response.RoomResponse;
import com.ngockhanh.clinic.catalog.application.usecase.CreateRoomUseCase;
import com.ngockhanh.clinic.catalog.application.usecase.DeactivateRoomUseCase;
import com.ngockhanh.clinic.catalog.application.usecase.GetRoomUseCase;
import com.ngockhanh.clinic.catalog.application.usecase.ListRoomsUseCase;
import com.ngockhanh.clinic.catalog.application.usecase.ReactivateRoomUseCase;
import com.ngockhanh.clinic.catalog.application.usecase.UpdateRoomUseCase;
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

class RoomManagementControllerTest {
  private final ListRoomsUseCase listUseCase = mock(ListRoomsUseCase.class);
  private final GetRoomUseCase getUseCase = mock(GetRoomUseCase.class);
  private final CreateRoomUseCase createUseCase = mock(CreateRoomUseCase.class);
  private final UpdateRoomUseCase updateUseCase = mock(UpdateRoomUseCase.class);
  private final DeactivateRoomUseCase deactivateUseCase = mock(DeactivateRoomUseCase.class);
  private final ReactivateRoomUseCase reactivateUseCase = mock(ReactivateRoomUseCase.class);

  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcBuilders.standaloneSetup(
                new RoomManagementController(
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
  void listReturnsPageOfRooms() throws Exception {
    UUID id = UUID.randomUUID();
    UUID deptId = UUID.randomUUID();
    when(listUseCase.execute(any(RoomListQuery.class), any()))
        .thenReturn(
            PageResponse.<RoomItemResponse>builder()
                .items(
                    List.of(
                        new RoomItemResponse(
                            id,
                            "P101",
                            "Phòng 101",
                            deptId,
                            "Khoa Khám",
                            "EXAMINATION",
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
            get("/api/v1/catalog/management/rooms")
                .param("page", "1")
                .param("size", "10")
                .param("departmentId", deptId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(200))
        .andExpect(jsonPath("$.data.items[0].id").value(id.toString()))
        .andExpect(jsonPath("$.data.items[0].code").value("P101"))
        .andExpect(jsonPath("$.data.items[0].departmentName").value("Khoa Khám"));

    var captor = ArgumentCaptor.forClass(RoomListQuery.class);
    verify(listUseCase).execute(captor.capture(), any());
    assertThat(captor.getValue().departmentId()).isEqualTo(deptId);
  }

  @Test
  void createReturns201WithLocation() throws Exception {
    UUID id = UUID.randomUUID();
    UUID deptId = UUID.randomUUID();
    when(createUseCase.execute(any(CreateRoomCommand.class), any()))
        .thenReturn(
            new RoomResponse(
                id,
                "P102",
                "Phòng 102",
                deptId,
                "Khoa Khám",
                "EXAMINATION",
                true,
                0L,
                Instant.now(),
                Instant.now()));

    mvc.perform(
            post("/api/v1/catalog/management/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "code": "P102",
                      "name": "Phòng 102",
                      "departmentId": "%s",
                      "roomType": "EXAMINATION"
                    }
                    """
                        .formatted(deptId)))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/v1/catalog/management/rooms/" + id))
        .andExpect(jsonPath("$.code").value(201))
        .andExpect(jsonPath("$.data.code").value("P102"));
  }

  @Test
  void updateReturns200() throws Exception {
    UUID id = UUID.randomUUID();
    UUID deptId = UUID.randomUUID();
    when(updateUseCase.execute(any(UpdateRoomCommand.class), any()))
        .thenReturn(
            new RoomResponse(
                id,
                "P101",
                "Phòng 101 Mới",
                deptId,
                "Khoa Khám",
                "SUPPORT",
                true,
                1L,
                Instant.now(),
                Instant.now()));

    mvc.perform(
            put("/api/v1/catalog/management/rooms/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Phòng 101 Mới",
                      "roomType": "SUPPORT",
                      "rowVersion": 0
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(200))
        .andExpect(jsonPath("$.data.name").value("Phòng 101 Mới"))
        .andExpect(jsonPath("$.data.rowVersion").value(1));
  }

  @Test
  void deactivateReturns204() throws Exception {
    UUID id = UUID.randomUUID();

    mvc.perform(delete("/api/v1/catalog/management/rooms/{id}", id).param("rowVersion", "0"))
        .andExpect(status().isNoContent());

    var captor = ArgumentCaptor.forClass(DeactivateRoomCommand.class);
    verify(deactivateUseCase).execute(captor.capture(), any());
    assertThat(captor.getValue().id()).isEqualTo(id);
    assertThat(captor.getValue().rowVersion()).isZero();
  }

  @Test
  void reactivateReturns200() throws Exception {
    UUID id = UUID.randomUUID();
    UUID deptId = UUID.randomUUID();
    when(reactivateUseCase.execute(any(ReactivateRoomCommand.class), any()))
        .thenReturn(
            new RoomResponse(
                id,
                "P101",
                "Phòng 101",
                deptId,
                "Khoa Khám",
                "EXAMINATION",
                true,
                1L,
                Instant.now(),
                Instant.now()));

    mvc.perform(
            post("/api/v1/catalog/management/rooms/{id}/reactivate", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "rowVersion": 0 }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.active").value(true));
  }
}

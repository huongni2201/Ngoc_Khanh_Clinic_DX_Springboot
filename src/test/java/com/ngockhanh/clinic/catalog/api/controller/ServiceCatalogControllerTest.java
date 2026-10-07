package com.ngockhanh.clinic.catalog.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ngockhanh.clinic.catalog.application.query.ServiceCatalogListQuery;
import com.ngockhanh.clinic.catalog.application.response.ServiceCatalogItemResponse;
import com.ngockhanh.clinic.catalog.application.usecase.ListServiceCatalogUseCase;
import com.ngockhanh.clinic.shared.web.ApiResponseWriter;
import com.ngockhanh.clinic.shared.web.GlobalExceptionHandler;
import com.ngockhanh.clinic.shared.web.PageResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

class ServiceCatalogControllerTest {
  private final ListServiceCatalogUseCase list = mock(ListServiceCatalogUseCase.class);
  private MockMvc mvc;

  @BeforeEach
  void standaloneMvc() {
    mvc =
        MockMvcBuilders.standaloneSetup(new ServiceCatalogController(list))
            .setControllerAdvice(
                new GlobalExceptionHandler(new ApiResponseWriter(JsonMapper.builder().build())))
            .build();
  }

  @Test
  void listReturnsThePageInTheEnvelopeAndMapsQueryParameters() throws Exception {
    UUID id = UUID.randomUUID();
    when(list.execute(any()))
        .thenReturn(
            PageResponse.<ServiceCatalogItemResponse>builder()
                .items(
                    List.of(
                        ServiceCatalogItemResponse.builder()
                            .id(id)
                            .code("S1")
                            .name("Khám tổng quát")
                            .serviceType("CONSULTATION")
                            .unitPrice(new BigDecimal("150000.00"))
                            .active(true)
                            .build()))
                .page(2)
                .size(5)
                .totalElements(6)
                .totalPages(2)
                .build());

    mvc.perform(
            get("/api/v1/catalog/services")
                .param("page", "2")
                .param("size", "5")
                .param("searchKey", "kham")
                .param("sortKey", "name")
                .param("sortBy", "DESC"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(200))
        .andExpect(jsonPath("$.data.items[0].id").value(id.toString()))
        .andExpect(jsonPath("$.data.items[0].code").value("S1"))
        .andExpect(jsonPath("$.data.items[0].serviceType").value("CONSULTATION"))
        .andExpect(jsonPath("$.data.items[0].unitPrice").value(150000.00))
        .andExpect(jsonPath("$.data.items[0].active").value(true))
        .andExpect(jsonPath("$.data.totalElements").value(6));

    var query = ArgumentCaptor.forClass(ServiceCatalogListQuery.class);
    verify(list).execute(query.capture());
    assertThat(query.getValue())
        .isEqualTo(
            ServiceCatalogListQuery.builder()
                .page(2)
                .size(5)
                .searchKey("kham")
                .sortKey("name")
                .sortBy("DESC")
                .build());
  }

  @Test
  void listUsesDefaultsWhenNoParameterIsGiven() throws Exception {
    when(list.execute(any()))
        .thenReturn(
            PageResponse.<ServiceCatalogItemResponse>builder()
                .items(List.of())
                .page(1)
                .size(10)
                .totalElements(0)
                .totalPages(0)
                .build());

    mvc.perform(get("/api/v1/catalog/services")).andExpect(status().isOk());

    var query = ArgumentCaptor.forClass(ServiceCatalogListQuery.class);
    verify(list).execute(query.capture());
    assertThat(query.getValue().page()).isEqualTo(1);
    assertThat(query.getValue().size()).isEqualTo(10);
    assertThat(query.getValue().sortKey()).isEqualTo("id");
    assertThat(query.getValue().sortBy()).isEqualTo("ASC");
  }

  @Test
  void invalidParametersAreRejectedWith400BeforeTheUseCase() throws Exception {
    mvc.perform(get("/api/v1/catalog/services").param("size", "101"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/catalog/services").param("page", "0"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/catalog/services").param("sortKey", "serviceType"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/catalog/services").param("sortBy", "sideways"))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(list);
  }
}

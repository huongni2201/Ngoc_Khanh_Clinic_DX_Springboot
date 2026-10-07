package com.ngockhanh.clinic.catalog.api.controller;

import com.ngockhanh.clinic.catalog.api.request.ServiceCatalogListRequest;
import com.ngockhanh.clinic.catalog.application.query.ServiceCatalogListQuery;
import com.ngockhanh.clinic.catalog.application.response.ServiceCatalogItemResponse;
import com.ngockhanh.clinic.catalog.application.usecase.ListServiceCatalogUseCase;
import com.ngockhanh.clinic.shared.web.ApiResponse;
import com.ngockhanh.clinic.shared.web.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/catalog/services")
public class ServiceCatalogController {
  private final ListServiceCatalogUseCase listUseCase;

  /**
   * Lists the active services a health examination batch can select.
   *
   * @param request search and pagination parameters
   * @return the requested page of catalog services
   */
  @GetMapping
  public ResponseEntity<ApiResponse<PageResponse<ServiceCatalogItemResponse>>> list(
      @Valid @ModelAttribute ServiceCatalogListRequest request) {
    log.debug(
        "List catalog services request: page={}, size={}", request.getPage(), request.getSize());
    ServiceCatalogListQuery query =
        ServiceCatalogListQuery.builder()
            .page(request.getPage())
            .size(request.getSize())
            .searchKey(request.getSearchKey())
            .sortKey(request.getSortKey())
            .sortBy(request.getSortBy())
            .build();
    return ResponseEntity.ok(
        ApiResponse.success(HttpStatus.OK.value(), listUseCase.execute(query)));
  }
}

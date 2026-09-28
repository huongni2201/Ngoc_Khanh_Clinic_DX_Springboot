package com.ngockhanh.clinic.healthcheck.api.controller;

import com.ngockhanh.clinic.healthcheck.api.request.EmployeeRequest;
import com.ngockhanh.clinic.healthcheck.api.response.EmployeeListResponse;
import com.ngockhanh.clinic.healthcheck.application.query.EmployeeListQuery;
import com.ngockhanh.clinic.healthcheck.application.usecase.ListBatchEmployeeUseCase;
import com.ngockhanh.clinic.shared.web.ApiResponse;
import com.ngockhanh.clinic.shared.web.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/health-examination-batches/{batchId}")
@RequiredArgsConstructor
@Slf4j
public class OrganizationController {

  private final ListBatchEmployeeUseCase listBatchEmployeeUseCase;

  /**
   * Lists a batch roster. Defaults are page 1, size 10, sortType id, and sortBy ASC.
   * searchKey is a case-insensitive keyword across employee name, CCCD, and phone number.
   * sortType selects an allowlisted field; sortBy selects its direction.
   */
  @GetMapping("/employees")
  public ResponseEntity<ApiResponse<PageResponse<EmployeeListResponse>>> employees(
      @PathVariable UUID organizationId,
      @PathVariable UUID batchId,
      @Valid @ModelAttribute EmployeeRequest request
  ) {
    log.info("Listing employees for organizationId={}, batchId={}", organizationId, batchId);

    EmployeeListQuery query = EmployeeListQuery.builder()
        .page(request.getPage())
        .size(request.getSize())
        .searchKey(request.getSearchKey())
        .sortBy(request.getSortBy())
        .sortType(request.getSortType())
        .build();

    PageResponse<EmployeeListResponse> response = listBatchEmployeeUseCase.execute(
        organizationId, batchId, query);

    return ResponseEntity.ok(new ApiResponse<>(200, "Health examination batch employees", response));
  }
}

package com.ngockhanh.clinic.healthexamination.api.controller;

import com.ngockhanh.clinic.healthexamination.api.request.OrganizationBatchParticipantRequest;
import com.ngockhanh.clinic.healthexamination.application.query.OrganizationBatchParticipantQuery;
import com.ngockhanh.clinic.healthexamination.application.response.OrganizationBatchParticipantResponse;
import com.ngockhanh.clinic.healthexamination.application.usecase.ListBatchParticipantUseCase;
import com.ngockhanh.clinic.shared.web.ApiResponse;
import com.ngockhanh.clinic.shared.web.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/health-examination-batches/{batchId}")
@RequiredArgsConstructor
public class OrganizationBatchParticipantController {

  private final ListBatchParticipantUseCase listBatchParticipantUseCase;

  /**
   * Lists a batch roster. Defaults are page 1, size 10, sortKey id, and sortBy ASC. searchKey is a
   * case-insensitive keyword across participant name, CCCD, and phone number. sortKey selects an
   * allowlisted field; sortBy selects its direction.
   *
   * @param organizationId organization identifier
   * @param batchId health-examination batch identifier
   * @param request pagination, search and sorting parameters
   * @return the requested batch participant page
   */
  @GetMapping("/participants")
  public ResponseEntity<ApiResponse<PageResponse<OrganizationBatchParticipantResponse>>>
      participants(
          @PathVariable UUID organizationId,
          @PathVariable UUID batchId,
          @Valid @ModelAttribute OrganizationBatchParticipantRequest request) {
    log.debug(
        "List batch participants request: organizationId={}, batchId={}", organizationId, batchId);
    OrganizationBatchParticipantQuery query =
        OrganizationBatchParticipantQuery.builder()
            .page(request.getPage())
            .size(request.getSize())
            .searchKey(request.getSearchKey())
            .sortBy(request.getSortBy())
            .sortKey(request.getSortKey())
            .build();

    PageResponse<OrganizationBatchParticipantResponse> response =
        listBatchParticipantUseCase.execute(organizationId, batchId, query);

    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(), "Health examination batch participants", response));
  }
}

package com.ngockhanh.clinic.healthexamination.api.controller;

import com.ngockhanh.clinic.healthexamination.api.request.CreateHealthExaminationBatchRequest;
import com.ngockhanh.clinic.healthexamination.api.request.DeleteHealthExaminationBatchRequest;
import com.ngockhanh.clinic.healthexamination.api.request.HealthExaminationBatchListRequest;
import com.ngockhanh.clinic.healthexamination.api.request.UpdateHealthExaminationBatchRequest;
import com.ngockhanh.clinic.healthexamination.application.command.CreateHealthExaminationBatchCommand;
import com.ngockhanh.clinic.healthexamination.application.command.DeleteHealthExaminationBatchCommand;
import com.ngockhanh.clinic.healthexamination.application.command.UpdateHealthExaminationBatchCommand;
import com.ngockhanh.clinic.healthexamination.application.query.HealthExaminationBatchListQuery;
import com.ngockhanh.clinic.healthexamination.application.response.BatchDetailResponse;
import com.ngockhanh.clinic.healthexamination.application.response.BatchSummaryResponse;
import com.ngockhanh.clinic.healthexamination.application.usecase.CreateHealthExaminationBatchUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.DeleteHealthExaminationBatchUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.GetHealthExaminationBatchByIdUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.ListHealthExaminationBatchUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.UpdateHealthExaminationBatchUseCase;
import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.shared.web.ApiResponse;
import com.ngockhanh.clinic.shared.web.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/organizations/{organizationId}/health-examination-batches")
public class OrganizationBatchController {
  private final CreateHealthExaminationBatchUseCase createBatchUseCase;
  private final GetHealthExaminationBatchByIdUseCase getBatchUseCase;
  private final UpdateHealthExaminationBatchUseCase updateBatchUseCase;
  private final ListHealthExaminationBatchUseCase listBatchUseCase;
  private final DeleteHealthExaminationBatchUseCase deleteBatchUseCase;

  /**
   * Creates a draft health examination batch for an organization.
   *
   * @param organizationId owning organization
   * @param request batch configuration submitted by the client
   * @param principal authenticated staff principal
   * @return the created batch, with a {@code Location} header pointing to it
   */
  @PostMapping
  public ResponseEntity<ApiResponse<BatchDetailResponse>> create(
      @PathVariable UUID organizationId,
      @Valid @RequestBody CreateHealthExaminationBatchRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.debug("Create health examination batch request: organizationId={}", organizationId);
    BatchDetailResponse response =
        createBatchUseCase.execute(
            organizationId,
            CreateHealthExaminationBatchCommand.builder()
                .configuration(request.toConfiguration())
                .build(),
            principal.userId());
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{batchId}")
            .buildAndExpand(response.id())
            .toUri();
    return ResponseEntity.created(location)
        .body(
            ApiResponse.success(
                HttpStatus.CREATED.value(), "Health examination batch created", response));
  }

  /**
   * Retrieves one health examination batch of an organization.
   *
   * @param organizationId owning organization
   * @param batchId batch identifier
   * @return the batch with its days and services
   */
  @GetMapping("/{batchId}")
  public ResponseEntity<ApiResponse<BatchDetailResponse>> get(
      @PathVariable UUID organizationId, @PathVariable UUID batchId) {
    log.debug(
        "Get health examination batch request: organizationId={}, batchId={}",
        organizationId,
        batchId);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(), getBatchUseCase.execute(organizationId, batchId)));
  }

  /**
   * Lists the health examination batches of an organization.
   *
   * @param organizationId owning organization
   * @param request search and pagination parameters
   * @return the requested page of batch summaries
   */
  @GetMapping
  public ResponseEntity<ApiResponse<PageResponse<BatchSummaryResponse>>> list(
      @PathVariable UUID organizationId,
      @Valid @ModelAttribute HealthExaminationBatchListRequest request) {
    log.debug(
        "List health examination batches request: organizationId={}, page={}, size={}",
        organizationId,
        request.getPage(),
        request.getSize());
    HealthExaminationBatchListQuery query =
        HealthExaminationBatchListQuery.builder()
            .page(request.getPage())
            .size(request.getSize())
            .searchKey(request.getSearchKey())
            .sortKey(request.getSortKey())
            .sortBy(request.getSortBy())
            .build();
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(), listBatchUseCase.execute(organizationId, query)));
  }

  /**
   * Replaces the whole configuration of a draft batch.
   *
   * @param organizationId owning organization
   * @param batchId batch identifier
   * @param request full configuration and the expected row version
   * @param principal authenticated staff principal
   * @return the updated batch
   */
  @PutMapping("/{batchId}")
  public ResponseEntity<ApiResponse<BatchDetailResponse>> update(
      @PathVariable UUID organizationId,
      @PathVariable UUID batchId,
      @Valid @RequestBody UpdateHealthExaminationBatchRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.debug(
        "Update health examination batch request: organizationId={}, batchId={}",
        organizationId,
        batchId);
    BatchDetailResponse response =
        updateBatchUseCase.execute(
            organizationId,
            batchId,
            UpdateHealthExaminationBatchCommand.builder()
                .configuration(request.toConfiguration())
                .rowVersion(request.rowVersion())
                .build(),
            principal.userId());
    return ResponseEntity.ok(
        ApiResponse.success(HttpStatus.OK.value(), "Health examination batch updated", response));
  }

  /**
   * Soft-deletes a draft batch that has no Participant and no integration history.
   *
   * @param organizationId owning organization
   * @param batchId batch identifier
   * @param request carries the expected row version
   * @param principal authenticated staff principal
   * @return an empty 204 response
   */
  @DeleteMapping("/{batchId}")
  public ResponseEntity<Void> delete(
      @PathVariable UUID organizationId,
      @PathVariable UUID batchId,
      @Valid @ModelAttribute DeleteHealthExaminationBatchRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.debug(
        "Delete health examination batch request: organizationId={}, batchId={}",
        organizationId,
        batchId);
    deleteBatchUseCase.execute(
        organizationId,
        batchId,
        DeleteHealthExaminationBatchCommand.builder().rowVersion(request.rowVersion()).build(),
        principal.userId());
    return ResponseEntity.noContent().build();
  }
}

package com.ngockhanh.clinic.healthexamination.api.controller;

import com.ngockhanh.clinic.healthexamination.api.request.*;
import com.ngockhanh.clinic.healthexamination.application.command.CreateHealthExaminationBatchCommand;
import com.ngockhanh.clinic.healthexamination.application.query.HealthExaminationBatchListQuery;
import com.ngockhanh.clinic.healthexamination.application.response.*;
import com.ngockhanh.clinic.healthexamination.application.usecase.*;
import com.ngockhanh.clinic.shared.web.*;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.*;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/organizations/{organizationId}/health-examination-batches")
public class OrganizationBatchController {
  private final CreateHealthExaminationBatchUseCase create;
  private final GetHealthExaminationBatchUseCase get;
  private final ListHealthExaminationBatchUseCase list;
  private final UpdateHealthExaminationBatchUseCase update;
  private final DeleteHealthExaminationBatchUseCase delete;
  private final Environment environment;

  /**
   * Creates a draft campaign with its service scope and entered prices.
   *
   * @param organizationId parent organization identifier
   * @param request campaign configuration
   * @param authentication authenticated actor, or the configured local/test mock actor
   * @return the persisted campaign
   */
  @PostMapping
  public ResponseEntity<ApiResponse<BatchDetailResponse>> create(
      @PathVariable UUID organizationId,
      @Valid @RequestBody HealthExaminationBatchRequest request,
      Authentication authentication) {
    log.debug("Create batch request: organizationId={}", organizationId);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201,
                "Batch created",
                create.execute(
                    organizationId,
                    new CreateHealthExaminationBatchCommand(
                        actor(authentication), request.toCommand()))));
  }

  /**
   * Lists visible campaigns using keyword search, allowlisted sorting, and one-based pagination.
   *
   * @param organizationId parent organization identifier
   * @param request search and pagination parameters
   * @return the requested campaign page
   */
  @GetMapping
  public ResponseEntity<ApiResponse<PageResponse<BatchSummaryResponse>>> list(
      @PathVariable UUID organizationId,
      @Valid @ModelAttribute HealthExaminationBatchListRequest request) {
    log.debug(
        "List batch request: organizationId={}, page={}, size={}",
        organizationId,
        request.getPage(),
        request.getSize());
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            list.execute(
                organizationId,
                new HealthExaminationBatchListQuery(
                    request.getPage(),
                    request.getSize(),
                    request.getSearchKey(),
                    request.getSortKey(),
                    request.getSortBy()))));
  }

  /**
   * Retrieves a visible campaign and its frozen service metadata.
   *
   * @param organizationId parent organization identifier
   * @param batchId campaign identifier
   * @return campaign details
   */
  @GetMapping("/{batchId}")
  public ResponseEntity<ApiResponse<BatchDetailResponse>> get(
      @PathVariable UUID organizationId, @PathVariable UUID batchId) {
    log.debug("Get batch request: organizationId={}, batchId={}", organizationId, batchId);
    return ResponseEntity.ok(ApiResponse.success(200, get.execute(organizationId, batchId)));
  }

  /**
   * Replaces editable draft configuration while preserving identities of retained services.
   *
   * @param organizationId parent organization identifier
   * @param batchId campaign identifier
   * @param request desired draft configuration
   * @param authentication authenticated actor, or the configured local/test mock actor
   * @return updated campaign details
   */
  @PutMapping("/{batchId}")
  public ResponseEntity<ApiResponse<BatchDetailResponse>> update(
      @PathVariable UUID organizationId,
      @PathVariable UUID batchId,
      @Valid @RequestBody HealthExaminationBatchRequest request,
      Authentication authentication) {
    log.debug("Update batch request: organizationId={}, batchId={}", organizationId, batchId);
    return ResponseEntity.ok(
        ApiResponse.success(
            200,
            "Batch updated",
            update.execute(organizationId, batchId, request.toCommand(), actor(authentication))));
  }

  /**
   * Soft deletes a draft without dependent records. Repeated deletion is idempotent.
   *
   * @param organizationId parent organization identifier
   * @param batchId campaign identifier
   * @param authentication authenticated actor, or the configured local/test mock actor
   * @return an empty successful response
   */
  @DeleteMapping("/{batchId}")
  public ResponseEntity<ApiResponse<Void>> delete(
      @PathVariable UUID organizationId,
      @PathVariable UUID batchId,
      Authentication authentication) {
    log.debug("Delete batch request: organizationId={}, batchId={}", organizationId, batchId);
    delete.execute(organizationId, batchId, actor(authentication));
    return ResponseEntity.ok(new ApiResponse<>(200, "Batch deleted"));
  }

  private UUID actor(Authentication authentication) {
    if (authentication != null && authentication.isAuthenticated()) {
      try {
        return UUID.fromString(authentication.getName());
      } catch (IllegalArgumentException ignored) {
      }
    }
    if (environment.acceptsProfiles(Profiles.of("local", "test"))) {
      String mock = environment.getProperty("clinic.health-examination.batch.mock-created-by");
      if (mock != null) return UUID.fromString(mock);
    }
    throw new AccessDeniedException("An authenticated actor is required");
  }
}

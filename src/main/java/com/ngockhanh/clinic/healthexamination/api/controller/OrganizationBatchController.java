package com.ngockhanh.clinic.healthexamination.api.controller;

import com.ngockhanh.clinic.healthexamination.api.request.*;
import com.ngockhanh.clinic.healthexamination.application.command.CreateHealthExaminationBatchCommand;
import com.ngockhanh.clinic.healthexamination.application.query.HealthExaminationBatchListQuery;
import com.ngockhanh.clinic.healthexamination.application.response.*;
import com.ngockhanh.clinic.healthexamination.application.usecase.*;
import com.ngockhanh.clinic.identity.application.query.UserPrincipal;
import com.ngockhanh.clinic.shared.web.*;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.*;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/organizations/{organizationId}/health-examination-batches")
public class OrganizationBatchController {
  private final CreateHealthExaminationBatchUseCase create;
  private final ListHealthExaminationBatchUseCase list;
  private final Environment environment;

  /**
   * Creates a draft campaign with at least one examination day and contracted service prices.
   *
   * @param organizationId parent organization identifier
   * @param request campaign configuration
   * @param principal authenticated actor, or the configured local/test mock actor
   * @return the persisted campaign
   */
  @PostMapping
  public ResponseEntity<ApiResponse<BatchDetailResponse>> create(
      @PathVariable UUID organizationId,
      @Valid @RequestBody HealthExaminationBatchRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {

    log.debug("Create batch request: organizationId={}", organizationId);

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                201,
                "Batch created",
                create.execute(
                    organizationId,
                    CreateHealthExaminationBatchCommand.builder()
                        .createdBy(actor(principal))
                        .configuration(request.toCommand())
                        .build())));
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
                HealthExaminationBatchListQuery.builder()
                    .page(request.getPage())
                    .size(request.getSize())
                    .searchKey(request.getSearchKey())
                    .sortKey(request.getSortKey())
                    .sortBy(request.getSortBy())
                    .build())));
  }

  private UUID actor(UserPrincipal principal) {
    if (principal != null && principal.userId() != null) return principal.userId();
    if (environment.acceptsProfiles(Profiles.of("local", "test"))) {
      String mock = environment.getProperty("clinic.health-examination.batch.mock-created-by");
      if (mock != null) return UUID.fromString(mock);
    }
    throw new AccessDeniedException("An authenticated actor is required");
  }
}

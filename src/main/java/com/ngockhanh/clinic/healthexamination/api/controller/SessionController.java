package com.ngockhanh.clinic.healthexamination.api.controller;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.healthexamination.api.request.CancelSessionRequest;
import com.ngockhanh.clinic.healthexamination.api.request.CreateSessionRequest;
import com.ngockhanh.clinic.healthexamination.api.request.SessionListRequest;
import com.ngockhanh.clinic.healthexamination.api.request.UpdateSessionRequest;
import com.ngockhanh.clinic.healthexamination.application.response.SessionResponse;
import com.ngockhanh.clinic.healthexamination.application.usecase.CancelSessionUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.CreateSessionUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.GetSessionUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.ListSessionsUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.UpdateSessionUseCase;
import com.ngockhanh.clinic.shared.web.ApiResponse;
import com.ngockhanh.clinic.shared.web.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Examination sessions of an organization: a date, a site and an activity. */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/organizations/{organizationId}/sessions")
public class SessionController {
  private final CreateSessionUseCase createUseCase;
  private final UpdateSessionUseCase updateUseCase;
  private final CancelSessionUseCase cancelUseCase;
  private final GetSessionUseCase getUseCase;
  private final ListSessionsUseCase listUseCase;

  /**
   * Creates an open session.
   *
   * @param organizationId owning organization
   * @param request date, activity, site and optional agreement
   * @return the created session with HTTP 201
   */
  @PostMapping
  public ResponseEntity<ApiResponse<SessionResponse>> create(
      @PathVariable UUID organizationId,
      @Valid @RequestBody CreateSessionRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.info("Create session request: organizationId={}", organizationId);
    SessionResponse response =
        createUseCase.execute(organizationId, request.toCommand(), principal);
    return ResponseEntity.status(HttpStatus.CREATED)
        .cacheControl(CacheControl.noStore())
        .body(ApiResponse.success(HttpStatus.CREATED.value(), "Session created", response));
  }

  /**
   * Lists the sessions of an organization with the number of Participants in each.
   *
   * @param request filters, sorting and pagination
   * @return the requested page
   */
  @GetMapping
  public ResponseEntity<ApiResponse<PageResponse<SessionResponse>>> list(
      @PathVariable UUID organizationId,
      @Valid @ModelAttribute SessionListRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.info(
        "List sessions request: organizationId={}, page={}, size={}",
        organizationId,
        request.getPage(),
        request.getSize());
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            ApiResponse.success(
                HttpStatus.OK.value(),
                listUseCase.execute(organizationId, request.toQuery(), principal)));
  }

  /**
   * Retrieves one session.
   *
   * @return the session with its Participant counts
   */
  @GetMapping("/{sessionId}")
  public ResponseEntity<ApiResponse<SessionResponse>> get(
      @PathVariable UUID organizationId,
      @PathVariable UUID sessionId,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.info("Get session request: organizationId={}, sessionId={}", organizationId, sessionId);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            ApiResponse.success(
                HttpStatus.OK.value(), getUseCase.execute(organizationId, sessionId, principal)));
  }

  /**
   * Changes the date, activity and site of an open session.
   *
   * @param request replacement values and the expected row version
   * @return the updated session
   */
  @PutMapping("/{sessionId}")
  public ResponseEntity<ApiResponse<SessionResponse>> update(
      @PathVariable UUID organizationId,
      @PathVariable UUID sessionId,
      @Valid @RequestBody UpdateSessionRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.info("Update session request: organizationId={}, sessionId={}", organizationId, sessionId);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            ApiResponse.success(
                HttpStatus.OK.value(),
                "Session updated",
                updateUseCase.execute(organizationId, sessionId, request.toCommand(), principal)));
  }

  /**
   * Cancels a session nobody is assigned to.
   *
   * @param request carries the expected row version
   * @return the cancelled session
   */
  @PostMapping("/{sessionId}/cancel")
  public ResponseEntity<ApiResponse<SessionResponse>> cancel(
      @PathVariable UUID organizationId,
      @PathVariable UUID sessionId,
      @Valid @RequestBody CancelSessionRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.info("Cancel session request: organizationId={}, sessionId={}", organizationId, sessionId);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            ApiResponse.success(
                HttpStatus.OK.value(),
                "Session cancelled",
                cancelUseCase.execute(organizationId, sessionId, request.toCommand(), principal)));
  }
}

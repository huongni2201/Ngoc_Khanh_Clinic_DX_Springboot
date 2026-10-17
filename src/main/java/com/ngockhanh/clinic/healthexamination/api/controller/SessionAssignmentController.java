package com.ngockhanh.clinic.healthexamination.api.controller;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.healthexamination.api.request.AssignParticipantsRequest;
import com.ngockhanh.clinic.healthexamination.api.request.AssignmentListRequest;
import com.ngockhanh.clinic.healthexamination.api.request.CancelAssignmentRequest;
import com.ngockhanh.clinic.healthexamination.api.request.MoveAssignmentRequest;
import com.ngockhanh.clinic.healthexamination.api.request.MoveSessionAssignmentsRequest;
import com.ngockhanh.clinic.healthexamination.api.request.RecordAttendanceRequest;
import com.ngockhanh.clinic.healthexamination.application.response.AssignParticipantsResponse;
import com.ngockhanh.clinic.healthexamination.application.response.AssignmentResponse;
import com.ngockhanh.clinic.healthexamination.application.response.AssignmentRowResponse;
import com.ngockhanh.clinic.healthexamination.application.response.MoveSessionAssignmentsResponse;
import com.ngockhanh.clinic.healthexamination.application.usecase.AssignParticipantsToSessionUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.CancelAssignmentUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.ListSessionAssignmentsUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.MoveAssignmentUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.MoveSessionAssignmentsUseCase;
import com.ngockhanh.clinic.healthexamination.application.usecase.RecordAssignmentAttendanceUseCase;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Participants of a session: scheduling them, moving them, cancelling them and recording who
 * attended.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/organizations/{organizationId}/sessions/{sessionId}/assignments")
public class SessionAssignmentController {
  private final ListSessionAssignmentsUseCase listUseCase;
  private final AssignParticipantsToSessionUseCase assignUseCase;
  private final MoveAssignmentUseCase moveUseCase;
  private final MoveSessionAssignmentsUseCase moveAllUseCase;
  private final CancelAssignmentUseCase cancelUseCase;
  private final RecordAssignmentAttendanceUseCase attendanceUseCase;

  /**
   * Lists the Participants of a session; the identification number is masked.
   *
   * @return the requested page
   */
  @GetMapping
  public ResponseEntity<ApiResponse<PageResponse<AssignmentRowResponse>>> list(
      @PathVariable UUID organizationId,
      @PathVariable UUID sessionId,
      @Valid @ModelAttribute AssignmentListRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.info(
        "List session assignments request: organizationId={}, sessionId={}, page={}, size={}",
        organizationId,
        sessionId,
        request.getPage(),
        request.getSize());
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            ApiResponse.success(
                HttpStatus.OK.value(),
                listUseCase.execute(organizationId, sessionId, request.toQuery(), principal)));
  }

  /**
   * Schedules Participants into the session. Participants already in it are skipped and reported.
   *
   * @return 201 with the new assignments and the skipped Participants
   */
  @PostMapping
  public ResponseEntity<ApiResponse<AssignParticipantsResponse>> assign(
      @PathVariable UUID organizationId,
      @PathVariable UUID sessionId,
      @Valid @RequestBody AssignParticipantsRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.info(
        "Assign participants request: organizationId={}, sessionId={}, count={}",
        organizationId,
        sessionId,
        request.participantIds().size());
    return ResponseEntity.status(HttpStatus.CREATED)
        .cacheControl(CacheControl.noStore())
        .body(
            ApiResponse.success(
                HttpStatus.CREATED.value(),
                assignUseCase.execute(organizationId, sessionId, request.toCommand(), principal)));
  }

  /**
   * Moves every expected Participant of the session to another session.
   *
   * @return how many Participants moved and who was skipped
   */
  @PostMapping("/move")
  public ResponseEntity<ApiResponse<MoveSessionAssignmentsResponse>> moveAll(
      @PathVariable UUID organizationId,
      @PathVariable UUID sessionId,
      @Valid @RequestBody MoveSessionAssignmentsRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.info(
        "Move session assignments request: organizationId={}, sessionId={}",
        organizationId,
        sessionId);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            ApiResponse.success(
                HttpStatus.OK.value(),
                moveAllUseCase.execute(organizationId, sessionId, request.toCommand(), principal)));
  }

  /**
   * Moves one Participant to another session.
   *
   * @return the new assignment in the target session
   */
  @PostMapping("/{assignmentId}/move")
  public ResponseEntity<ApiResponse<AssignmentResponse>> move(
      @PathVariable UUID organizationId,
      @PathVariable UUID sessionId,
      @PathVariable UUID assignmentId,
      @Valid @RequestBody MoveAssignmentRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.info(
        "Move assignment request: organizationId={}, sessionId={}, assignmentId={}",
        organizationId,
        sessionId,
        assignmentId);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            ApiResponse.success(
                HttpStatus.OK.value(),
                moveUseCase.execute(
                    organizationId, sessionId, assignmentId, request.toCommand(), principal)));
  }

  /**
   * Cancels the assignment of a Participant who is still expected.
   *
   * @return the cancelled assignment
   */
  @PostMapping("/{assignmentId}/cancel")
  public ResponseEntity<ApiResponse<AssignmentResponse>> cancel(
      @PathVariable UUID organizationId,
      @PathVariable UUID sessionId,
      @PathVariable UUID assignmentId,
      @Valid @RequestBody CancelAssignmentRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.info(
        "Cancel assignment request: organizationId={}, sessionId={}, assignmentId={}",
        organizationId,
        sessionId,
        assignmentId);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            ApiResponse.success(
                HttpStatus.OK.value(),
                cancelUseCase.execute(
                    organizationId, sessionId, assignmentId, request.toCommand(), principal)));
  }

  /**
   * Records that the Participant attended, did not show, or is expected again.
   *
   * @return the assignment as stored afterwards
   */
  @PostMapping("/{assignmentId}/attendance")
  public ResponseEntity<ApiResponse<AssignmentResponse>> recordAttendance(
      @PathVariable UUID organizationId,
      @PathVariable UUID sessionId,
      @PathVariable UUID assignmentId,
      @Valid @RequestBody RecordAttendanceRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.info(
        "Record attendance request: organizationId={}, sessionId={}, assignmentId={}",
        organizationId,
        sessionId,
        assignmentId);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            ApiResponse.success(
                HttpStatus.OK.value(),
                attendanceUseCase.execute(
                    organizationId, sessionId, assignmentId, request.toCommand(), principal)));
  }
}

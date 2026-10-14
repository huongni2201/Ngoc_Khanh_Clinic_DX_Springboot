package com.ngockhanh.clinic.accesscontrol.api.controller;

import com.ngockhanh.clinic.accesscontrol.api.request.CreateStaffMemberRequest;
import com.ngockhanh.clinic.accesscontrol.api.request.ListStaffMemberRequest;
import com.ngockhanh.clinic.accesscontrol.application.query.ListStaffMembersQuery;
import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.accesscontrol.application.response.StaffMemberResponse;
import com.ngockhanh.clinic.accesscontrol.application.usecase.CreateStaffMemberUseCase;
import com.ngockhanh.clinic.accesscontrol.application.usecase.ListStaffMembersUseCase;
import com.ngockhanh.clinic.shared.web.ApiResponse;
import com.ngockhanh.clinic.shared.web.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Staff members and their accounts (docs/api/staff-members.md). */
@Slf4j
@RestController
@RequestMapping("/api/v1/staff-members")
@RequiredArgsConstructor
public class StaffMemberController {
  private final ListStaffMembersUseCase listStaffMembersUseCase;
  private final CreateStaffMemberUseCase createStaffMemberUseCase;

  /**
   * Lists staff members with their accounts.
   *
   * @param request search and pagination parameters
   * @param principal authenticated staff principal
   * @return the requested page
   */
  @GetMapping
  public ResponseEntity<ApiResponse<PageResponse<StaffMemberResponse>>> list(
      @Valid @ModelAttribute ListStaffMemberRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    ListStaffMembersQuery query =
        ListStaffMembersQuery.builder()
            .page(request.getPage())
            .size(request.getSize())
            .searchKey(request.getSearchKey())
            .sortKey(request.getSortKey())
            .sortBy(request.getSortBy())
            .build();
    return ResponseEntity.ok(
        ApiResponse.success(HttpStatus.OK.value(), listStaffMembersUseCase.execute(query, principal)));
  }

  /**
   * Creates a staff member and its STAFF account with generated staff code and username.
   *
   * @param request staff member details, temporary password and roles
   * @param principal authenticated staff principal
   * @return the created staff member with its account
   */
  @PostMapping
  public ResponseEntity<ApiResponse<StaffMemberResponse>> create(
      @Valid @RequestBody CreateStaffMemberRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.info("Create staff member request received: roles={}", request.roleCodes().size());
    StaffMemberResponse response = createStaffMemberUseCase.execute(request.toCommand(), principal);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(HttpStatus.CREATED.value(), "Staff member created", response));
  }
}

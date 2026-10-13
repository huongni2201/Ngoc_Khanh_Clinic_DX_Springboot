package com.ngockhanh.clinic.accesscontrol.api.controller;

import com.ngockhanh.clinic.accesscontrol.api.request.AccountOwnerCandidateSearchRequest;
import com.ngockhanh.clinic.accesscontrol.api.request.CreateUserAccountRequest;
import com.ngockhanh.clinic.accesscontrol.api.request.UsernameAvailabilityRequest;
import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.accesscontrol.application.response.AccountOwnerCandidateListResponse;
import com.ngockhanh.clinic.accesscontrol.application.response.AssignableRoleResponse;
import com.ngockhanh.clinic.accesscontrol.application.response.CreateUserAccountResponse;
import com.ngockhanh.clinic.accesscontrol.application.response.UsernameAvailabilityResponse;
import com.ngockhanh.clinic.accesscontrol.application.usecase.CheckUsernameAvailabilityUseCase;
import com.ngockhanh.clinic.accesscontrol.application.usecase.CreateUserAccountUseCase;
import com.ngockhanh.clinic.accesscontrol.application.usecase.ListAssignableRolesUseCase;
import com.ngockhanh.clinic.accesscontrol.application.usecase.SearchAccountOwnerCandidatesUseCase;
import com.ngockhanh.clinic.shared.web.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** User account creation by an Administrator (UC-005); see docs/api/user-accounts.md. */
@Slf4j
@RestController
@RequestMapping("/api/v1/user-accounts")
@RequiredArgsConstructor
public class UserAccountController {
  private final CreateUserAccountUseCase createUseCase;
  private final SearchAccountOwnerCandidatesUseCase ownerSearchUseCase;
  private final CheckUsernameAvailabilityUseCase usernameAvailabilityUseCase;
  private final ListAssignableRolesUseCase assignableRolesUseCase;

  /**
   * Creates an account for an existing staff member or patient.
   *
   * @param request account type, owner, username, temporary password and roles
   * @param principal authenticated staff principal
   * @return 201 with the created account; the temporary password is never returned
   */
  @PostMapping
  public ResponseEntity<ApiResponse<CreateUserAccountResponse>> create(
      @Valid @RequestBody CreateUserAccountRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.info("Create user account request received: accountType={}", request.accountType());
    CreateUserAccountResponse response = createUseCase.execute(request.toCommand(), principal);
    return ResponseEntity.status(HttpStatus.CREATED)
        .cacheControl(CacheControl.noStore())
        .body(ApiResponse.success(HttpStatus.CREATED.value(), "User account created", response));
  }

  /**
   * Finds active staff members or patients without an account.
   *
   * @param request account type and keyword
   * @param principal authenticated staff principal
   * @return 200 with at most 20 candidates
   */
  @GetMapping("/owner-candidates")
  public ResponseEntity<ApiResponse<AccountOwnerCandidateListResponse>> ownerCandidates(
      @Valid @ModelAttribute AccountOwnerCandidateSearchRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    AccountOwnerCandidateListResponse response =
        ownerSearchUseCase.execute(request.getAccountType(), request.getKeyword(), principal);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(ApiResponse.success(HttpStatus.OK.value(), response));
  }

  /**
   * Tells whether a username is still free.
   *
   * @param request username to check
   * @param principal authenticated staff principal
   * @return 200 with the availability
   */
  @GetMapping("/username-availability")
  public ResponseEntity<ApiResponse<UsernameAvailabilityResponse>> usernameAvailability(
      @Valid @ModelAttribute UsernameAvailabilityRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    UsernameAvailabilityResponse response =
        usernameAvailabilityUseCase.execute(request.getUsername(), principal);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(ApiResponse.success(HttpStatus.OK.value(), response));
  }

  /**
   * Lists the active staff roles that can be granted to a new staff account.
   *
   * @param principal authenticated staff principal
   * @return 200 with the roles ordered by name
   */
  @GetMapping("/assignable-roles")
  public ResponseEntity<ApiResponse<List<AssignableRoleResponse>>> assignableRoles(
      @AuthenticationPrincipal UserPrincipal principal) {
    List<AssignableRoleResponse> response = assignableRolesUseCase.execute(principal);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(ApiResponse.success(HttpStatus.OK.value(), response));
  }
}

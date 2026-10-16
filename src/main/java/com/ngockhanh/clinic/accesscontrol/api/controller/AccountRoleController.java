package com.ngockhanh.clinic.accesscontrol.api.controller;

import com.ngockhanh.clinic.accesscontrol.api.request.ChangeAccountRoleRequest;
import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.accesscontrol.application.response.AccountRoleResponse;
import com.ngockhanh.clinic.accesscontrol.application.usecase.ChangeAccountRoleUseCase;
import com.ngockhanh.clinic.shared.web.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The role a user account holds (SRS UC-010 Change User Role, docs/api/staff-members.md). */
@Slf4j
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountRoleController {
  private final ChangeAccountRoleUseCase changeAccountRoleUseCase;

  /**
   * Replaces the one role of an active staff account.
   *
   * @param accountId account identifier
   * @param request the new role code and the account version the client last read
   * @param principal authenticated staff principal
   * @return the new role and the incremented account version
   */
  @PutMapping("/{accountId}/role")
  public ResponseEntity<ApiResponse<AccountRoleResponse>> changeRole(
      @PathVariable UUID accountId,
      @Valid @RequestBody ChangeAccountRoleRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.info("Change account role request received: accountId={}", accountId);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            "Account role changed",
            changeAccountRoleUseCase.execute(accountId, request.toCommand(), principal)));
  }
}

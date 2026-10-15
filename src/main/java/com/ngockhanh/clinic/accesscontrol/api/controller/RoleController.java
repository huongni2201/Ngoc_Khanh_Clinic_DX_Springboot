package com.ngockhanh.clinic.accesscontrol.api.controller;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.accesscontrol.application.response.RoleResponse;
import com.ngockhanh.clinic.accesscontrol.application.usecase.ListAssignableRolesUseCase;
import com.ngockhanh.clinic.shared.web.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Roles that may be granted to a staff account (docs/api/staff-members.md). */
@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
public class RoleController {
  private final ListAssignableRolesUseCase listAssignableRolesUseCase;

  /**
   * Lists the active roles, other than {@code PATIENT}, ordered by code.
   *
   * @param principal authenticated staff principal
   * @return the roles
   */
  @GetMapping
  public ResponseEntity<ApiResponse<List<RoleResponse>>> list(
      @AuthenticationPrincipal UserPrincipal principal) {
    return ResponseEntity.ok(
        ApiResponse.success(HttpStatus.OK.value(), listAssignableRolesUseCase.execute(principal)));
  }
}

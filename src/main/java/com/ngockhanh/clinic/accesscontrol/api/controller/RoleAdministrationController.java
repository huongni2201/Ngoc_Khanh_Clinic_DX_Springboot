package com.ngockhanh.clinic.accesscontrol.api.controller;

import com.ngockhanh.clinic.accesscontrol.api.request.CreateRoleRequest;
import com.ngockhanh.clinic.accesscontrol.api.request.ListRoleRequest;
import com.ngockhanh.clinic.accesscontrol.api.request.UpdateRolePermissionsRequest;
import com.ngockhanh.clinic.accesscontrol.api.request.UpdateRoleRequest;
import com.ngockhanh.clinic.accesscontrol.application.query.RoleSummary;
import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.accesscontrol.application.response.RolePermissionsResponse;
import com.ngockhanh.clinic.accesscontrol.application.usecase.CreateRoleUseCase;
import com.ngockhanh.clinic.accesscontrol.application.usecase.GetRolePermissionsUseCase;
import com.ngockhanh.clinic.accesscontrol.application.usecase.ListRolesUseCase;
import com.ngockhanh.clinic.accesscontrol.application.usecase.UpdateRolePermissionsUseCase;
import com.ngockhanh.clinic.accesscontrol.application.usecase.UpdateRoleUseCase;
import com.ngockhanh.clinic.shared.web.ApiResponse;
import com.ngockhanh.clinic.shared.web.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

/** Role administration: roles and their permissions (docs/api/roles-and-permissions.md). */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/roles")
@RequiredArgsConstructor
public class RoleAdministrationController {
  private final ListRolesUseCase listRolesUseCase;
  private final CreateRoleUseCase createRoleUseCase;
  private final UpdateRoleUseCase updateRoleUseCase;
  private final GetRolePermissionsUseCase getRolePermissionsUseCase;
  private final UpdateRolePermissionsUseCase updateRolePermissionsUseCase;

  /**
   * Lists roles of every status, or only active or inactive ones, with their usage counts.
   *
   * @param request search, status filter and pagination parameters
   * @param principal authenticated staff principal
   * @return the requested page
   */
  @GetMapping
  public ResponseEntity<ApiResponse<PageResponse<RoleSummary>>> list(
      @Valid @ModelAttribute ListRoleRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(), listRolesUseCase.execute(request.toQuery(), principal)));
  }

  /**
   * Creates a role, optionally copying the permissions of an active role.
   *
   * @param request role details
   * @param principal authenticated staff principal
   * @return the created role
   */
  @PostMapping
  public ResponseEntity<ApiResponse<RoleSummary>> create(
      @Valid @RequestBody CreateRoleRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.info("Create role request received");
    RoleSummary response = createRoleUseCase.execute(request.toCommand(), principal);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(HttpStatus.CREATED.value(), "Role created", response));
  }

  /**
   * Updates the name, description and active flag of a role.
   *
   * @param roleId role identifier
   * @param request new details and the version the client last read
   * @param principal authenticated staff principal
   * @return the updated role
   */
  @PutMapping("/{roleId}")
  public ResponseEntity<ApiResponse<RoleSummary>> update(
      @PathVariable UUID roleId,
      @Valid @RequestBody UpdateRoleRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.info("Update role request received: roleId={}", roleId);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            "Role updated",
            updateRoleUseCase.execute(roleId, request.toCommand(), principal)));
  }

  /**
   * Returns the permissions granted to a role.
   *
   * @param roleId role identifier
   * @param principal authenticated staff principal
   * @return the granted permission codes and the role version
   */
  @GetMapping("/{roleId}/permissions")
  public ResponseEntity<ApiResponse<RolePermissionsResponse>> permissions(
      @PathVariable UUID roleId, @AuthenticationPrincipal UserPrincipal principal) {
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(), getRolePermissionsUseCase.execute(roleId, principal)));
  }

  /**
   * Grants and removes permissions of a role in one transaction.
   *
   * @param roleId role identifier
   * @param request pending changes and the role version the client last read
   * @param principal authenticated staff principal
   * @return the permissions now granted and the new role version
   */
  @PutMapping("/{roleId}/permissions")
  public ResponseEntity<ApiResponse<RolePermissionsResponse>> updatePermissions(
      @PathVariable UUID roleId,
      @Valid @RequestBody UpdateRolePermissionsRequest request,
      @AuthenticationPrincipal UserPrincipal principal) {
    log.info("Update role permissions request received: roleId={}", roleId);
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(),
            "Role permissions updated",
            updateRolePermissionsUseCase.execute(roleId, request.toCommand(), principal)));
  }
}

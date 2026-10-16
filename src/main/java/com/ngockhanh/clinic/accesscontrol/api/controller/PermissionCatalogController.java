package com.ngockhanh.clinic.accesscontrol.api.controller;

import com.ngockhanh.clinic.accesscontrol.application.query.PermissionCatalogItem;
import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.accesscontrol.application.usecase.ListPermissionCatalogUseCase;
import com.ngockhanh.clinic.shared.web.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Predefined permissions that may be granted to roles (docs/api/roles-and-permissions.md). */
@RestController
@RequestMapping("/api/v1/admin/permissions")
@RequiredArgsConstructor
public class PermissionCatalogController {
  private final ListPermissionCatalogUseCase listPermissionCatalogUseCase;

  /**
   * Lists every permission of the catalogue, ordered by module and code.
   *
   * @param principal authenticated staff principal
   * @return the permissions
   */
  @GetMapping
  public ResponseEntity<ApiResponse<List<PermissionCatalogItem>>> list(
      @AuthenticationPrincipal UserPrincipal principal) {
    return ResponseEntity.ok(
        ApiResponse.success(
            HttpStatus.OK.value(), listPermissionCatalogUseCase.execute(principal)));
  }
}

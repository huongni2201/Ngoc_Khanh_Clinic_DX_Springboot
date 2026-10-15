package com.ngockhanh.clinic.accesscontrol.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.port.out.RoleDirectory;
import com.ngockhanh.clinic.accesscontrol.application.query.PermissionCatalogItem;
import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lists the predefined permissions that may be granted to a role (SRS UC-009, 1.3.4). Permissions
 * are seeded by migrations; an Administrator cannot create them.
 */
@Service
@RequiredArgsConstructor
public class ListPermissionCatalogUseCase {
  public static final String VIEW_PERMISSION = "PERMISSION_VIEW";

  private final RoleDirectory directory;

  /**
   * Returns the whole catalogue.
   *
   * @param principal authenticated caller
   * @return every permission, ordered by module and code
   * @throws ApplicationException of type {@code ACCESS_DENIED} without {@code PERMISSION_VIEW}
   */
  @Transactional(readOnly = true)
  public List<PermissionCatalogItem> execute(UserPrincipal principal) {
    requirePermission(principal);
    return directory.findPermissionCatalog();
  }

  /**
   * Requires permission to view the catalogue. An actor ID alone proves nothing: the principal must
   * be a staff account whose roles carry the permission (docs/api/roles-and-permissions.md).
   */
  private static void requirePermission(UserPrincipal principal) {
    Boolean allowed =
        principal != null
            && principal.userId() != null
            && "STAFF".equals(principal.principalType())
            && principal.roleAssignments().stream()
                .anyMatch(role -> role.permissions().contains(VIEW_PERMISSION));
    if (!allowed)
      throw new ApplicationException(
          ApplicationException.Type.ACCESS_DENIED, "You are not authorized to perform this action");
  }
}

package com.ngockhanh.clinic.accesscontrol.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.accesscontrol.application.response.AssignableRoleResponse;
import com.ngockhanh.clinic.accesscontrol.application.service.UserAccountAccessPolicy;
import com.ngockhanh.clinic.accesscontrol.domain.repository.RoleRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lists the active staff roles an Administrator can grant to a new STAFF account (UC-005). The
 * PATIENT role is excluded because it is reserved for patient accounts.
 */
@Service
@RequiredArgsConstructor
public class ListAssignableRolesUseCase {
  private final UserAccountAccessPolicy access;
  private final RoleRepository roles;

  /**
   * Lists the roles.
   *
   * @param principal authenticated Administrator
   * @return active non-patient roles ordered by name then code
   * @throws com.ngockhanh.clinic.shared.exception.ApplicationException of type {@code
   *     ACCESS_DENIED} without the assignable role view permission
   */
  @Transactional(readOnly = true)
  public List<AssignableRoleResponse> execute(UserPrincipal principal) {
    access.requireAssignableRoleView(principal);
    return roles.findActiveStaffRoles().stream()
        .map(
            role ->
                new AssignableRoleResponse(role.id(), role.code(), role.name(), role.description()))
        .toList();
  }
}

package com.ngockhanh.clinic.accesscontrol.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.port.out.StaffMemberDirectory;
import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.accesscontrol.application.response.RoleResponse;
import com.ngockhanh.clinic.accesscontrol.application.service.StaffAdministrationPolicy;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lists the active roles, other than {@code PATIENT}, that may be granted to a staff account. */
@Service
@RequiredArgsConstructor
public class ListAssignableRolesUseCase {
  private final StaffAdministrationPolicy policy;
  private final StaffMemberDirectory directory;

  /**
   * Returns the assignable roles ordered by code.
   *
   * @param principal authenticated caller
   * @return the roles
   * @throws ApplicationException of type {@code ACCESS_DENIED} without {@code ROLE_VIEW}
   */
  @Transactional(readOnly = true)
  public List<RoleResponse> execute(UserPrincipal principal) {
    policy.requireRoleView(principal);
    return directory.findAssignableRoles().stream().map(RoleResponse::from).toList();
  }
}

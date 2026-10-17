package com.ngockhanh.clinic.healthexamination.application.service;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import org.springframework.stereotype.Component;

/**
 * Authorizes the session and assignment use cases for every supported caller, not only HTTP. An
 * actor ID alone proves nothing: the principal must be a staff account whose roles carry the
 * permission.
 */
@Component
public class SessionAccessPolicy {
  public static final String SESSION_VIEW = "HEALTH_EXAMINATION_SESSION_VIEW";
  public static final String SESSION_DETAIL_VIEW = "HEALTH_EXAMINATION_SESSION_DETAIL_VIEW";
  public static final String SESSION_CREATE = "HEALTH_EXAMINATION_SESSION_CREATE";
  public static final String SESSION_UPDATE = "HEALTH_EXAMINATION_SESSION_UPDATE";
  public static final String SESSION_CANCEL = "HEALTH_EXAMINATION_SESSION_CANCEL";
  public static final String ASSIGNMENT_VIEW = "HEALTH_EXAMINATION_ASSIGNMENT_VIEW";
  public static final String ASSIGNMENT_CREATE = "HEALTH_EXAMINATION_ASSIGNMENT_CREATE";
  public static final String ASSIGNMENT_MOVE = "HEALTH_EXAMINATION_ASSIGNMENT_MOVE";
  public static final String ASSIGNMENT_MOVE_ALL = "HEALTH_EXAMINATION_ASSIGNMENT_MOVE_ALL";
  public static final String ASSIGNMENT_CANCEL = "HEALTH_EXAMINATION_ASSIGNMENT_CANCEL";
  public static final String ASSIGNMENT_ATTENDANCE_RECORD =
      "HEALTH_EXAMINATION_ASSIGNMENT_ATTENDANCE_RECORD";

  /**
   * Requires the named permission.
   *
   * @param permission one of the permission constants of this class
   * @throws ApplicationException of type {@code ACCESS_DENIED} otherwise
   */
  public void require(UserPrincipal principal, String permission) {
    boolean allowed =
        principal != null
            && principal.userId() != null
            && "STAFF".equals(principal.principalType())
            && principal.roleAssignments().stream()
                .anyMatch(role -> role.permissions().contains(permission));
    if (!allowed)
      throw new ApplicationException(
          ApplicationException.Type.ACCESS_DENIED, "You are not authorized to perform this action");
  }
}

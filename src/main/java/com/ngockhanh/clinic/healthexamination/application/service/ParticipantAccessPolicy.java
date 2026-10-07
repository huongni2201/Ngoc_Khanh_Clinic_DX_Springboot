package com.ngockhanh.clinic.healthexamination.application.service;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import org.springframework.stereotype.Component;

/**
 * Authorizes the Participant use cases for every supported caller, not only HTTP. An actor ID alone
 * proves nothing: the principal must be a staff account whose roles carry the permission.
 */
@Component
public class ParticipantAccessPolicy {
  public static final String READ_PERMISSION = "HEALTH_EXAMINATION_PARTICIPANT_READ";
  public static final String IMPORT_PERMISSION = "HEALTH_EXAMINATION_PARTICIPANT_IMPORT";
  public static final String MANAGE_PERMISSION = "HEALTH_EXAMINATION_PARTICIPANT_MANAGE";

  /**
   * Requires permission to read the Participant roster of a batch and download its import template.
   *
   * @throws ApplicationException of type {@code ACCESS_DENIED} otherwise
   */
  public void requireRead(UserPrincipal principal) {
    require(principal, READ_PERMISSION);
  }

  /**
   * Requires permission to import Participants.
   *
   * @throws ApplicationException of type {@code ACCESS_DENIED} otherwise
   */
  public void requireImport(UserPrincipal principal) {
    require(principal, IMPORT_PERMISSION);
  }

  /**
   * Requires permission to add, view in full, edit and cancel a single Participant by hand.
   *
   * @throws ApplicationException of type {@code ACCESS_DENIED} otherwise
   */
  public void requireManage(UserPrincipal principal) {
    require(principal, MANAGE_PERMISSION);
  }

  private static void require(UserPrincipal principal, String permission) {
    boolean allowed =
        principal != null
            && principal.userId() != null
            && "STAFF".equals(principal.principalType())
            && principal.roleAssignments().stream()
                .anyMatch(role -> role.permissions().contains(permission));
    if (!allowed)
      throw new ApplicationException(
          ApplicationException.Type.ACCESS_DENIED,
          "You are not authorized to perform this action");
  }
}

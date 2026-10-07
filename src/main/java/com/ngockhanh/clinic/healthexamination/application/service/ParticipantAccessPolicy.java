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
  public static final String READ_PERMISSION = "PARTICIPANT_VIEW";
  public static final String TEMPLATE_DOWNLOAD_PERMISSION = "PARTICIPANT_TEMPLATE_DOWNLOAD";
  public static final String IMPORT_PERMISSION = "PARTICIPANT_IMPORT";
  public static final String CREATE_PERMISSION = "PARTICIPANT_CREATE";
  public static final String UPDATE_PERMISSION = "PARTICIPANT_UPDATE";
  public static final String REMOVE_PERMISSION = "PARTICIPANT_REMOVE";
  public static final String REACTIVATE_PERMISSION = "PARTICIPANT_REACTIVATE";

  /**
   * Requires permission to read the Participant roster of a batch.
   *
   * @throws ApplicationException of type {@code ACCESS_DENIED} otherwise
   */
  public void requireRead(UserPrincipal principal) {
    require(principal, READ_PERMISSION);
  }

  /**
   * Requires permission to download the Participant import template of a batch.
   *
   * @throws ApplicationException of type {@code ACCESS_DENIED} otherwise
   */
  public void requireTemplateDownload(UserPrincipal principal) {
    require(principal, TEMPLATE_DOWNLOAD_PERMISSION);
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
   * Requires permission to add a Participant manually.
   *
   * @param principal authenticated caller
   * @throws ApplicationException of type {@code ACCESS_DENIED} otherwise
   */
  public void requireCreate(UserPrincipal principal) {
    require(principal, CREATE_PERMISSION);
  }

  /**
   * Requires permission to update a Participant.
   *
   * @param principal authenticated caller
   * @throws ApplicationException of type {@code ACCESS_DENIED} otherwise
   */
  public void requireUpdate(UserPrincipal principal) {
    require(principal, UPDATE_PERMISSION);
  }

  /**
   * Requires permission to cancel a Participant.
   *
   * @param principal authenticated caller
   * @throws ApplicationException of type {@code ACCESS_DENIED} otherwise
   */
  public void requireRemove(UserPrincipal principal) {
    require(principal, REMOVE_PERMISSION);
  }

  /**
   * Requires permission to reactivate a cancelled Participant.
   *
   * @param principal authenticated caller
   * @throws ApplicationException of type {@code ACCESS_DENIED} otherwise
   */
  public void requireReactivate(UserPrincipal principal) {
    require(principal, REACTIVATE_PERMISSION);
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
          ApplicationException.Type.ACCESS_DENIED, "You are not authorized to perform this action");
  }
}

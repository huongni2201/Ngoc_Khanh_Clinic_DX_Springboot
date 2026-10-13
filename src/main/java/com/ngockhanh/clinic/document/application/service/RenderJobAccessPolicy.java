package com.ngockhanh.clinic.document.application.service;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import org.springframework.stereotype.Component;

/** Authorizes the print job use cases for every caller, not only HTTP. */
@Component
public class RenderJobAccessPolicy {
  public static final String READ_PERMISSION = "DOCUMENT_RENDER_JOB_READ";
  public static final String DOWNLOAD_PERMISSION = "DOCUMENT_RENDER_JOB_DOWNLOAD";

  /**
   * @throws ApplicationException of type {@code ACCESS_DENIED} without the read permission
   */
  public void requireRead(UserPrincipal principal) {
    require(principal, READ_PERMISSION);
  }

  /**
   * @throws ApplicationException of type {@code ACCESS_DENIED} without the download permission
   */
  public void requireDownload(UserPrincipal principal) {
    require(principal, DOWNLOAD_PERMISSION);
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

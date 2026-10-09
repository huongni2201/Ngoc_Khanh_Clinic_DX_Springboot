package com.ngockhanh.clinic.healthexamination.application.service;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import org.springframework.stereotype.Component;

/**
 * Authorizes the examination detail and payment report use cases for every supported caller, not
 * only HTTP. An actor ID alone proves nothing: the principal must be a staff account whose roles
 * carry the permission.
 */
@Component
public class ExaminationDetailAccessPolicy {
  public static final String SERVICE_READ_PERMISSION = "HEALTH_EXAMINATION_SERVICE_READ";
  public static final String SERVICE_SUMMARY_READ_PERMISSION =
      "HEALTH_EXAMINATION_SERVICE_SUMMARY_READ";
  public static final String SERVICE_EXPORT_PERMISSION = "HEALTH_EXAMINATION_SERVICE_EXPORT";
  public static final String SERVICE_RECONCILE_PERMISSION = "HEALTH_EXAMINATION_SERVICE_RECONCILE";
  public static final String REPORT_READ_PERMISSION = "HEALTH_EXAMINATION_REPORT_READ";
  public static final String REPORT_EXPORT_PERMISSION = "HEALTH_EXAMINATION_REPORT_EXPORT";

  /**
   * Requires permission to view the examination detail matrix.
   *
   * @throws ApplicationException of type {@code ACCESS_DENIED} otherwise
   */
  public void requireServiceRead(UserPrincipal principal) {
    require(principal, SERVICE_READ_PERMISSION);
  }

  /**
   * Requires permission to view the examination detail summary.
   *
   * @throws ApplicationException of type {@code ACCESS_DENIED} otherwise
   */
  public void requireServiceSummaryRead(UserPrincipal principal) {
    require(principal, SERVICE_SUMMARY_READ_PERMISSION);
  }

  /**
   * Requires permission to export the examination detail matrix to Excel.
   *
   * @throws ApplicationException of type {@code ACCESS_DENIED} otherwise
   */
  public void requireServiceExport(UserPrincipal principal) {
    require(principal, SERVICE_EXPORT_PERMISSION);
  }

  /**
   * Requires permission to import an examination detail Excel file.
   *
   * @throws ApplicationException of type {@code ACCESS_DENIED} otherwise
   */
  public void requireServiceReconcile(UserPrincipal principal) {
    require(principal, SERVICE_RECONCILE_PERMISSION);
  }

  /**
   * Requires permission to view the payment report.
   *
   * @throws ApplicationException of type {@code ACCESS_DENIED} otherwise
   */
  public void requireReportRead(UserPrincipal principal) {
    require(principal, REPORT_READ_PERMISSION);
  }

  /**
   * Requires permission to export the payment report to Word.
   *
   * @throws ApplicationException of type {@code ACCESS_DENIED} otherwise
   */
  public void requireReportExport(UserPrincipal principal) {
    require(principal, REPORT_EXPORT_PERMISSION);
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

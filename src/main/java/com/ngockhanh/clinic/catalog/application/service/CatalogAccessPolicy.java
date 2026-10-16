package com.ngockhanh.clinic.catalog.application.service;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import org.springframework.stereotype.Component;

/**
 * Authorises catalog management use cases for every supported caller (HTTP and direct).
 *
 * <p>An actor UUID alone proves nothing; the principal must be a staff account whose active role
 * assignments carry the required permission. Pattern follows {@code ParticipantAccessPolicy}.
 */
@Component
public class CatalogAccessPolicy {

  // -- Departments --
  public static final String DEPARTMENT_VIEW = "CATALOG_DEPARTMENT_VIEW";
  public static final String DEPARTMENT_DETAIL_VIEW = "CATALOG_DEPARTMENT_DETAIL_VIEW";
  public static final String DEPARTMENT_CREATE = "CATALOG_DEPARTMENT_CREATE";
  public static final String DEPARTMENT_UPDATE = "CATALOG_DEPARTMENT_UPDATE";
  public static final String DEPARTMENT_DEACTIVATE = "CATALOG_DEPARTMENT_DEACTIVATE";
  public static final String DEPARTMENT_REACTIVATE = "CATALOG_DEPARTMENT_REACTIVATE";

  // -- Rooms --
  public static final String ROOM_VIEW = "CATALOG_ROOM_VIEW";
  public static final String ROOM_DETAIL_VIEW = "CATALOG_ROOM_DETAIL_VIEW";
  public static final String ROOM_CREATE = "CATALOG_ROOM_CREATE";
  public static final String ROOM_UPDATE = "CATALOG_ROOM_UPDATE";
  public static final String ROOM_DEACTIVATE = "CATALOG_ROOM_DEACTIVATE";
  public static final String ROOM_REACTIVATE = "CATALOG_ROOM_REACTIVATE";

  // -- Specialties --
  public static final String SPECIALTY_VIEW = "CATALOG_SPECIALTY_VIEW";
  public static final String SPECIALTY_DETAIL_VIEW = "CATALOG_SPECIALTY_DETAIL_VIEW";
  public static final String SPECIALTY_CREATE = "CATALOG_SPECIALTY_CREATE";
  public static final String SPECIALTY_UPDATE = "CATALOG_SPECIALTY_UPDATE";
  public static final String SPECIALTY_DEACTIVATE = "CATALOG_SPECIALTY_DEACTIVATE";
  public static final String SPECIALTY_REACTIVATE = "CATALOG_SPECIALTY_REACTIVATE";

  // -- Services --
  public static final String SERVICE_VIEW = "CATALOG_SERVICE_VIEW";
  public static final String SERVICE_DETAIL_VIEW = "CATALOG_SERVICE_DETAIL_VIEW";
  public static final String SERVICE_CREATE = "CATALOG_SERVICE_CREATE";
  public static final String SERVICE_UPDATE = "CATALOG_SERVICE_UPDATE";
  public static final String SERVICE_DEACTIVATE = "CATALOG_SERVICE_DEACTIVATE";
  public static final String SERVICE_REACTIVATE = "CATALOG_SERVICE_REACTIVATE";

  // -- Medicines --
  public static final String MEDICINE_VIEW = "CATALOG_MEDICINE_VIEW";
  public static final String MEDICINE_DETAIL_VIEW = "CATALOG_MEDICINE_DETAIL_VIEW";
  public static final String MEDICINE_CREATE = "CATALOG_MEDICINE_CREATE";
  public static final String MEDICINE_UPDATE = "CATALOG_MEDICINE_UPDATE";
  public static final String MEDICINE_DEACTIVATE = "CATALOG_MEDICINE_DEACTIVATE";
  public static final String MEDICINE_REACTIVATE = "CATALOG_MEDICINE_REACTIVATE";

  // Department
  public void requireDepartmentView(UserPrincipal p) {
    require(p, DEPARTMENT_VIEW);
  }

  public void requireDepartmentDetailView(UserPrincipal p) {
    require(p, DEPARTMENT_DETAIL_VIEW);
  }

  public void requireDepartmentCreate(UserPrincipal p) {
    require(p, DEPARTMENT_CREATE);
  }

  public void requireDepartmentUpdate(UserPrincipal p) {
    require(p, DEPARTMENT_UPDATE);
  }

  public void requireDepartmentDeactivate(UserPrincipal p) {
    require(p, DEPARTMENT_DEACTIVATE);
  }

  public void requireDepartmentReactivate(UserPrincipal p) {
    require(p, DEPARTMENT_REACTIVATE);
  }

  // Room
  public void requireRoomView(UserPrincipal p) {
    require(p, ROOM_VIEW);
  }

  public void requireRoomDetailView(UserPrincipal p) {
    require(p, ROOM_DETAIL_VIEW);
  }

  public void requireRoomCreate(UserPrincipal p) {
    require(p, ROOM_CREATE);
  }

  public void requireRoomUpdate(UserPrincipal p) {
    require(p, ROOM_UPDATE);
  }

  public void requireRoomDeactivate(UserPrincipal p) {
    require(p, ROOM_DEACTIVATE);
  }

  public void requireRoomReactivate(UserPrincipal p) {
    require(p, ROOM_REACTIVATE);
  }

  // Specialty
  public void requireSpecialtyView(UserPrincipal p) {
    require(p, SPECIALTY_VIEW);
  }

  public void requireSpecialtyDetailView(UserPrincipal p) {
    require(p, SPECIALTY_DETAIL_VIEW);
  }

  public void requireSpecialtyCreate(UserPrincipal p) {
    require(p, SPECIALTY_CREATE);
  }

  public void requireSpecialtyUpdate(UserPrincipal p) {
    require(p, SPECIALTY_UPDATE);
  }

  public void requireSpecialtyDeactivate(UserPrincipal p) {
    require(p, SPECIALTY_DEACTIVATE);
  }

  public void requireSpecialtyReactivate(UserPrincipal p) {
    require(p, SPECIALTY_REACTIVATE);
  }

  // Service
  public void requireServiceView(UserPrincipal p) {
    require(p, SERVICE_VIEW);
  }

  public void requireServiceDetailView(UserPrincipal p) {
    require(p, SERVICE_DETAIL_VIEW);
  }

  public void requireServiceCreate(UserPrincipal p) {
    require(p, SERVICE_CREATE);
  }

  public void requireServiceUpdate(UserPrincipal p) {
    require(p, SERVICE_UPDATE);
  }

  public void requireServiceDeactivate(UserPrincipal p) {
    require(p, SERVICE_DEACTIVATE);
  }

  public void requireServiceReactivate(UserPrincipal p) {
    require(p, SERVICE_REACTIVATE);
  }

  // Medicine
  public void requireMedicineView(UserPrincipal p) {
    require(p, MEDICINE_VIEW);
  }

  public void requireMedicineDetailView(UserPrincipal p) {
    require(p, MEDICINE_DETAIL_VIEW);
  }

  public void requireMedicineCreate(UserPrincipal p) {
    require(p, MEDICINE_CREATE);
  }

  public void requireMedicineUpdate(UserPrincipal p) {
    require(p, MEDICINE_UPDATE);
  }

  public void requireMedicineDeactivate(UserPrincipal p) {
    require(p, MEDICINE_DEACTIVATE);
  }

  public void requireMedicineReactivate(UserPrincipal p) {
    require(p, MEDICINE_REACTIVATE);
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

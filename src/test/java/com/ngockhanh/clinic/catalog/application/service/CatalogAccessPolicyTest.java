package com.ngockhanh.clinic.catalog.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class CatalogAccessPolicyTest {
  private final CatalogAccessPolicy policy = new CatalogAccessPolicy();

  private record Operation(String permission, Consumer<UserPrincipal> authorize) {}

  private static UserPrincipal staff(String... permissions) {
    return UserPrincipal.builder()
        .userId(UUID.randomUUID())
        .staffId(UUID.randomUUID())
        .username("manager")
        .principalType("STAFF")
        .roleAssignments(
            List.of(
                new UserPrincipal.Assignment(
                    UUID.randomUUID(), "CLINIC_MANAGER", List.of(permissions))))
        .build();
  }

  @Test
  void eachOperationRequiresItsOwnPermissionAndAStaffAccount() {
    var operations =
        List.of(
            // Department
            new Operation(CatalogAccessPolicy.DEPARTMENT_VIEW, policy::requireDepartmentView),
            new Operation(
                CatalogAccessPolicy.DEPARTMENT_DETAIL_VIEW, policy::requireDepartmentDetailView),
            new Operation(CatalogAccessPolicy.DEPARTMENT_CREATE, policy::requireDepartmentCreate),
            new Operation(CatalogAccessPolicy.DEPARTMENT_UPDATE, policy::requireDepartmentUpdate),
            new Operation(
                CatalogAccessPolicy.DEPARTMENT_DEACTIVATE, policy::requireDepartmentDeactivate),
            new Operation(
                CatalogAccessPolicy.DEPARTMENT_REACTIVATE, policy::requireDepartmentReactivate),
            // Room
            new Operation(CatalogAccessPolicy.ROOM_VIEW, policy::requireRoomView),
            new Operation(CatalogAccessPolicy.ROOM_DETAIL_VIEW, policy::requireRoomDetailView),
            new Operation(CatalogAccessPolicy.ROOM_CREATE, policy::requireRoomCreate),
            new Operation(CatalogAccessPolicy.ROOM_UPDATE, policy::requireRoomUpdate),
            new Operation(CatalogAccessPolicy.ROOM_DEACTIVATE, policy::requireRoomDeactivate),
            new Operation(CatalogAccessPolicy.ROOM_REACTIVATE, policy::requireRoomReactivate),
            // Specialty
            new Operation(CatalogAccessPolicy.SPECIALTY_VIEW, policy::requireSpecialtyView),
            new Operation(
                CatalogAccessPolicy.SPECIALTY_DETAIL_VIEW, policy::requireSpecialtyDetailView),
            new Operation(CatalogAccessPolicy.SPECIALTY_CREATE, policy::requireSpecialtyCreate),
            new Operation(CatalogAccessPolicy.SPECIALTY_UPDATE, policy::requireSpecialtyUpdate),
            new Operation(
                CatalogAccessPolicy.SPECIALTY_DEACTIVATE, policy::requireSpecialtyDeactivate),
            new Operation(
                CatalogAccessPolicy.SPECIALTY_REACTIVATE, policy::requireSpecialtyReactivate),
            // Service
            new Operation(CatalogAccessPolicy.SERVICE_VIEW, policy::requireServiceView),
            new Operation(
                CatalogAccessPolicy.SERVICE_DETAIL_VIEW, policy::requireServiceDetailView),
            new Operation(CatalogAccessPolicy.SERVICE_CREATE, policy::requireServiceCreate),
            new Operation(CatalogAccessPolicy.SERVICE_UPDATE, policy::requireServiceUpdate),
            new Operation(CatalogAccessPolicy.SERVICE_DEACTIVATE, policy::requireServiceDeactivate),
            new Operation(CatalogAccessPolicy.SERVICE_REACTIVATE, policy::requireServiceReactivate),
            // Medicine
            new Operation(CatalogAccessPolicy.MEDICINE_VIEW, policy::requireMedicineView),
            new Operation(
                CatalogAccessPolicy.MEDICINE_DETAIL_VIEW, policy::requireMedicineDetailView),
            new Operation(CatalogAccessPolicy.MEDICINE_CREATE, policy::requireMedicineCreate),
            new Operation(CatalogAccessPolicy.MEDICINE_UPDATE, policy::requireMedicineUpdate),
            new Operation(
                CatalogAccessPolicy.MEDICINE_DEACTIVATE, policy::requireMedicineDeactivate),
            new Operation(
                CatalogAccessPolicy.MEDICINE_REACTIVATE, policy::requireMedicineReactivate));

    assertThat(operations).hasSize(30);

    for (var operation : operations) {
      // Valid staff with the exact permission passes
      assertThatCode(() -> operation.authorize().accept(staff(operation.permission())))
          .as("Valid permission: " + operation.permission())
          .doesNotThrowAnyException();

      // Null principal denied
      assertDenied(operation, null);

      // Staff with no permissions denied
      assertDenied(operation, staff());

      // Patient with permission denied
      assertDenied(
          operation,
          UserPrincipal.builder()
              .userId(UUID.randomUUID())
              .patientId(UUID.randomUUID())
              .principalType("PATIENT")
              .roleAssignments(staff(operation.permission()).roleAssignments())
              .build());

      // Principal without userId denied
      assertDenied(
          operation,
          UserPrincipal.builder()
              .staffId(UUID.randomUUID())
              .principalType("STAFF")
              .roleAssignments(staff(operation.permission()).roleAssignments())
              .build());
    }
  }

  private static void assertDenied(Operation operation, UserPrincipal principal) {
    assertThatThrownBy(() -> operation.authorize().accept(principal))
        .as("Should deny: " + operation.permission())
        .isInstanceOfSatisfying(
            ApplicationException.class,
            failure ->
                assertThat(failure.type()).isEqualTo(ApplicationException.Type.ACCESS_DENIED));
  }
}

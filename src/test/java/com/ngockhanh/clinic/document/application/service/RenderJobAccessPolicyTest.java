package com.ngockhanh.clinic.document.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RenderJobAccessPolicyTest {
  private final RenderJobAccessPolicy policy = new RenderJobAccessPolicy();

  private static void assertDenied(Runnable call) {
    assertThatThrownBy(call::run)
        .isInstanceOfSatisfying(
            ApplicationException.class,
            denied -> assertThat(denied.type()).isEqualTo(ApplicationException.Type.ACCESS_DENIED));
  }

  @Test
  void readNeedsTheReadPermissionAndDownloadNeedsTheDownloadPermission() {
    var reader = RenderJobFakes.staff(RenderJobAccessPolicy.READ_PERMISSION);
    var downloader = RenderJobFakes.staff(RenderJobAccessPolicy.DOWNLOAD_PERMISSION);

    policy.requireRead(reader);
    policy.requireDownload(downloader);
    assertDenied(() -> policy.requireRead(downloader));
    assertDenied(() -> policy.requireDownload(reader));
    assertDenied(() -> policy.requireRead(RenderJobFakes.staff()));
  }

  @Test
  void onlyStaffAccountsWithAnIdentityAreAllowed() {
    var assignment =
        new UserPrincipal.Assignment(
            UUID.randomUUID(), "ROLE", List.of(RenderJobAccessPolicy.READ_PERMISSION));
    var patient =
        UserPrincipal.builder()
            .userId(UUID.randomUUID())
            .patientId(UUID.randomUUID())
            .principalType("PATIENT")
            .roleAssignments(List.of(assignment))
            .build();
    var anonymousStaff =
        UserPrincipal.builder().principalType("STAFF").roleAssignments(List.of(assignment)).build();

    assertDenied(() -> policy.requireRead(patient));
    assertDenied(() -> policy.requireRead(anonymousStaff));
    assertDenied(() -> policy.requireRead(null));
  }
}

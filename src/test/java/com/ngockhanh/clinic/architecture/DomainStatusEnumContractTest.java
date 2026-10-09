package com.ngockhanh.clinic.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.ngockhanh.clinic.accesscontrol.domain.entity.UserAccount;
import com.ngockhanh.clinic.accesscontrol.domain.enums.AccountStatus;
import com.ngockhanh.clinic.accesscontrol.domain.enums.StaffMemberStatus;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatchParticipant;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.Organization;
import com.ngockhanh.clinic.healthexamination.domain.enums.AttendanceStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.BatchStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.OrganizationStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.ReconciliationStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.RosterStatus;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import org.junit.jupiter.api.Test;

class DomainStatusEnumContractTest {
  @Test
  void participantStatusesAreTopLevelEnums() throws NoSuchMethodException {
    assertReturnType(
        HealthExaminationBatchParticipant.class, "getRosterStatus", RosterStatus.class);
    assertReturnType(
        HealthExaminationBatchParticipant.class, "getAttendanceStatus", AttendanceStatus.class);
    assertReturnType(
        HealthExaminationBatchParticipant.class,
        "getReconciliationStatus",
        ReconciliationStatus.class);
    assertThat(RosterStatus.class.isMemberClass()).isFalse();
    assertThat(AttendanceStatus.class.isMemberClass()).isFalse();
    assertThat(ReconciliationStatus.class.isMemberClass()).isFalse();
  }

  @Test
  void organizationStatusIsAnEnum() throws NoSuchMethodException {
    assertReturnType(Organization.class, "status", OrganizationStatus.class);
  }

  @Test
  void accountAndStaffStatusesAreEnums() throws NoSuchMethodException {
    assertReturnType(UserAccount.class, "status", AccountStatus.class);
    assertReturnType(UserAccount.class, "staffStatus", StaffMemberStatus.class);
  }

  @Test
  void repositoryStatusContractsUseEnums() throws NoSuchMethodException {
    OrganizationRepository.class.getMethod(
        "search",
        int.class,
        int.class,
        String.class,
        String.class,
        String.class,
        OrganizationStatus.class);
    assertReturnType(
        HealthExaminationBatchRepository.BatchSummary.class, "status", BatchStatus.class);
  }

  private static void assertReturnType(
      Class<?> type, String method, Class<? extends Enum<?>> expectedType)
      throws NoSuchMethodException {
    assertThat(type.getMethod(method).getReturnType())
        .as("%s.%s", type.getSimpleName(), method)
        .isEqualTo(expectedType);
  }
}

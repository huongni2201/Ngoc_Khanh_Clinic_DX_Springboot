package com.ngockhanh.clinic.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.ngockhanh.clinic.accesscontrol.domain.entity.UserAccount;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatchParticipant;
import com.ngockhanh.clinic.healthexamination.domain.aggregate.Organization;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class DomainStatusEnumContractTest {
  @Test
  void participantStatusesAreTopLevelEnums() {
    assertThat(HealthExaminationBatchParticipant.class.getDeclaredClasses())
        .noneMatch(Class::isEnum);
  }

  @Test
  void organizationStatusIsAnEnum() throws NoSuchMethodException {
    Class<?> statusType = Organization.class.getMethod("status").getReturnType();

    assertThat(statusType.isEnum()).isTrue();
    assertThat(statusType.getSimpleName()).isEqualTo("OrganizationStatus");
  }

  @Test
  void accountAndStaffStatusesAreEnums() {
    assertEnumComponent(UserAccount.class, "status", "AccountStatus");
    assertEnumComponent(UserAccount.class, "staffStatus", "StaffMemberStatus");
  }

  @Test
  void repositoryStatusContractsUseEnums() {
    Method search =
        Arrays.stream(OrganizationRepository.class.getMethods())
            .filter(method -> method.getName().equals("search"))
            .findFirst()
            .orElseThrow();
    Class<?> organizationStatusType = search.getParameterTypes()[5];
    Class<?> batchStatusType =
        Arrays.stream(HealthExaminationBatchRepository.BatchSummary.class.getRecordComponents())
            .filter(component -> component.getName().equals("status"))
            .map(component -> component.getType())
            .findFirst()
            .orElseThrow();

    assertThat(organizationStatusType.isEnum()).isTrue();
    assertThat(organizationStatusType.getSimpleName()).isEqualTo("OrganizationStatus");
    assertThat(batchStatusType.isEnum()).isTrue();
    assertThat(batchStatusType.getSimpleName()).isEqualTo("BatchStatus");
  }

  private static void assertEnumComponent(
      Class<?> recordType, String componentName, String expectedEnumName) {
    Class<?> componentType =
        Arrays.stream(recordType.getRecordComponents())
            .filter(component -> component.getName().equals(componentName))
            .map(component -> component.getType())
            .findFirst()
            .orElseThrow();

    assertThat(componentType.isEnum()).isTrue();
    assertThat(componentType.getSimpleName()).isEqualTo(expectedEnumName);
  }
}

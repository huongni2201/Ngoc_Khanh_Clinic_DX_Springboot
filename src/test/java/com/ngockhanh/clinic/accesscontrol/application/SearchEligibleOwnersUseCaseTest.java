package com.ngockhanh.clinic.accesscontrol.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ngockhanh.clinic.accesscontrol.application.port.out.owner.PatientAccountOwnerDirectory;
import com.ngockhanh.clinic.accesscontrol.application.port.out.owner.PatientAccountOwnerDirectory.EligiblePatientOwner;
import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.accesscontrol.application.response.EligibleOwnerListResponse;
import com.ngockhanh.clinic.accesscontrol.application.response.EligibleOwnerResponse;
import com.ngockhanh.clinic.accesscontrol.application.usecase.CreateUserAccountUseCase;
import com.ngockhanh.clinic.accesscontrol.application.usecase.SearchEligibleOwnersUseCase;
import com.ngockhanh.clinic.accesscontrol.domain.repository.StaffMemberRepository;
import com.ngockhanh.clinic.accesscontrol.domain.repository.StaffMemberRepository.EligibleStaffOwner;
import com.ngockhanh.clinic.accesscontrol.domain.repository.UserAccountRepository;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class SearchEligibleOwnersUseCaseTest {
  private final StaffMemberRepository staffMembers = mock(StaffMemberRepository.class);
  private final PatientAccountOwnerDirectory patients = mock(PatientAccountOwnerDirectory.class);
  private final UserAccountRepository accounts = mock(UserAccountRepository.class);
  private final SearchEligibleOwnersUseCase useCase =
      new SearchEligibleOwnersUseCase(staffMembers, patients, accounts);

  private static UserPrincipal admin(String permission) {
    return UserPrincipal.builder()
        .userId(UUID.randomUUID())
        .principalType("STAFF")
        .roleAssignments(
            List.of(
                UserPrincipal.Assignment.builder()
                    .roleId(UUID.randomUUID())
                    .roleCode("ADMINISTRATOR")
                    .permissions(List.of(permission))
                    .build()))
        .build();
  }

  private static final UserPrincipal ADMIN = admin(SearchEligibleOwnersUseCase.PERMISSION);

  private static EligibleStaffOwner staff(Integer index, String phone) {
    return new EligibleStaffOwner(
        UUID.randomUUID(), "NV-" + index, "Staff " + index, "Khoa Khám Bệnh", phone, null);
  }

  private static EligiblePatientOwner patient(Integer index) {
    return new EligiblePatientOwner(
        UUID.randomUUID(),
        "BN-" + index,
        "Patient " + index,
        LocalDate.of(1990, 1, 1),
        "0987111222");
  }

  @Test
  void eligibleStaffOwnersCarryTheirSmsNumberWhenThePhoneIsAMobile() {
    when(staffMembers.searchActiveWithoutAccount("Nguyễn", 21))
        .thenReturn(List.of(staff(1, "0912 345 678"), staff(2, "024 3123 4567")));

    EligibleOwnerListResponse result = useCase.execute("STAFF", " Nguyễn ", ADMIN);

    assertThat(result.limited()).isFalse();
    assertThat(result.items())
        .extracting(EligibleOwnerResponse::smsPhoneNumber)
        .containsExactly("0912345678", null);
    assertThat(result.items().getFirst().departmentName()).isEqualTo("Khoa Khám Bệnh");
  }

  @Test
  void staffResultsAreCappedAndFlaggedAsLimited() {
    when(staffMembers.searchActiveWithoutAccount("Staff", 21))
        .thenReturn(IntStream.range(0, 21).mapToObj(i -> staff(i, null)).toList());

    EligibleOwnerListResponse result = useCase.execute("STAFF", "Staff", ADMIN);

    assertThat(result.items()).hasSize(20);
    assertThat(result.limited()).isTrue();
  }

  @Test
  void patientsThatAlreadyHaveAnAccountAreLeftOut() {
    EligiblePatientOwner free = patient(1);
    EligiblePatientOwner linked = patient(2);
    when(patients.searchActive("Patient", 100)).thenReturn(List.of(free, linked));
    when(accounts.findLinkedPatientIds(List.of(free.id(), linked.id())))
        .thenReturn(Set.of(linked.id()));

    EligibleOwnerListResponse result = useCase.execute("PATIENT", "Patient", ADMIN);

    assertThat(result.items())
        .extracting(EligibleOwnerResponse::ownerId)
        .containsExactly(free.id());
    assertThat(result.items().getFirst().dateOfBirth()).isEqualTo(LocalDate.of(1990, 1, 1));
    assertThat(result.limited()).isFalse();
  }

  @Test
  void aFullPatientOverfetchIsFlaggedAsLimited() {
    when(patients.searchActive("Patient", 100))
        .thenReturn(
            IntStream.range(0, 100).mapToObj(SearchEligibleOwnersUseCaseTest::patient).toList());
    when(accounts.findLinkedPatientIds(any())).thenReturn(Set.of());

    EligibleOwnerListResponse result = useCase.execute("PATIENT", "Patient", ADMIN);

    assertThat(result.items()).hasSize(20);
    assertThat(result.limited()).isTrue();
  }

  @Test
  void callersWithoutThePermissionAreDenied() {
    assertThatThrownBy(
            () -> useCase.execute("STAFF", "Nguyen", admin(CreateUserAccountUseCase.PERMISSION)))
        .isInstanceOf(ApplicationException.class);
    verifyNoInteractions(staffMembers, patients, accounts);
  }

  @Test
  void unknownTypesAndBlankKeywordsAreInvalid() {
    assertThatThrownBy(() -> useCase.execute("ADMIN", "Nguyen", ADMIN))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> useCase.execute("STAFF", "  ", ADMIN))
        .isInstanceOf(IllegalArgumentException.class);
    when(staffMembers.searchActiveWithoutAccount(anyString(), anyInt())).thenReturn(List.of());
    assertThat(useCase.execute("STAFF", "none", ADMIN).items()).isEmpty();
  }
}

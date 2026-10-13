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
import com.ngockhanh.clinic.accesscontrol.application.port.out.owner.PatientAccountOwnerDirectory.PatientOwnerCandidate;
import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.accesscontrol.application.response.AccountOwnerCandidateListResponse;
import com.ngockhanh.clinic.accesscontrol.application.response.AccountOwnerCandidateResponse;
import com.ngockhanh.clinic.accesscontrol.application.service.UserAccountAccessPolicy;
import com.ngockhanh.clinic.accesscontrol.application.usecase.SearchAccountOwnerCandidatesUseCase;
import com.ngockhanh.clinic.accesscontrol.domain.repository.StaffMemberRepository;
import com.ngockhanh.clinic.accesscontrol.domain.repository.StaffMemberRepository.StaffOwnerCandidate;
import com.ngockhanh.clinic.accesscontrol.domain.repository.UserAccountRepository;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class SearchAccountOwnerCandidatesUseCaseTest {
  private final StaffMemberRepository staffMembers = mock(StaffMemberRepository.class);
  private final PatientAccountOwnerDirectory patients = mock(PatientAccountOwnerDirectory.class);
  private final UserAccountRepository accounts = mock(UserAccountRepository.class);
  private final SearchAccountOwnerCandidatesUseCase useCase =
      new SearchAccountOwnerCandidatesUseCase(
          new UserAccountAccessPolicy(), staffMembers, patients, accounts);

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

  private static final UserPrincipal ADMIN = admin(UserAccountAccessPolicy.OWNER_SEARCH_PERMISSION);

  private static StaffOwnerCandidate staff(int index, String phone) {
    return new StaffOwnerCandidate(
        UUID.randomUUID(), "NV-" + index, "Staff " + index, "Khoa Khám Bệnh", phone, null);
  }

  private static PatientOwnerCandidate patient(int index) {
    return new PatientOwnerCandidate(
        UUID.randomUUID(),
        "BN-" + index,
        "Patient " + index,
        LocalDate.of(1990, 1, 1),
        "0987111222");
  }

  @Test
  void staffCandidatesCarryTheirSmsNumberWhenThePhoneIsAMobile() {
    when(staffMembers.searchActiveWithoutAccount("Nguyễn", 21))
        .thenReturn(List.of(staff(1, "0912 345 678"), staff(2, "024 3123 4567")));

    AccountOwnerCandidateListResponse result = useCase.execute("STAFF", " Nguyễn ", ADMIN);

    assertThat(result.limited()).isFalse();
    assertThat(result.items())
        .extracting(AccountOwnerCandidateResponse::smsPhoneNumber)
        .containsExactly("0912345678", null);
    assertThat(result.items().getFirst().departmentName()).isEqualTo("Khoa Khám Bệnh");
  }

  @Test
  void staffResultsAreCappedAndFlaggedAsLimited() {
    when(staffMembers.searchActiveWithoutAccount("Staff", 21))
        .thenReturn(IntStream.range(0, 21).mapToObj(i -> staff(i, null)).toList());

    AccountOwnerCandidateListResponse result = useCase.execute("STAFF", "Staff", ADMIN);

    assertThat(result.items()).hasSize(20);
    assertThat(result.limited()).isTrue();
  }

  @Test
  void patientsThatAlreadyHaveAnAccountAreLeftOut() {
    PatientOwnerCandidate free = patient(1);
    PatientOwnerCandidate linked = patient(2);
    when(patients.searchActive("Patient", 100)).thenReturn(List.of(free, linked));
    when(accounts.findLinkedPatientIds(List.of(free.id(), linked.id())))
        .thenReturn(Set.of(linked.id()));

    AccountOwnerCandidateListResponse result = useCase.execute("PATIENT", "Patient", ADMIN);

    assertThat(result.items())
        .extracting(AccountOwnerCandidateResponse::ownerId)
        .containsExactly(free.id());
    assertThat(result.items().getFirst().dateOfBirth()).isEqualTo(LocalDate.of(1990, 1, 1));
    assertThat(result.limited()).isFalse();
  }

  @Test
  void aFullPatientOverfetchIsFlaggedAsLimited() {
    when(patients.searchActive("Patient", 100))
        .thenReturn(
            IntStream.range(0, 100)
                .mapToObj(SearchAccountOwnerCandidatesUseCaseTest::patient)
                .toList());
    when(accounts.findLinkedPatientIds(any())).thenReturn(Set.of());

    AccountOwnerCandidateListResponse result = useCase.execute("PATIENT", "Patient", ADMIN);

    assertThat(result.items()).hasSize(20);
    assertThat(result.limited()).isTrue();
  }

  @Test
  void callersWithoutThePermissionAreDenied() {
    assertThatThrownBy(
            () ->
                useCase.execute(
                    "STAFF", "Nguyen", admin(UserAccountAccessPolicy.CREATE_PERMISSION)))
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

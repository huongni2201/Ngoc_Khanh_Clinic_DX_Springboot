package com.ngockhanh.clinic.accesscontrol.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.port.out.owner.PatientAccountOwnerDirectory;
import com.ngockhanh.clinic.accesscontrol.application.port.out.owner.PatientAccountOwnerDirectory.PatientOwnerCandidate;
import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.accesscontrol.application.response.AccountOwnerCandidateListResponse;
import com.ngockhanh.clinic.accesscontrol.application.response.AccountOwnerCandidateResponse;
import com.ngockhanh.clinic.accesscontrol.application.service.UserAccountAccessPolicy;
import com.ngockhanh.clinic.accesscontrol.domain.enums.AccountType;
import com.ngockhanh.clinic.accesscontrol.domain.repository.StaffMemberRepository;
import com.ngockhanh.clinic.accesscontrol.domain.repository.StaffMemberRepository.StaffOwnerCandidate;
import com.ngockhanh.clinic.accesscontrol.domain.repository.UserAccountRepository;
import com.ngockhanh.clinic.accesscontrol.domain.valueobject.SmsPhoneNumber;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Finds the active staff members or patients without a user account that can own a new account
 * (UC-005). The result is bounded instead of paginated: patients that already have an account are
 * filtered after the search, so an exact total is not available.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchAccountOwnerCandidatesUseCase {
  /** Maximum number of candidates returned. */
  static final Integer LIMIT = 20;

  /** Patients read before removing those that already have an account. */
  static final Integer PATIENT_OVERFETCH = 100;

  private final UserAccountAccessPolicy access;
  private final StaffMemberRepository staffMembers;
  private final PatientAccountOwnerDirectory patients;
  private final UserAccountRepository accounts;

  /**
   * Searches account owner candidates. Staff members match on staff code, full name or phone
   * number; patients on patient code, full name, phone number or the exact identification number.
   *
   * @param accountType STAFF or PATIENT
   * @param keyword search text
   * @param principal authenticated Administrator
   * @return at most 20 candidates ordered by full name, and whether the keyword should be refined
   * @throws com.ngockhanh.clinic.shared.exception.ApplicationException of type {@code
   *     ACCESS_DENIED} without the owner search permission
   * @throws IllegalArgumentException if the account type is unknown or the keyword is blank
   */
  @Transactional(readOnly = true)
  public AccountOwnerCandidateListResponse execute(
      String accountType, String keyword, UserPrincipal principal) {
    access.requireOwnerSearch(principal);
    AccountType type =
        AccountType.from(accountType)
            .orElseThrow(() -> new IllegalArgumentException("Unknown account type"));
    if (keyword == null || keyword.isBlank())
      throw new IllegalArgumentException("Keyword is required");
    String text = keyword.strip();
    AccountOwnerCandidateListResponse result =
        type == AccountType.STAFF ? staff(text) : patients(text);
    log.debug(
        "Account owner candidates searched: accountType={}, resultCount={}, limited={}",
        type,
        result.items().size(),
        result.limited());
    return result;
  }

  private AccountOwnerCandidateListResponse staff(String keyword) {
    List<StaffOwnerCandidate> rows = staffMembers.searchActiveWithoutAccount(keyword, LIMIT + 1);
    return new AccountOwnerCandidateListResponse(
        rows.stream().limit(LIMIT).map(SearchAccountOwnerCandidatesUseCase::toResponse).toList(),
        rows.size() > LIMIT);
  }

  private AccountOwnerCandidateListResponse patients(String keyword) {
    List<PatientOwnerCandidate> rows = patients.searchActive(keyword, PATIENT_OVERFETCH);
    Set<UUID> linked =
        rows.isEmpty()
            ? Set.of()
            : accounts.findLinkedPatientIds(rows.stream().map(PatientOwnerCandidate::id).toList());
    List<PatientOwnerCandidate> free =
        rows.stream().filter(row -> !linked.contains(row.id())).toList();
    return new AccountOwnerCandidateListResponse(
        free.stream().limit(LIMIT).map(SearchAccountOwnerCandidatesUseCase::toResponse).toList(),
        free.size() > LIMIT || rows.size() == PATIENT_OVERFETCH);
  }

  private static AccountOwnerCandidateResponse toResponse(StaffOwnerCandidate row) {
    return AccountOwnerCandidateResponse.builder()
        .ownerId(row.id())
        .ownerCode(row.staffCode())
        .fullName(row.fullName())
        .departmentName(row.departmentName())
        .phone(row.phone())
        .email(row.email())
        .smsPhoneNumber(smsNumber(row.phone()))
        .build();
  }

  private static AccountOwnerCandidateResponse toResponse(PatientOwnerCandidate row) {
    return AccountOwnerCandidateResponse.builder()
        .ownerId(row.id())
        .ownerCode(row.patientCode())
        .fullName(row.fullName())
        .dateOfBirth(row.dateOfBirth())
        .phone(row.phone())
        .smsPhoneNumber(smsNumber(row.phone()))
        .build();
  }

  private static String smsNumber(String phone) {
    return SmsPhoneNumber.parse(phone).map(SmsPhoneNumber::value).orElse(null);
  }
}
